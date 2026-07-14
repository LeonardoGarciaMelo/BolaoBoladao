package br.com.bolaoboladao.carteira.domain.repository;

import br.com.bolaoboladao.carteira.domain.model.DailyBalance;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface DailyBalanceRepository {
    void save(DailyBalance dailyBalance);
    Optional<DailyBalance> findByWalletIdAndDate(UUID walletId, LocalDate date);
    Optional<DailyBalance> findLatestByWalletIdBeforeDate(UUID walletId, LocalDate date);
}
