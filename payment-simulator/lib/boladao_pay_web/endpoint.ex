defmodule BoladaoPayWeb.Endpoint do
  use Phoenix.Endpoint, otp_app: :boladao_pay

  # Code reloading can be explicitly enabled under the
  # :code_reloader configuration of your endpoint.
  if code_reloading? do
    plug Phoenix.CodeReloader
    plug Phoenix.Ecto.CheckRepoStatus, otp_app: :boladao_pay
  end

  plug Plug.RequestId
  plug Plug.Telemetry, event_prefix: [:phoenix, :endpoint]

  plug Plug.Parsers,
    parsers: [:json],
    pass: ["application/json"],
    json_decoder: Phoenix.json_library()

  plug Plug.Head
  plug BoladaoPayWeb.Plugs.CheckoutCors
  plug BoladaoPayWeb.Router
end
