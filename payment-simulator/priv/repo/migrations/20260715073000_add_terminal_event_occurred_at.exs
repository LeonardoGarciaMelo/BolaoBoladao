defmodule BoladaoPay.Repo.Migrations.AddTerminalEventOccurredAt do
  use Ecto.Migration

  def change do
    alter table(:charges) do
      add :terminal_event_occurred_at, :utc_datetime_usec
    end
  end
end
