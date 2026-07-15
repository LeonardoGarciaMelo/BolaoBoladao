# This file is responsible for configuring your application
# and its dependencies with the aid of the Config module.
#
# This configuration file is loaded before any dependency and
# is restricted to this project.

# General application configuration
import Config

config :boladao_pay,
  ecto_repos: [BoladaoPay.Repo],
  generators: [timestamp_type: :utc_datetime, binary_id: true]

config :boladao_pay,
  merchant_api_key: "development-merchant-key",
  webhook_secret: "development-webhook-secret",
  webhook_url: "http://localhost:8080/wallet/webhooks/boladao-pay",
  checkout_base_url: "http://localhost:8080/pagamento",
  allowed_return_origins: ["http://localhost:8080", "http://localhost:4321"],
  allowed_cors_origins: ["http://localhost:8080", "http://localhost:4321"],
  charge_ttl_seconds: 900

config :boladao_pay, Oban,
  repo: BoladaoPay.Repo,
  queues: [webhooks: 5, expirations: 2]

# Configure the endpoint
config :boladao_pay, BoladaoPayWeb.Endpoint,
  url: [host: "localhost"],
  adapter: Bandit.PhoenixAdapter,
  render_errors: [
    formats: [json: BoladaoPayWeb.ErrorJSON],
    layout: false
  ],
  pubsub_server: BoladaoPay.PubSub,
  live_view: [signing_salt: "XOTrYPfC"]

# Configure Elixir's Logger
config :logger, :default_formatter,
  format: "$time $metadata[$level] $message\n",
  metadata: [:request_id]

# Use Jason for JSON parsing in Phoenix
config :phoenix, :json_library, Jason

# Import environment specific config. This must remain at the bottom
# of this file so it overrides the configuration defined above.
import_config "#{config_env()}.exs"
