package br.com.bolaoboladao.carteira.infrastructure.persistence.entity;

import br.com.bolaoboladao.carteira.domain.model.Ledger;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ledger")
@Data
public class LedgerEntity {
    @Id
    private UUID id;

    private UUID walletId;

    @Enumerated(EnumType.STRING)
    private Ledger.Reason reason;

    @Enumerated(EnumType.STRING)
    private Ledger.Operation operation;

    private BigDecimal amount;

    private LocalDateTime occurredAt;

    private UUID referenceId;

    private UUID createdBy;

    @Column(length = 500)
    private String note;

    @Column(unique = true, length = 100)
    private String idempotencyKey;

    private BigDecimal balanceBefore;

    private BigDecimal balanceAfter;
}
