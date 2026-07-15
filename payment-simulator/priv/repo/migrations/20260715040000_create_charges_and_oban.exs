defmodule BoladaoPay.Repo.Migrations.CreateChargesAndOban do
  use Ecto.Migration

  def up do
    Oban.Migration.up()

    create table(:charges, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :merchant_reference, :binary_id, null: false
      add :amount_cents, :bigint, null: false
      add :description, :string, size: 250, null: false
      add :return_url, :string, size: 1000, null: false
      add :idempotency_key, :string, size: 150, null: false
      add :idempotency_fingerprint, :string, size: 64, null: false
      add :checkout_token, :string, size: 100, null: false
      add :status, :string, size: 20, null: false, default: "PENDING"
      add :expires_at, :utc_datetime_usec, null: false
      add :terminal_event_id, :binary_id
      add :terminal_event_type, :string, size: 40
      add :webhook_status, :string, size: 20
      add :webhook_attempts, :integer, null: false, default: 0
      add :last_webhook_error, :text
      add :webhook_delivered_at, :utc_datetime_usec

      timestamps(type: :utc_datetime_usec)
    end

    create unique_index(:charges, [:merchant_reference])
    create unique_index(:charges, [:idempotency_key])
    create unique_index(:charges, [:checkout_token])
    create constraint(:charges, :charges_amount_cents_range,
             check: "amount_cents BETWEEN 100 AND 1000000")
    create constraint(:charges, :charges_status_values,
             check: "status IN ('PENDING','PAID','REFUSED','EXPIRED')")
  end

  def down do
    drop table(:charges)
    Oban.Migration.down()
  end
end
