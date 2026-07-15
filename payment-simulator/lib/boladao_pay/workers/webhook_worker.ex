defmodule BoladaoPay.Workers.WebhookWorker do
  use Oban.Worker, queue: :webhooks, max_attempts: 10, unique: [period: :infinity, fields: [:worker, :args]]

  alias BoladaoPay.{Payments, Repo, WebhookSignature}
  alias BoladaoPay.Payments.Charge

  @impl Oban.Worker
  def perform(%Oban.Job{args: %{"charge_id" => charge_id, "event_id" => event_id}, attempt: attempt}) do
    with {:ok, id} <- Ecto.UUID.cast(charge_id),
         %Charge{terminal_event_id: ^event_id} = charge <- Repo.get(Charge, id) do
      body = charge |> Payments.webhook_payload() |> Jason.encode!()
      timestamp = DateTime.utc_now() |> DateTime.to_unix() |> Integer.to_string()
      signature = WebhookSignature.sign(config(:webhook_secret), timestamp, body)

      headers = [
        {"content-type", "application/json"},
        {"x-boladao-pay-event-id", event_id},
        {"x-boladao-pay-timestamp", timestamp},
        {"x-boladao-pay-signature", signature}
      ]

      case Req.post(config(:webhook_url), body: body, headers: headers, retry: false, receive_timeout: 5_000) do
        {:ok, %Req.Response{status: status}} when status in 200..299 ->
          case Payments.record_webhook_attempt(id, attempt) do
            {:ok, _charge} -> :ok
            {:error, reason} -> {:error, "could not record webhook delivery: #{inspect(reason)}"}
          end

        {:ok, %Req.Response{status: status}} ->
          error = "HTTP #{status}"
          Payments.record_webhook_attempt(id, attempt, error)
          {:error, error}

        {:error, reason} ->
          error = Exception.message(reason)
          Payments.record_webhook_attempt(id, attempt, error)
          {:error, error}
      end
    else
      :error -> {:discard, "invalid charge id"}
      nil -> {:discard, "charge not found"}
      _ -> {:discard, "event does not match charge"}
    end
  end

  def backoff(%Oban.Job{attempt: attempt}), do: min(round(:math.pow(2, attempt)), 60)

  defp config(key), do: Application.fetch_env!(:boladao_pay, key)
end
