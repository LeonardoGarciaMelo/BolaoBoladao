import Config

# Configure your database
#
# The MIX_TEST_PARTITION environment variable can be used
# to provide built-in test partitioning in CI environment.
# Run `mix help test` for more information.
config :boladao_pay, BoladaoPay.Repo,
  username: System.get_env("PAYMENT_TEST_DB_USER", "bolao"),
  password: System.get_env("PAYMENT_TEST_DB_PASSWORD", "bolao"),
  hostname: System.get_env("PAYMENT_TEST_DB_HOST", "localhost"),
  port: String.to_integer(System.get_env("PAYMENT_TEST_DB_PORT", "5435")),
  database: System.get_env("PAYMENT_TEST_DB_NAME", "payment_simulator_test") <> (System.get_env("MIX_TEST_PARTITION") || ""),
  pool: Ecto.Adapters.SQL.Sandbox,
  pool_size: System.schedulers_online() * 2

# We don't run a server during test. If one is required,
# you can enable the server option below.
config :boladao_pay, BoladaoPayWeb.Endpoint,
  http: [ip: {127, 0, 0, 1}, port: 4002],
  secret_key_base: "8yYb8bZ5oYr/48x6he4XVesMThVBKIRJfW+pmjvqQ5WgENBSTFP+0hi75V+/8TrD",
  server: false

# Print only warnings and errors during test
config :logger, level: :warning

# Initialize plugs at runtime for faster test compilation
config :phoenix, :plug_init_mode, :runtime

# Sort query params output of verified routes for robust url comparisons
config :phoenix,
  sort_verified_routes_query_params: true

config :boladao_pay,
  merchant_api_key: "merchant-test-key",
  webhook_secret: "webhook-test-secret",
  webhook_url: "http://localhost:59999/wallet/webhooks/boladao-pay",
  checkout_base_url: "http://localhost:8080/pagamento",
  allowed_return_origins: ["http://localhost:8080"],
  allowed_cors_origins: ["http://localhost:8080"],
  charge_ttl_seconds: 900

config :boladao_pay, Oban,
  repo: BoladaoPay.Repo,
  testing: :manual,
  queues: false,
  plugins: false
