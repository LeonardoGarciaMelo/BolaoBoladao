package br.com.bolaoboladao.carteira.infrastructure.persistence.entity;

import br.com.bolaoboladao.carteira.domain.model.Ledger;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Data;

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
}
