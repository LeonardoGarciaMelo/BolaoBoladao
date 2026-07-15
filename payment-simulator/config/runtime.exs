import Config

# config/runtime.exs is executed for all environments, including
# during releases. It is executed after compilation and before the
# system starts, so it is typically used to load production configuration
# and secrets from environment variables or elsewhere. Do not define
# any compile-time configuration in here, as it won't be applied.
# The block below contains prod specific runtime configuration.

# ## Using releases
#
# If you use `mix release`, you need to explicitly enable the server
# by passing the PHX_SERVER=true when you start it:
#
#     PHX_SERVER=true bin/boladao_pay start
#
# Alternatively, you can use `mix phx.gen.release` to generate a `bin/server`
# script that automatically sets the env var above.
if System.get_env("PHX_SERVER") do
  config :boladao_pay, BoladaoPayWeb.Endpoint, server: true
end

read_secret = fn env_name, file_env_name, default ->
  case System.get_env(file_env_name) do
    nil -> System.get_env(env_name, default)
    path -> path |> File.read!() |> String.trim()
  end
end

split_csv = fn value -> value |> String.split(",", trim: true) |> Enum.map(&String.trim/1) end

if config_env() != :test do
  config :boladao_pay,
    merchant_api_key: read_secret.("PAYMENT_MERCHANT_API_KEY", "PAYMENT_MERCHANT_API_KEY_FILE", "development-merchant-key"),
    webhook_secret: read_secret.("PAYMENT_WEBHOOK_SECRET", "PAYMENT_WEBHOOK_SECRET_FILE", "development-webhook-secret"),
    webhook_url: System.get_env("PAYMENT_WEBHOOK_URL", "http://localhost:8080/wallet/webhooks/boladao-pay"),
    checkout_base_url: System.get_env("PAYMENT_CHECKOUT_BASE_URL", "http://localhost:8080/pagamento"),
    allowed_return_origins: split_csv.(System.get_env("PAYMENT_RETURN_ORIGINS", "http://localhost:8080")),
    allowed_cors_origins: split_csv.(System.get_env("PAYMENT_CORS_ORIGINS", "http://localhost:8080")),
    charge_ttl_seconds: String.to_integer(System.get_env("PAYMENT_CHARGE_TTL_SECONDS", "900"))
end

config :boladao_pay, BoladaoPayWeb.Endpoint,
  http: [port: String.to_integer(System.get_env("PORT", "4000"))]

if config_env() == :prod do
  database_url =
    System.get_env("DATABASE_URL") ||
      raise """
      environment variable DATABASE_URL is missing.
      For example: ecto://USER:PASS@HOST/DATABASE
      """

  maybe_ipv6 = if System.get_env("ECTO_IPV6") in ~w(true 1), do: [:inet6], else: []

  config :boladao_pay, BoladaoPay.Repo,
    # ssl: true,
    url: database_url,
    pool_size: String.to_integer(System.get_env("POOL_SIZE") || "10"),
    # For machines with several cores, consider starting multiple pools of `pool_size`
    # pool_count: 4,
    socket_options: maybe_ipv6

  # The secret key base is used to sign/encrypt cookies and other secrets.
  # A default value is used in config/dev.exs and config/test.exs but you
  # want to use a different value for prod and you most likely don't want
  # to check this value into version control, so we use an environment
  # variable instead.
  secret_key_base =
    read_secret.("SECRET_KEY_BASE", "SECRET_KEY_BASE_FILE", nil) ||
      raise """
      environment variable SECRET_KEY_BASE is missing.
      You can generate one by calling: mix phx.gen.secret
      """

  host = System.get_env("PHX_HOST") || "localhost"

  config :boladao_pay, :dns_cluster_query, System.get_env("DNS_CLUSTER_QUERY")

  config :boladao_pay, BoladaoPayWeb.Endpoint,
    url: [host: host, port: String.to_integer(System.get_env("PORT", "4000")), scheme: System.get_env("PHX_SCHEME", "http")],
    http: [
      # Enable IPv6 and bind on all interfaces.
      # Set it to  {0, 0, 0, 0, 0, 0, 0, 1} for local network only access.
      # See the documentation on https://bandit.hexdocs.pm/Bandit.html#t:options/0
      # for details about using IPv6 vs IPv4 and loopback vs public addresses.
      ip: {0, 0, 0, 0, 0, 0, 0, 0}
    ],
    secret_key_base: secret_key_base

  # ## SSL Support
  #
  # To get SSL working, you will need to add the `https` key
  # to your endpoint configuration:
  #
  #     config :boladao_pay, BoladaoPayWeb.Endpoint,
  #       https: [
  #         ...,
  #         port: 443,
  #         cipher_suite: :strong,
  #         keyfile: System.get_env("SOME_APP_SSL_KEY_PATH"),
  #         certfile: System.get_env("SOME_APP_SSL_CERT_PATH")
  #       ]
  #
  # The `cipher_suite` is set to `:strong` to support only the
  # latest and more secure SSL ciphers. This means old browsers
  # and clients may not be supported. You can set it to
  # `:compatible` for wider support.
  #
  # `:keyfile` and `:certfile` expect an absolute path to the key
  # and cert in disk or a relative path inside priv, for example
  # "priv/ssl/server.key". For all supported SSL configuration
  # options, see https://plug.hexdocs.pm/Plug.SSL.html#configure/1
  #
  # We also recommend setting `force_ssl` in your config/prod.exs,
  # ensuring no data is ever sent via http, always redirecting to https:
  #
  #     config :boladao_pay, BoladaoPayWeb.Endpoint,
  #       force_ssl: [hsts: true]
  #
  # Check `Plug.SSL` for all available options in `force_ssl`.
end
