package br.com.bolaoboladao.carteira.infrastructure.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

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
