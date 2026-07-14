package br.com.bolaoboladao.carteira.infrastructure.persistence.entity;

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
    private String reason;
    
    @Enumerated(EnumType.STRING)
    private String operation;
    
    private BigDecimal amount;
    
    private LocalDateTime occurredAt;
}
