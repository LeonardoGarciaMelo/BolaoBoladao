package br.com.bolaoboladao.carteira.domain.repository;

import br.com.bolaoboladao.carteira.domain.model.DailyBalance;
import io.smallrye.mutiny.Uni;

import java.time.LocalDate;
import java.util.UUID;

public interface DailyBalanceRepository {
    Uni<Void> save(DailyBalance dailyBalance);

    Uni<DailyBalance> findByWalletIdAndDate(UUID walletId, LocalDate date);

    Uni<DailyBalance> findLatestByWalletIdBeforeDate(UUID walletId, LocalDate date);
}
