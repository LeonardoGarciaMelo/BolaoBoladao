defmodule BoladaoPay.WebhookSignature do
  def sign(secret, timestamp, body) do
    :crypto.mac(:hmac, :sha256, secret, "#{timestamp}.#{body}")
    |> Base.encode16(case: :lower)
    |> then(&("v1=" <> &1))
  end
end
