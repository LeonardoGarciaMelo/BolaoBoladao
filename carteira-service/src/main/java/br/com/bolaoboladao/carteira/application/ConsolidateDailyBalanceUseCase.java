package br.com.bolaoboladao.carteira.application;

import br.com.bolaoboladao.carteira.domain.model.DailyBalance;
import br.com.bolaoboladao.carteira.domain.model.Ledger;
import br.com.bolaoboladao.carteira.domain.repository.DailyBalanceRepository;
import br.com.bolaoboladao.carteira.domain.repository.LedgerRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ConsolidateDailyBalanceUseCase {

    @Inject
    DailyBalanceRepository dailyBalanceRepository;

    @Inject
    LedgerRepository ledgerRepository;

    @WithTransaction
    public Uni<Void> execute(UUID walletId, LocalDate date) {
        return dailyBalanceRepository.findByWalletIdAndDate(walletId, date)
                .flatMap(dailyBalance -> {
                    if (dailyBalance != null) {
                        return Uni.createFrom().voidItem();
                    }

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
                                                currentBalance = applyLedgerEntry(currentBalance, entry);
                                            }
                                            return currentBalance;
                                        })
                                        .flatMap(currentBalance -> dailyBalanceRepository.save(new DailyBalance(UUID.randomUUID(), walletId, currentBalance, date)));
                            });
                });
    }

    private BigDecimal applyLedgerEntry(BigDecimal balance, Ledger entry) {
        return entry.isCredit()
                ? balance.add(entry.amount())
                : balance.subtract(entry.amount());
    }
}
