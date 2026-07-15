package br.com.bolaoboladao.carteira.presentation.rest.dto;

import br.com.bolaoboladao.carteira.domain.model.Ledger;

import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserLedgerEntryResponse(
        UUID id,
        String reason,
        String operation,
        long amountCents,
        LocalDateTime occurredAt,
        UUID referenceId,
        String note
) {
    public static UserLedgerEntryResponse from(Ledger ledger) {
        return new UserLedgerEntryResponse(
                ledger.id(), ledger.reason().name(), ledger.operation().name(),
                ledger.amount().movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact(),
                ledger.occurredAt(), ledger.referenceId(), ledger.note()
        );
    }
}
