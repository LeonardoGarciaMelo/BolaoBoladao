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
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
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

    @WithTransaction
    public Uni<Void> executeBetCreated(UUID betId, UUID userId, BigDecimal amount) {
        return findAndLockWalletOrThrow(userId)
                .flatMap(wallet -> getWalletBalanceUseCase.execute(userId)
                        .flatMap(currentBalance -> {
                            if (currentBalance.compareTo(amount) < 0) {
                                return paymentEventPublisher.publishPaymentRefused(betId);
                            }
                            return recordLedgerEntry(wallet.id(), Ledger.Reason.BET, Ledger.Operation.DEBIT, amount)
                                    .flatMap(ignore -> walletCache.invalidateBalance(userId))
                                    .flatMap(ignore -> walletCache.invalidateStatement(wallet.id()))
                                    .flatMap(ignore -> paymentEventPublisher.publishPaymentAccepted(betId));
                        }));
    }

    @WithTransaction
    public Uni<Void> executeBetSettled(UUID betId, UUID userId, BigDecimal amount) {
        return findAndLockWalletOrThrow(userId)
                .flatMap(wallet -> recordLedgerEntry(wallet.id(), Ledger.Reason.WIN, Ledger.Operation.CREDIT, amount)
                        .flatMap(ignore -> walletCache.invalidateBalance(userId))
                        .flatMap(ignore -> walletCache.invalidateStatement(wallet.id())));
    }

    private Uni<Wallet> findAndLockWalletOrThrow(UUID userId) {
        return walletRepository.findAndLockByUserId(userId)
                .onItem().ifNull().failWith(() -> new WalletNotFoundException(userId));
    }

    private Uni<Void> recordLedgerEntry(UUID walletId, Ledger.Reason reason, Ledger.Operation operation, BigDecimal amount) {
        return ledgerRepository.save(new Ledger(
                UUID.randomUUID(), walletId, reason, operation, amount, LocalDateTime.now()
        ));
    }
}
