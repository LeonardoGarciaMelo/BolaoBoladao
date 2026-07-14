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
        LocalDateTime occurredAt
) {
    public enum Reason { WIN, DEPOSIT, BET, WITHDRAW }
    public enum Operation { CREDIT, DEBIT }

    public boolean isCredit() {
        return operation == Operation.CREDIT;
    }

    public boolean isDebit() {
        return operation == Operation.DEBIT;
    }
}
