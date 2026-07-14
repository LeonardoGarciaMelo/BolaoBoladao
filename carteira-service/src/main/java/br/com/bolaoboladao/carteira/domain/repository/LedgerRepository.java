package br.com.bolaoboladao.carteira.domain.repository;

import br.com.bolaoboladao.carteira.domain.model.Ledger;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LedgerRepository {
    void save(Ledger ledger);
    List<Ledger> findByWalletIdAndDate(UUID walletId, LocalDate date);
    List<Ledger> findByWalletIdAndDateBetween(UUID walletId, LocalDate startDate, LocalDate endDate);
    List<Ledger> findByWalletIdPaged(UUID walletId, int page, int size);
}
