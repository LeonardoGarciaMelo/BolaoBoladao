package br.com.bolaoboladao.carteira.domain.repository;

import br.com.bolaoboladao.carteira.domain.model.Ledger;
import io.smallrye.mutiny.Uni;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LedgerRepository {
    Uni<Void> save(Ledger ledger);
    Uni<List<Ledger>> findByWalletIdAndDate(UUID walletId, LocalDate date);
    Uni<List<Ledger>> findByWalletIdAndDateBetween(UUID walletId, LocalDate startDate, LocalDate endDate);
    Uni<List<Ledger>> findByWalletIdPaged(UUID walletId, int page, int size);
}
