defmodule BoladaoPayWeb.Plugs.MerchantAuth do
  import Plug.Conn

  def init(opts), do: opts

  def call(conn, _opts) do
    expected = Application.fetch_env!(:boladao_pay, :merchant_api_key)

    case get_req_header(conn, "authorization") do
      ["Bearer " <> provided] when byte_size(provided) == byte_size(expected) ->
        if Plug.Crypto.secure_compare(provided, expected), do: conn, else: unauthorized(conn)

      _ -> unauthorized(conn)
    end
  end

  defp unauthorized(conn) do
    conn
    |> put_resp_content_type("application/json")
    |> send_resp(401, Jason.encode!(%{error: "Invalid merchant credentials"}))
    |> halt()
  end
end
