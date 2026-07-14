package br.com.bolaoboladao.carteira.application;

import br.com.bolaoboladao.carteira.domain.exception.WalletNotFoundException;
import br.com.bolaoboladao.carteira.domain.model.Ledger;
import br.com.bolaoboladao.carteira.domain.model.Wallet;
import br.com.bolaoboladao.carteira.domain.repository.LedgerRepository;
import br.com.bolaoboladao.carteira.domain.repository.WalletRepository;
import br.com.bolaoboladao.carteira.domain.service.PaymentEventPublisher;
import br.com.bolaoboladao.carteira.domain.service.WalletCache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@ApplicationScoped
public class ProcessBetPaymentUseCase {

    @Inject
    WalletRepository walletRepository;

    @Inject
    LedgerRepository ledgerRepository;

    @Inject
    PaymentEventPublisher paymentEventPublisher;

    @Inject
    WalletCache walletCache;

    @Inject
    GetWalletBalanceUseCase getWalletBalanceUseCase;

    @Transactional
    public void executeBetCreated(UUID betId, UUID userId, BigDecimal amount) {
        Wallet wallet = findAndLockWalletOrThrow(userId);
        BigDecimal currentBalance = getWalletBalanceUseCase.execute(userId);

        if (currentBalance.compareTo(amount) < 0) {
            paymentEventPublisher.publishPaymentRefused(betId);
            return;
        }

        recordLedgerEntry(wallet.id(), Ledger.Reason.BET, Ledger.Operation.DEBIT, amount);
        walletCache.invalidateBalance(userId);
        walletCache.invalidateStatement(wallet.id());
        paymentEventPublisher.publishPaymentAccepted(betId);
    }

    @Transactional
    public void executeBetSettled(UUID betId, UUID userId, BigDecimal amount) {
        Wallet wallet = findAndLockWalletOrThrow(userId);
        recordLedgerEntry(wallet.id(), Ledger.Reason.WIN, Ledger.Operation.CREDIT, amount);
        walletCache.invalidateBalance(userId);
        walletCache.invalidateStatement(wallet.id());
    }

    private Wallet findAndLockWalletOrThrow(UUID userId) {
        return walletRepository.findAndLockByUserId(userId)
                .orElseThrow(() -> new WalletNotFoundException(userId));
    }

    private void recordLedgerEntry(UUID walletId, Ledger.Reason reason, Ledger.Operation operation, BigDecimal amount) {
        ledgerRepository.save(new Ledger(
                UUID.randomUUID(), walletId, reason, operation, amount, LocalDateTime.now()
        ));
    }
}
