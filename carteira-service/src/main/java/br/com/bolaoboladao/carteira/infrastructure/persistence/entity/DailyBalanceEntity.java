package br.com.bolaoboladao.carteira.infrastructure.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Data;

@Entity
@Table(name = "daily_balance")
@Data
public class DailyBalanceEntity {
    @Id
    private UUID id;
    private UUID walletId;
    private BigDecimal balance;
    private LocalDate balanceDate;
}
