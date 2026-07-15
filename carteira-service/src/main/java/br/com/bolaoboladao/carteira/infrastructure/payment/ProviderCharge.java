package br.com.bolaoboladao.carteira.infrastructure.payment;

import java.time.Instant;
import java.util.UUID;

public record ProviderCharge(
        UUID chargeId,
        UUID merchantReference,
        long amountCents,
        Status status,
        String checkoutUrl,
        Instant expiresAt,
        Instant createdAt
) {
    public enum Status {
        PENDING,
        PAID,
        REFUSED,
        EXPIRED;

        public static Status from(String value) {
            try {
                return Status.valueOf(value);
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Invalid payment provider status", exception);
            }
        }
    }
}
