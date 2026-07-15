package br.com.bolaoboladao.carteira.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Deposit(
        UUID id,
        UUID userId,
        long amountCents,
        Status status,
        String idempotencyKey,
        UUID providerChargeId,
        String checkoutUrl,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt,
        Instant confirmedAt
) {
    public enum Status {CREATING, PENDING, CONFIRMED, REFUSED, EXPIRED}

    public boolean terminal() {
        return status == Status.CONFIRMED || status == Status.REFUSED || status == Status.EXPIRED;
    }
}
