package br.com.bolaoboladao.carteira.application;

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

    private final WalletRepository walletRepository;
    private final LedgerRepository ledgerRepository;
    private final PaymentEventPublisher paymentEventPublisher;
    private final WalletCache walletCache;
    private final GetWalletBalanceUseCase getWalletBalanceUseCase;

    @Inject
    public ProcessBetPaymentUseCase(WalletRepository walletRepository,
                                    LedgerRepository ledgerRepository,
                                    PaymentEventPublisher paymentEventPublisher,
                                    WalletCache walletCache,
                                    GetWalletBalanceUseCase getWalletBalanceUseCase) {
        this.walletRepository = walletRepository;
        this.ledgerRepository = ledgerRepository;
        this.paymentEventPublisher = paymentEventPublisher;
        this.walletCache = walletCache;
        this.getWalletBalanceUseCase = getWalletBalanceUseCase;
    }

    @WithTransaction
    public Uni<Void> executeBetCreated(UUID betId, UUID userId, BigDecimal amount) {
        return findAndLockWalletOrThrow(userId)
                .flatMap(wallet -> getWalletBalanceUseCase.calculateBalanceFromDatabase(userId)
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
                .onItem().ifNull().switchTo(() -> {
                    Wallet newWallet = new Wallet(UUID.randomUUID(), userId);
                    return walletRepository.save(newWallet).replaceWith(newWallet);
                });
    }

    private Uni<Void> recordLedgerEntry(UUID walletId, Ledger.Reason reason, Ledger.Operation operation, BigDecimal amount) {
        return ledgerRepository.save(new Ledger(
                UUID.randomUUID(), walletId, reason, operation, amount, LocalDateTime.now()
        ));
    }
}
