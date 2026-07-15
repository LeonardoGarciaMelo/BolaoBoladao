defmodule BoladaoPayWeb.Plugs.CheckoutCors do
  import Plug.Conn

  @checkout_prefix "/api/v1/checkout"

  def init(opts), do: opts

  def call(%Plug.Conn{request_path: @checkout_prefix <> _} = conn, _opts) do
    origin = conn |> get_req_header("origin") |> List.first()
    allowed = Application.fetch_env!(:boladao_pay, :allowed_cors_origins)

    conn =
      if origin in allowed do
        conn
        |> put_resp_header("access-control-allow-origin", origin)
        |> put_resp_header("vary", "Origin")
        |> put_resp_header("access-control-allow-methods", "GET, POST, OPTIONS")
        |> put_resp_header("access-control-allow-headers", "Authorization, Content-Type")
        |> put_resp_header("access-control-max-age", "600")
      else
        conn
      end

    if conn.method == "OPTIONS", do: conn |> send_resp(204, "") |> halt(), else: conn
  end

  def call(conn, _opts), do: conn
end
