package br.com.bolaoboladao.carteira.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record Ledger(
        UUID id,
        UUID walletId,
        Reason reason,
        Operation operation,
        BigDecimal amount,
        LocalDateTime occurredAt,
        UUID referenceId,
        UUID createdBy,
        String note,
        String idempotencyKey,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter
) {
    public Ledger(UUID id, UUID walletId, Reason reason, Operation operation, BigDecimal amount, LocalDateTime occurredAt) {
        this(id, walletId, reason, operation, amount, occurredAt, null, null, null, null, null, null);
    }
    public boolean isCredit() {
        return operation == Operation.CREDIT;
    }

    public boolean isDebit() {
        return operation == Operation.DEBIT;
    }

    public BigDecimal applyTo(BigDecimal balance) {
        return isCredit() ? balance.add(amount) : balance.subtract(amount);
    }

    public enum Reason {WIN, DEPOSIT, BET, WITHDRAW, ADMIN_CREDIT, BET_REFUND}

    public enum Operation {CREDIT, DEBIT}
}
