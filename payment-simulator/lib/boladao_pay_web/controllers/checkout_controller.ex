defmodule BoladaoPayWeb.CheckoutController do
  use BoladaoPayWeb, :controller

  alias BoladaoPay.Payments
  alias BoladaoPayWeb.ChargeJSON

  action_fallback BoladaoPayWeb.FallbackController

  def show(conn, _params) do
    with {:ok, charge} <- Payments.get_checkout(conn.assigns.checkout_token) do
      json(conn, ChargeJSON.checkout(charge))
    end
  end

  def simulate(conn, %{"outcome" => outcome}) do
    with {:ok, charge} <- Payments.simulate(conn.assigns.checkout_token, String.upcase(outcome)) do
      json(conn, ChargeJSON.checkout(charge))
    end
  end

  def simulate(_conn, _params), do: {:error, :invalid_outcome}
end
