package br.com.bolaoboladao.carteira.application;

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

    private final WalletRepository walletRepository;
    private final DailyBalanceRepository dailyBalanceRepository;
    private final LedgerRepository ledgerRepository;
    private final WalletCache walletCache;

    @Inject
    public GetWalletBalanceUseCase(WalletRepository walletRepository,
                                   DailyBalanceRepository dailyBalanceRepository,
                                   LedgerRepository ledgerRepository,
                                   WalletCache walletCache) {
        this.walletRepository = walletRepository;
        this.dailyBalanceRepository = dailyBalanceRepository;
        this.ledgerRepository = ledgerRepository;
        this.walletCache = walletCache;
    }

    public Uni<BigDecimal> execute(UUID userId) {
        return walletCache.getBalance(userId)
                .flatMap(cachedBalance -> {
                    if (cachedBalance.isPresent()) {
                        return Uni.createFrom().item(cachedBalance.get());
                    }
                    return calculateAndCache(userId);
                });
    }

    public Uni<BigDecimal> calculateBalanceFromDatabase(UUID userId) {
        return walletRepository.findByUserId(userId)
                .onItem().ifNull().switchTo(() -> {
                    Wallet newWallet = new Wallet(UUID.randomUUID(), userId);
                    return walletRepository.save(newWallet).replaceWith(newWallet);
                })
                .flatMap(this::calculateCurrentBalance);
    }

    private Uni<BigDecimal> calculateAndCache(UUID userId) {
        return calculateBalanceFromDatabase(userId)
                .flatMap(currentBalance -> walletCache.setBalance(userId, currentBalance)
                        .replaceWith(currentBalance));
    }

    private Uni<BigDecimal> calculateCurrentBalance(Wallet wallet) {
        LocalDate today = LocalDate.now();
        return dailyBalanceRepository.findLatestByWalletIdBeforeDate(wallet.id(), today.minusDays(1))
                .flatMap(latestSnapshot -> {
                    BigDecimal previousBalance = latestSnapshot != null
                            ? latestSnapshot.balance()
                            : BigDecimal.ZERO;

                    LocalDate startDate = latestSnapshot != null
                            ? latestSnapshot.date().plusDays(1)
                            : LocalDate.ofEpochDay(0);

                    return ledgerRepository.findByWalletIdAndDateBetween(wallet.id(), startDate, today)
                            .map(ledgers -> {
                                BigDecimal currentBalance = previousBalance;
                                for (Ledger entry : ledgers) {
                                    currentBalance = entry.applyTo(currentBalance);
                                }
                                return currentBalance;
                            });
                });
    }
}
