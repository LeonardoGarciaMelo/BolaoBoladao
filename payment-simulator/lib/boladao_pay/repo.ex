defmodule BoladaoPay.Repo do
  use Ecto.Repo,
    otp_app: :boladao_pay,
    adapter: Ecto.Adapters.Postgres
end
