package br.com.bolaoboladao.carteira.application;

import br.com.bolaoboladao.carteira.domain.exception.WalletNotFoundException;
import br.com.bolaoboladao.carteira.domain.model.DailyBalance;
import br.com.bolaoboladao.carteira.domain.model.Ledger;
import br.com.bolaoboladao.carteira.domain.model.Wallet;
import br.com.bolaoboladao.carteira.domain.repository.DailyBalanceRepository;
import br.com.bolaoboladao.carteira.domain.repository.LedgerRepository;
import br.com.bolaoboladao.carteira.domain.repository.WalletRepository;
import br.com.bolaoboladao.carteira.domain.service.WalletCache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class GetWalletBalanceUseCase {

    @Inject
    WalletRepository walletRepository;

    @Inject
    DailyBalanceRepository dailyBalanceRepository;

    @Inject
    LedgerRepository ledgerRepository;

    @Inject
    WalletCache walletCache;

    public Uni<BigDecimal> execute(UUID userId) {
        return walletCache.getBalance(userId)
                .flatMap(cachedBalance -> {
                    if (cachedBalance.isPresent()) {
                        return Uni.createFrom().item(cachedBalance.get());
                    }
                    return calculateAndCache(userId);
                });
    }

    private Uni<BigDecimal> calculateAndCache(UUID userId) {
        return walletRepository.findByUserId(userId)
                .onItem().ifNull().failWith(() -> new WalletNotFoundException(userId))
                .flatMap(wallet -> {
                    LocalDate today = LocalDate.now();
                    return dailyBalanceRepository.findLatestByWalletIdBeforeDate(wallet.id(), today.minusDays(1))
                            .flatMap(latestSnapshot -> {
                                BigDecimal previousBalance = latestSnapshot
                                        .map(DailyBalance::balance)
                                        .orElse(BigDecimal.ZERO);

                                LocalDate startDate = latestSnapshot
                                        .map(snapshot -> snapshot.date().plusDays(1))
                                        .orElse(LocalDate.ofEpochDay(0));

                                return ledgerRepository.findByWalletIdAndDateBetween(wallet.id(), startDate, today)
                                        .map(ledgers -> {
                                            BigDecimal currentBalance = previousBalance;
                                            for (Ledger entry : ledgers) {
                                                currentBalance = applyLedgerEntry(currentBalance, entry);
                                            }
                                            return currentBalance;
                                        })
                                        .flatMap(currentBalance -> walletCache.setBalance(userId, currentBalance)
                                                .replaceWith(currentBalance));
                            });
                });
    }

    private BigDecimal applyLedgerEntry(BigDecimal balance, Ledger entry) {
        return entry.isCredit()
                ? balance.add(entry.amount())
                : balance.subtract(entry.amount());
    }
}
