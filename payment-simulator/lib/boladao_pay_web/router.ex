defmodule BoladaoPayWeb.Router do
  use BoladaoPayWeb, :router

  pipeline :api do
    plug :accepts, ["json"]
  end

  pipeline :merchant do
    plug BoladaoPayWeb.Plugs.MerchantAuth
  end

  pipeline :checkout do
    plug BoladaoPayWeb.Plugs.CheckoutAuth
  end

  get "/health", BoladaoPayWeb.HealthController, :show

  scope "/api/v1/merchant", BoladaoPayWeb do
    pipe_through [:api, :merchant]
    post "/charges", MerchantChargeController, :create
    get "/charges/:id", MerchantChargeController, :show
  end

  scope "/api/v1", BoladaoPayWeb do
    pipe_through [:api, :checkout]
    get "/checkout", CheckoutController, :show
    post "/checkout/simulate", CheckoutController, :simulate
  end
end
