defmodule BoladaoPay.Payments.Charge do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id
  schema "charges" do
    field :merchant_reference, Ecto.UUID
    field :amount_cents, :integer
    field :description, :string
    field :return_url, :string
    field :idempotency_key, :string
    field :idempotency_fingerprint, :string
    field :checkout_token, :string
    field :status, :string, default: "PENDING"
    field :expires_at, :utc_datetime_usec
    field :terminal_event_id, Ecto.UUID
    field :terminal_event_type, :string
    field :terminal_event_occurred_at, :utc_datetime_usec
    field :webhook_status, :string
    field :webhook_attempts, :integer, default: 0
    field :last_webhook_error, :string
    field :webhook_delivered_at, :utc_datetime_usec

    timestamps(type: :utc_datetime_usec)
  end

  @merchant_fields ~w(merchant_reference amount_cents description return_url)a
  @server_fields ~w(idempotency_key idempotency_fingerprint checkout_token expires_at)a
  @required @merchant_fields ++ @server_fields

  def create_changeset(charge, attrs) do
    charge
    |> cast(attrs, @merchant_fields)
    |> put_server_fields(attrs)
    |> validate_required(@required)
    |> validate_number(:amount_cents, greater_than_or_equal_to: 100, less_than_or_equal_to: 1_000_000)
    |> validate_length(:description, min: 1, max: 250)
    |> unique_constraint(:merchant_reference)
    |> unique_constraint(:idempotency_key)
    |> unique_constraint(:checkout_token)
  end

  defp put_server_fields(changeset, attrs) do
    Enum.reduce(@server_fields, changeset, fn field, current ->
      put_change(current, field, Map.fetch!(attrs, field))
    end)
  end

  def terminal_changeset(charge, status, event_id, event_type, occurred_at) do
    change(charge,
      status: status,
      terminal_event_id: event_id,
      terminal_event_type: event_type,
      terminal_event_occurred_at: occurred_at,
      webhook_status: "PENDING",
      last_webhook_error: nil
    )
  end

  def webhook_attempt_changeset(charge, attempt, error \\ nil) do
    change(charge,
      webhook_attempts: attempt,
      webhook_status: webhook_status(attempt, error),
      last_webhook_error: error,
      webhook_delivered_at: if(error, do: nil, else: DateTime.utc_now())
    )
  end

  defp webhook_status(_attempt, nil), do: "DELIVERED"
  defp webhook_status(attempt, _error) when attempt >= 10, do: "FAILED"
  defp webhook_status(_attempt, _error), do: "RETRYING"
end
