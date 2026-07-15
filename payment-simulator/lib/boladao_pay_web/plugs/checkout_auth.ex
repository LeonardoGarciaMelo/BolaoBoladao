defmodule BoladaoPayWeb.Plugs.CheckoutAuth do
  import Plug.Conn

  def init(opts), do: opts

  def call(conn, _opts) do
    case get_req_header(conn, "authorization") do
      ["Checkout " <> token] when byte_size(token) >= 32 -> assign(conn, :checkout_token, token)
      _ -> unauthorized(conn)
    end
  end

  defp unauthorized(conn) do
    conn
    |> put_resp_content_type("application/json")
    |> send_resp(401, Jason.encode!(%{error: "Invalid checkout token"}))
    |> halt()
  end
end
