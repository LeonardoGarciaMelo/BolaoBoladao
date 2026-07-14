package br.com.bolaoboladao.carteira.application;

import br.com.bolaoboladao.carteira.domain.exception.WalletNotFoundException;
import br.com.bolaoboladao.carteira.domain.model.DailyBalance;
import br.com.bolaoboladao.carteira.domain.model.Ledger;
import br.com.bolaoboladao.carteira.domain.model.Wallet;
import br.com.bolaoboladao.carteira.domain.repository.DailyBalanceRepository;
import br.com.bolaoboladao.carteira.domain.repository.LedgerRepository;
import br.com.bolaoboladao.carteira.domain.repository.WalletRepository;
import br.com.bolaoboladao.carteira.domain.service.WalletCache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDate;
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

    public BigDecimal execute(UUID userId) {
        return walletCache.getBalance(userId).orElseGet(() -> calculateAndCache(userId));
    }

    private BigDecimal calculateAndCache(UUID userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new WalletNotFoundException(userId));

        LocalDate today = LocalDate.now();

        BigDecimal previousBalance = dailyBalanceRepository
                .findByWalletIdAndDate(wallet.id(), today.minusDays(1))
                .map(DailyBalance::balance)
                .orElse(BigDecimal.ZERO);

        BigDecimal currentBalance = ledgerRepository
                .findByWalletIdAndDate(wallet.id(), today)
                .stream()
                .reduce(previousBalance, this::applyLedgerEntry, BigDecimal::add);

        walletCache.setBalance(userId, currentBalance);
        return currentBalance;
    }

    private BigDecimal applyLedgerEntry(BigDecimal balance, Ledger entry) {
        return entry.isCredit()
                ? balance.add(entry.amount())
                : balance.subtract(entry.amount());
    }
}
