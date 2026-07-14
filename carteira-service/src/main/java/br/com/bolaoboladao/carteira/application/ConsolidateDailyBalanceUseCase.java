package br.com.bolaoboladao.carteira.application;

import br.com.bolaoboladao.carteira.domain.model.DailyBalance;
import br.com.bolaoboladao.carteira.domain.model.Ledger;
import br.com.bolaoboladao.carteira.domain.repository.DailyBalanceRepository;
import br.com.bolaoboladao.carteira.domain.repository.LedgerRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@ApplicationScoped
public class ConsolidateDailyBalanceUseCase {

    private final DailyBalanceRepository dailyBalanceRepository;
    private final LedgerRepository ledgerRepository;

    @Inject
    public ConsolidateDailyBalanceUseCase(DailyBalanceRepository dailyBalanceRepository,
                                          LedgerRepository ledgerRepository) {
        this.dailyBalanceRepository = dailyBalanceRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @WithTransaction
    public Uni<Void> execute(UUID walletId, LocalDate date) {
        return dailyBalanceRepository.findByWalletIdAndDate(walletId, date)
                .flatMap(dailyBalance -> {
                    if (dailyBalance != null) {
                        return Uni.createFrom().voidItem();
                    }
                    return calculateAndSaveBalance(walletId, date);
                });
    }

    private Uni<Void> calculateAndSaveBalance(UUID walletId, LocalDate date) {
        return dailyBalanceRepository.findLatestByWalletIdBeforeDate(walletId, date.minusDays(1))
                .flatMap(latestSnapshot -> {
                    BigDecimal previousBalance = latestSnapshot != null
                            ? latestSnapshot.balance()
                            : BigDecimal.ZERO;

                    LocalDate startDate = latestSnapshot != null
                            ? latestSnapshot.date().plusDays(1)
                            : LocalDate.ofEpochDay(0);

                    return ledgerRepository.findByWalletIdAndDateBetween(walletId, startDate, date)
                            .map(ledgers -> {
                                BigDecimal currentBalance = previousBalance;
                                for (Ledger entry : ledgers) {
                                    currentBalance = entry.applyTo(currentBalance);
                                }
                                return currentBalance;
                            });
                })
                .flatMap(currentBalance -> dailyBalanceRepository.save(new DailyBalance(UUID.randomUUID(), walletId, currentBalance, date)));
    }
}
