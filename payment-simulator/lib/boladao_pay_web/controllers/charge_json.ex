defmodule BoladaoPayWeb.ChargeJSON do
  alias BoladaoPay.Payments

  def merchant(charge) do
    %{
      chargeId: charge.id,
      merchantReference: charge.merchant_reference,
      amountCents: charge.amount_cents,
      description: charge.description,
      status: charge.status,
      checkoutUrl: Payments.checkout_url(charge),
      returnUrl: charge.return_url,
      expiresAt: DateTime.to_iso8601(charge.expires_at),
      createdAt: DateTime.to_iso8601(charge.inserted_at),
      updatedAt: DateTime.to_iso8601(charge.updated_at),
      terminalEvent: terminal_event(charge),
      webhook: %{
        status: charge.webhook_status,
        attempts: charge.webhook_attempts,
        lastError: charge.last_webhook_error,
        deliveredAt: iso8601(charge.webhook_delivered_at)
      }
    }
  end

  def checkout(charge) do
    %{
      chargeId: charge.id,
      amountCents: charge.amount_cents,
      description: charge.description,
      status: charge.status,
      returnUrl: charge.return_url,
      expiresAt: DateTime.to_iso8601(charge.expires_at),
      updatedAt: DateTime.to_iso8601(charge.updated_at)
    }
  end

  defp terminal_event(%{terminal_event_id: nil}), do: nil

  defp terminal_event(charge) do
    %{
      eventId: charge.terminal_event_id,
      eventType: charge.terminal_event_type,
      occurredAt: iso8601(charge.terminal_event_occurred_at)
    }
  end

  defp iso8601(nil), do: nil
  defp iso8601(value), do: DateTime.to_iso8601(value)
end
