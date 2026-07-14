package br.com.bolaoboladao.carteira.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DailyBalance(UUID id, UUID walletId, BigDecimal balance, LocalDate date) {
}
