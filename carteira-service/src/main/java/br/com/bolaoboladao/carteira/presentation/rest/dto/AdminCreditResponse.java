package br.com.bolaoboladao.carteira.presentation.rest.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminCreditResponse(
        UUID ledgerEntryId,
        UUID userId,
        UUID walletId,
        long amountCents,
        long balanceBeforeCents,
        long balanceAfterCents,
        String reason,
        UUID createdBy,
        LocalDateTime createdAt
) {
}
