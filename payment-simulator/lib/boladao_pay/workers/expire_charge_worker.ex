defmodule BoladaoPay.Workers.ExpireChargeWorker do
  use Oban.Worker, queue: :expirations, max_attempts: 3, unique: [period: :infinity, fields: [:worker, :args]]

  @impl Oban.Worker
  def perform(%Oban.Job{args: %{"charge_id" => charge_id}}) do
    case Ecto.UUID.cast(charge_id) do
      {:ok, id} ->
        BoladaoPay.Payments.expire_charge(id)
        :ok

      :error -> {:discard, "invalid charge id"}
    end
  end
end
