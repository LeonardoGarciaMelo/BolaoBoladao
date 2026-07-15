defmodule BoladaoPay.Payments do
  import Ecto.Query

  alias BoladaoPay.Payments.Charge
  alias BoladaoPay.Repo
  alias BoladaoPay.Workers.{ExpireChargeWorker, WebhookWorker}

  @terminal_statuses ~w(PAID REFUSED EXPIRED)

  def create_charge(attrs, idempotency_key) when is_map(attrs) do
    with :ok <- require_idempotency_key(idempotency_key),
         {:ok, result} <- Repo.transaction(fn -> create_charge_locked(attrs, idempotency_key) end) do
      result
    end
  end

  def get_merchant_charge(id) do
    case Ecto.UUID.cast(id) do
      {:ok, charge_id} -> fetch_and_expire(from(c in Charge, where: c.id == ^charge_id))
      :error -> {:error, :not_found}
    end
  end

  def get_checkout(token) when is_binary(token) do
    fetch_and_expire(from(c in Charge, where: c.checkout_token == ^token))
  end

  def get_checkout(_), do: {:error, :not_found}

  def simulate(token, outcome) when outcome in @terminal_statuses do
    Repo.transaction(fn ->
      case Repo.one(from(c in Charge, where: c.checkout_token == ^token, lock: "FOR UPDATE")) do
        nil -> Repo.rollback(:not_found)
        charge -> transition_locked(charge, outcome, DateTime.utc_now())
      end
    end)
    |> unwrap_transaction()
  end

  def simulate(_token, _outcome), do: {:error, :invalid_outcome}

  def expire_charge(charge_id) do
    Repo.transaction(fn ->
      case Repo.one(from(c in Charge, where: c.id == ^charge_id, lock: "FOR UPDATE")) do
        nil -> :not_found
        %Charge{status: "PENDING"} = charge ->
          if DateTime.compare(charge.expires_at, DateTime.utc_now()) in [:lt, :eq] do
            {:transitioned, terminalize!(charge, "EXPIRED")}
          else
            {:unchanged, charge}
          end

        charge -> {:unchanged, charge}
      end
    end)
  end

  def record_webhook_attempt(charge_id, attempt, error \\ nil) do
    case Repo.get(Charge, charge_id) do
      nil -> {:error, :not_found}
      charge -> charge |> Charge.webhook_attempt_changeset(attempt, error) |> Repo.update()
    end
  end

  def checkout_url(%Charge{checkout_token: token}) do
    String.trim_trailing(config(:checkout_base_url), "#") <> "#" <> token
  end

  def webhook_payload(%Charge{} = charge) do
    occurred_at = charge.terminal_event_occurred_at || charge.updated_at

    %{
      eventId: charge.terminal_event_id,
      eventType: charge.terminal_event_type,
      occurredAt: DateTime.to_iso8601(occurred_at),
      data: %{
        chargeId: charge.id,
        merchantReference: charge.merchant_reference,
        amountCents: charge.amount_cents,
        status: charge.status
      }
    }
  end

  defp create_charge_locked(attrs, idempotency_key) do
    Repo.query!("SELECT pg_advisory_xact_lock(hashtextextended($1, 0))", [idempotency_key])
    fingerprint = fingerprint(attrs)

    case Repo.get_by(Charge, idempotency_key: idempotency_key) do
      %Charge{idempotency_fingerprint: ^fingerprint} = charge -> {:replay, charge}
      %Charge{} -> Repo.rollback(:idempotency_conflict)
      nil -> insert_charge(attrs, idempotency_key, fingerprint)
    end
  end

  defp insert_charge(attrs, idempotency_key, fingerprint) do
    with {:ok, merchant_reference} <- cast_uuid(value(attrs, :merchantReference)),
         {:ok, amount_cents} <- cast_integer(value(attrs, :amountCents)),
         description when is_binary(description) <- value(attrs, :description),
         return_url when is_binary(return_url) <- value(attrs, :returnUrl),
         :ok <- validate_return_url(return_url) do
      expires_at = DateTime.add(DateTime.utc_now(), config(:charge_ttl_seconds), :second)

      changes = %{
        merchant_reference: merchant_reference,
        amount_cents: amount_cents,
        description: description,
        return_url: return_url,
        idempotency_key: idempotency_key,
        idempotency_fingerprint: fingerprint,
        checkout_token: random_token(),
        expires_at: expires_at
      }

      case %Charge{} |> Charge.create_changeset(changes) |> Repo.insert() do
        {:ok, charge} ->
          charge.id
          |> then(&ExpireChargeWorker.new(%{"charge_id" => &1}, scheduled_at: expires_at))
          |> Oban.insert!()

          {:created, charge}

        {:error, changeset} -> Repo.rollback({:validation, changeset})
      end
    else
      _ -> Repo.rollback(:invalid_payload)
    end
  end

  defp fetch_and_expire(query) do
    Repo.transaction(fn ->
      case Repo.one(from(c in query, lock: "FOR UPDATE")) do
        nil -> Repo.rollback(:not_found)
        charge -> maybe_expire_locked(charge)
      end
    end)
    |> unwrap_transaction()
  end

  defp maybe_expire_locked(%Charge{status: "PENDING"} = charge) do
    if DateTime.compare(charge.expires_at, DateTime.utc_now()) in [:lt, :eq] do
      terminalize!(charge, "EXPIRED")
    else
      charge
    end
  end

  defp maybe_expire_locked(charge), do: charge

  defp transition_locked(charge, requested, now) do
    charge =
      if charge.status == "PENDING" and DateTime.compare(charge.expires_at, now) in [:lt, :eq] do
        terminalize!(charge, "EXPIRED")
      else
        charge
      end

    cond do
      charge.status == "PENDING" -> terminalize!(charge, requested)
      charge.status == requested -> charge
      true -> Repo.rollback({:terminal_conflict, charge.status})
    end
  end

  defp terminalize!(charge, status) do
    event_id = Ecto.UUID.generate()
    event_type = "CHARGE_#{status}"
    occurred_at = DateTime.utc_now()

    updated =
      charge
      |> Charge.terminal_changeset(status, event_id, event_type, occurred_at)
      |> Repo.update!()

    %{"charge_id" => updated.id, "event_id" => event_id}
    |> WebhookWorker.new()
    |> Oban.insert!()

    updated
  end

  defp unwrap_transaction({:ok, value}), do: {:ok, value}
  defp unwrap_transaction({:error, reason}), do: {:error, reason}

  defp require_idempotency_key(key) when is_binary(key) do
    if String.trim(key) == "" or String.length(key) > 150,
      do: {:error, :missing_idempotency_key},
      else: :ok
  end

  defp require_idempotency_key(_), do: {:error, :missing_idempotency_key}

  defp validate_return_url(url) do
    uri = URI.parse(url)
    port = uri.port || if(uri.scheme == "https", do: 443, else: 80)
    default_port? = (uri.scheme == "https" and port == 443) or (uri.scheme == "http" and port == 80)
    origin = "#{uri.scheme}://#{uri.host}" <> if(default_port?, do: "", else: ":#{port}")

    if uri.scheme in ["http", "https"] and is_binary(uri.host) and origin in config(:allowed_return_origins),
      do: :ok,
      else: {:error, :invalid_return_url}
  end

  defp fingerprint(attrs) do
    [:merchantReference, :amountCents, :description, :returnUrl]
    |> Enum.map(&to_string(value(attrs, &1) || ""))
    |> Enum.join(<<31>>)
    |> then(&:crypto.hash(:sha256, &1))
    |> Base.encode16(case: :lower)
  end

  defp value(attrs, key), do: Map.get(attrs, key) || Map.get(attrs, Atom.to_string(key))
  defp cast_uuid(value), do: Ecto.UUID.cast(value)
  defp cast_integer(value) when is_integer(value), do: {:ok, value}
  defp cast_integer(value) when is_binary(value) do
    case Integer.parse(value) do
      {parsed, ""} -> {:ok, parsed}
      _ -> :error
    end
  end
  defp cast_integer(_), do: :error

  defp random_token do
    32 |> :crypto.strong_rand_bytes() |> Base.url_encode64(padding: false)
  end

  defp config(key), do: Application.fetch_env!(:boladao_pay, key)
end
