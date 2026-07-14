package br.com.bolaoboladao.carteira.domain.service;

import br.com.bolaoboladao.carteira.domain.model.Ledger;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletCache {
    Optional<BigDecimal> getBalance(UUID userId);
    void setBalance(UUID userId, BigDecimal balance);
    void invalidateBalance(UUID userId);

    Optional<List<Ledger>> getStatement(UUID walletId, int page, int size);
    void setStatement(UUID walletId, int page, int size, List<Ledger> statement);
    void invalidateStatement(UUID walletId);
}
