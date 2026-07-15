package br.com.bolaoboladao.carteira.presentation.rest.dto;

import br.com.bolaoboladao.carteira.domain.model.Deposit;

import java.time.Instant;
import java.util.UUID;

public record DepositResponse(
        UUID depositId,
        long amountCents,
        Deposit.Status status,
        String checkoutUrl,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt,
        Instant confirmedAt
) {
    public static DepositResponse from(Deposit deposit) {
        return new DepositResponse(deposit.id(), deposit.amountCents(), deposit.status(), deposit.checkoutUrl(),
                deposit.expiresAt(), deposit.createdAt(), deposit.updatedAt(), deposit.confirmedAt());
    }
}
