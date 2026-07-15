defmodule BoladaoPayWeb.FallbackController do
  use BoladaoPayWeb, :controller

  def call(conn, {:error, :not_found}), do: error(conn, 404, "Charge not found")
  def call(conn, {:error, :missing_idempotency_key}), do: error(conn, 400, "Idempotency-Key is required")
  def call(conn, {:error, :idempotency_conflict}), do: error(conn, 409, "Idempotency-Key already used with another payload")
  def call(conn, {:error, :invalid_payload}), do: error(conn, 400, "Invalid charge payload")
  def call(conn, {:error, :invalid_return_url}), do: error(conn, 400, "Invalid return URL")
  def call(conn, {:error, :invalid_outcome}), do: error(conn, 400, "Invalid simulation outcome")
  def call(conn, {:error, {:terminal_conflict, status}}), do: error(conn, 409, "Charge is already #{status}", %{status: status})

  def call(conn, {:error, {:validation, changeset}}) do
    details = Ecto.Changeset.traverse_errors(changeset, fn {message, opts} ->
      Regex.replace(~r"%{(\w+)}", message, fn _, key ->
        opts |> Keyword.get(String.to_existing_atom(key), key) |> to_string()
      end)
    end)

    error(conn, 400, "Invalid charge payload", %{details: details})
  end

  defp error(conn, status, message, extra \\ %{}) do
    conn |> put_status(status) |> json(Map.merge(%{error: message}, extra))
  end
end
