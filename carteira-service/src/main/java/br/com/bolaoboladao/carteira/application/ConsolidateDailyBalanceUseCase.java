package br.com.bolaoboladao.carteira.application;

import br.com.bolaoboladao.carteira.domain.model.DailyBalance;
import br.com.bolaoboladao.carteira.domain.model.Ledger;
import br.com.bolaoboladao.carteira.domain.repository.DailyBalanceRepository;
import br.com.bolaoboladao.carteira.domain.repository.LedgerRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
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

    @Transactional
    public void execute(UUID walletId, LocalDate date) {
        if (dailyBalanceRepository.findByWalletIdAndDate(walletId, date).isPresent()) {
            return;
        }

        Optional<DailyBalance> latestSnapshot = dailyBalanceRepository
                .findLatestByWalletIdBeforeDate(walletId, date.minusDays(1));

        BigDecimal previousBalance = latestSnapshot
                .map(DailyBalance::balance)
                .orElse(BigDecimal.ZERO);

        LocalDate startDate = latestSnapshot
                .map(snapshot -> snapshot.date().plusDays(1))
                .orElse(LocalDate.ofEpochDay(0));

        BigDecimal currentBalance = ledgerRepository
                .findByWalletIdAndDateBetween(walletId, startDate, date)
                .stream()
                .reduce(previousBalance, this::applyLedgerEntry, BigDecimal::add);

        dailyBalanceRepository.save(new DailyBalance(UUID.randomUUID(), walletId, currentBalance, date));
    }

    private BigDecimal applyLedgerEntry(BigDecimal balance, Ledger entry) {
        return entry.isCredit()
                ? balance.add(entry.amount())
                : balance.subtract(entry.amount());
    }
}
