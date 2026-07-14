package br.com.bolaoboladao.carteira.domain.service;

import br.com.bolaoboladao.carteira.domain.model.Ledger;
import io.smallrye.mutiny.Uni;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletCache {
    Uni<Optional<BigDecimal>> getBalance(UUID userId);
    Uni<Void> setBalance(UUID userId, BigDecimal balance);
    Uni<Void> invalidateBalance(UUID userId);

    Uni<Optional<List<Ledger>>> getStatement(UUID walletId, int page, int size);
    Uni<Void> setStatement(UUID walletId, int page, int size, List<Ledger> statement);
    Uni<Void> invalidateStatement(UUID walletId);
}
