import Config

# O sandbox é acessível apenas pela rede do Compose e por 127.0.0.1:4000.
# TLS pertence a um proxy externo quando este simulador for usado fora do ambiente local.
config :boladao_pay, BoladaoPayWeb.Endpoint, force_ssl: false

# Do not print debug messages in production
config :logger, level: :info

# Runtime production configuration, including reading
# of environment variables, is done on config/runtime.exs.
