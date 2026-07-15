defmodule BoladaoPayWeb.MerchantChargeController do
  use BoladaoPayWeb, :controller

  alias BoladaoPay.Payments
  alias BoladaoPayWeb.ChargeJSON

  action_fallback BoladaoPayWeb.FallbackController

  def create(conn, params) do
    key = conn |> get_req_header("idempotency-key") |> List.first()

    case Payments.create_charge(params, key) do
      {:created, charge} -> conn |> put_status(:created) |> json(ChargeJSON.merchant(charge))
      {:replay, charge} -> json(conn, ChargeJSON.merchant(charge))
      {:error, _} = error -> error
    end
  end

  def show(conn, %{"id" => id}) do
    with {:ok, charge} <- Payments.get_merchant_charge(id) do
      json(conn, ChargeJSON.merchant(charge))
    end
  end
end
