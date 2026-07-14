package br.com.bolaoboladao.carteira.domain.service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface WalletCache {
    Optional<BigDecimal> getBalance(UUID userId);
    void setBalance(UUID userId, BigDecimal balance);
    void invalidateBalance(UUID userId);
}
