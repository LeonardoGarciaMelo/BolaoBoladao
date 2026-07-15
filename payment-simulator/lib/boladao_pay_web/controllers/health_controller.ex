defmodule BoladaoPayWeb.HealthController do
  use BoladaoPayWeb, :controller

  def show(conn, _params), do: json(conn, %{status: "UP", service: "boladao-pay-sandbox"})
end
