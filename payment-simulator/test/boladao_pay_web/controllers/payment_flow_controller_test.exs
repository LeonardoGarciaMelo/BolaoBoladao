defmodule BoladaoPayWeb.PaymentFlowControllerTest do
  use BoladaoPayWeb.ConnCase, async: false

  use Oban.Testing, repo: BoladaoPay.Repo

  alias BoladaoPay.Workers.{ExpireChargeWorker, WebhookWorker}

  @merchant_key "merchant-test-key"
  @reference "11111111-1111-1111-1111-111111111111"

  test "merchant creates and replays an idempotent charge", %{conn: conn} do
    response = create_charge(conn, "charge-1", 5_000)

    assert response.status == 201
    body = Jason.decode!(response.resp_body)
    assert body["amountCents"] == 5_000
    assert body["status"] == "PENDING"
    assert body["checkoutUrl"] =~ "http://localhost:8080/pagamento#"
    assert DateTime.diff(DateTime.from_iso8601(body["expiresAt"]) |> elem(1),
             DateTime.from_iso8601(body["createdAt"]) |> elem(1)) in 899..901
    assert_enqueued worker: ExpireChargeWorker, args: %{"charge_id" => body["chargeId"]}

    replay = create_charge(conn, "charge-1", 5_000)
    assert replay.status == 200
    assert Jason.decode!(replay.resp_body)["chargeId"] == body["chargeId"]

    conflict = create_charge(conn, "charge-1", 5_001)
    assert conflict.status == 409
    assert Jason.decode!(conflict.resp_body)["error"] == "Idempotency-Key already used with another payload"
  end

  test "merchant endpoints reject missing credentials", %{conn: conn} do
    response = post(conn, "/api/v1/merchant/charges", valid_payload(5_000))
    assert response.status == 401
  end

  test "charge validates value limits and return origin", %{conn: conn} do
    assert create_charge(conn, "too-small", 99).status == 400
    assert create_charge(conn, "too-large", 1_000_001).status == 400

    payload = valid_payload(1_000) |> Map.put("returnUrl", "https://evil.example/steal")
    response = merchant_conn(conn, "invalid-return") |> post("/api/v1/merchant/charges", payload)
    assert response.status == 400
  end

  test "checkout accepts one terminal outcome and rejects a conflicting one", %{conn: conn} do
    charge = create_charge(conn, "terminal", 2_000) |> json_body()
    token = checkout_token(charge)

    checkout = checkout_conn(conn, token) |> get("/api/v1/checkout")
    assert json_body(checkout)["status"] == "PENDING"

    paid = checkout_conn(conn, token) |> post("/api/v1/checkout/simulate", %{outcome: "PAID"})
    assert json_body(paid)["status"] == "PAID"
    assert_enqueued worker: WebhookWorker, args: %{"charge_id" => charge["chargeId"]}

    replay = checkout_conn(conn, token) |> post("/api/v1/checkout/simulate", %{outcome: "PAID"})
    assert replay.status == 200

    conflict = checkout_conn(conn, token) |> post("/api/v1/checkout/simulate", %{outcome: "REFUSED"})
    assert conflict.status == 409
    assert json_body(conflict)["status"] == "PAID"
  end

  test "concurrent terminal outcomes keep exactly one winner", %{conn: conn} do
    charge = create_charge(conn, "concurrent-terminal", 2_500) |> json_body()
    token = checkout_token(charge)

    results =
      ["PAID", "REFUSED"]
      |> Task.async_stream(&BoladaoPay.Payments.simulate(token, &1), ordered: false, timeout: :infinity)
      |> Enum.map(fn {:ok, result} -> result end)

    assert Enum.count(results, &match?({:ok, _}, &1)) == 1
    assert Enum.count(results, &match?({:error, {:terminal_conflict, _}}, &1)) == 1
  end

  test "checkout rejects an invalid opaque token", %{conn: conn} do
    response = checkout_conn(conn, "invalid-token") |> get("/api/v1/checkout")
    assert response.status == 401
  end

  test "checkout lazily expires an overdue charge", %{conn: conn} do
    previous = Application.fetch_env!(:boladao_pay, :charge_ttl_seconds)
    Application.put_env(:boladao_pay, :charge_ttl_seconds, 0)
    on_exit(fn -> Application.put_env(:boladao_pay, :charge_ttl_seconds, previous) end)

    charge = create_charge(conn, "expires", 3_000) |> json_body()
    response = checkout_conn(conn, checkout_token(charge)) |> get("/api/v1/checkout")

    assert json_body(response)["status"] == "EXPIRED"
    assert_enqueued worker: WebhookWorker, args: %{"charge_id" => charge["chargeId"]}
  end

  test "checkout CORS allows only configured web origin", %{conn: conn} do
    allowed =
      conn
      |> put_req_header("origin", "http://localhost:8080")
      |> options("/api/v1/checkout")

    assert allowed.status == 204
    assert get_resp_header(allowed, "access-control-allow-origin") == ["http://localhost:8080"]

    denied =
      build_conn()
      |> put_req_header("origin", "https://evil.example")
      |> options("/api/v1/checkout")

    assert get_resp_header(denied, "access-control-allow-origin") == []
  end

  test "webhook worker signs the exact body and records delivery", %{conn: conn} do
    bypass = Bypass.open()
    previous = Application.fetch_env!(:boladao_pay, :webhook_url)
    Application.put_env(:boladao_pay, :webhook_url, "http://localhost:#{bypass.port}/wallet/webhooks/boladao-pay")
    on_exit(fn -> Application.put_env(:boladao_pay, :webhook_url, previous) end)

    Bypass.expect_once(bypass, "POST", "/wallet/webhooks/boladao-pay", fn request_conn ->
      {:ok, body, request_conn} = Plug.Conn.read_body(request_conn)
      [timestamp] = Plug.Conn.get_req_header(request_conn, "x-boladao-pay-timestamp")
      [signature] = Plug.Conn.get_req_header(request_conn, "x-boladao-pay-signature")
      assert signature == BoladaoPay.WebhookSignature.sign("webhook-test-secret", timestamp, body)
      assert Jason.decode!(body)["eventType"] == "CHARGE_PAID"
      Plug.Conn.send_resp(request_conn, 202, "")
    end)

    charge = create_charge(conn, "webhook", 4_000) |> json_body()
    token = checkout_token(charge)
    checkout_conn(conn, token) |> post("/api/v1/checkout/simulate", %{outcome: "PAID"})

    [job] = all_enqueued(worker: WebhookWorker)
    assert :ok = perform_job(WebhookWorker, job.args)

    merchant = merchant_conn(conn) |> get("/api/v1/merchant/charges/#{charge["chargeId"]}") |> json_body()
    assert merchant["status"] == "PAID"
    assert merchant["webhook"]["status"] == "DELIVERED"
    assert merchant["webhook"]["attempts"] == 1
    assert merchant["webhook"]["deliveredAt"]
    assert merchant["terminalEvent"]["eventType"] == "CHARGE_PAID"
  end

  test "webhook retries keep the event stable and expose final delivery failure", %{conn: conn} do
    bypass = Bypass.open()
    previous = Application.fetch_env!(:boladao_pay, :webhook_url)
    Application.put_env(:boladao_pay, :webhook_url, "http://localhost:#{bypass.port}/wallet/webhooks/boladao-pay")
    on_exit(fn -> Application.put_env(:boladao_pay, :webhook_url, previous) end)
    test_pid = self()

    charge = create_charge(conn, "webhook-retry", 4_500) |> json_body()
    checkout_conn(conn, checkout_token(charge)) |> post("/api/v1/checkout/simulate", %{outcome: "PAID"})
    [job] = all_enqueued(worker: WebhookWorker)

    Bypass.expect_once(bypass, "POST", "/wallet/webhooks/boladao-pay", fn request_conn ->
      {:ok, body, request_conn} = Plug.Conn.read_body(request_conn)
      send(test_pid, {:webhook_body, 1, body})
      Plug.Conn.send_resp(request_conn, 500, "retry")
    end)

    assert {:error, "HTTP 500"} = WebhookWorker.perform(%Oban.Job{args: job.args, attempt: 1})
    assert_receive {:webhook_body, 1, first_body}

    Bypass.expect_once(bypass, "POST", "/wallet/webhooks/boladao-pay", fn request_conn ->
      {:ok, body, request_conn} = Plug.Conn.read_body(request_conn)
      send(test_pid, {:webhook_body, 10, body})
      Plug.Conn.send_resp(request_conn, 500, "failed")
    end)

    assert {:error, "HTTP 500"} = WebhookWorker.perform(%Oban.Job{args: job.args, attempt: 10})
    assert_receive {:webhook_body, 10, final_body}
    assert Jason.decode!(first_body) == Jason.decode!(final_body)
    assert WebhookWorker.backoff(%Oban.Job{attempt: 1}) == 2
    assert WebhookWorker.backoff(%Oban.Job{attempt: 10}) == 60

    merchant = merchant_conn(conn) |> get("/api/v1/merchant/charges/#{charge["chargeId"]}") |> json_body()
    assert merchant["webhook"]["status"] == "FAILED"
    assert merchant["webhook"]["attempts"] == 10
    assert merchant["webhook"]["lastError"] == "HTTP 500"
  end

  defp create_charge(conn, key, amount) do
    conn |> merchant_conn(key) |> post("/api/v1/merchant/charges", valid_payload(amount))
  end

  defp merchant_conn(conn, key \\ nil) do
    conn = put_req_header(conn, "authorization", "Bearer #{@merchant_key}")
    if key, do: put_req_header(conn, "idempotency-key", key), else: conn
  end

  defp checkout_conn(conn, token), do: put_req_header(conn, "authorization", "Checkout #{token}")

  defp valid_payload(amount) do
    %{
      "merchantReference" => @reference,
      "amountCents" => amount,
      "description" => "Depósito PIX fictício",
      "returnUrl" => "http://localhost:8080/carteira?deposit=#{@reference}"
    }
  end

  defp checkout_token(%{"checkoutUrl" => url}), do: url |> String.split("#") |> List.last()
  defp json_body(conn), do: Jason.decode!(conn.resp_body)
end
