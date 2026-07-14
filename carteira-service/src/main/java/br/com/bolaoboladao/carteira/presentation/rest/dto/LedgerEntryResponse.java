package br.com.bolaoboladao.carteira.presentation.rest.dto;

import br.com.bolaoboladao.carteira.domain.model.Ledger;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record LedgerEntryResponse(
        UUID id,
        String reason,
        String operation,
        BigDecimal amount,
        LocalDateTime occurredAt
) {
    public static LedgerEntryResponse from(Ledger ledger) {
        return new LedgerEntryResponse(
                ledger.id(),
                ledger.reason().name(),
                ledger.operation().name(),
                ledger.amount(),
                ledger.occurredAt()
        );
    }
}
