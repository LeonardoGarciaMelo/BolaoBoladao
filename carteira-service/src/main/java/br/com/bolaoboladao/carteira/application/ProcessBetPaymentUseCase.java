package br.com.bolaoboladao.carteira.application;

import br.com.bolaoboladao.carteira.domain.model.Ledger;
import br.com.bolaoboladao.carteira.domain.model.Wallet;
import br.com.bolaoboladao.carteira.domain.repository.LedgerRepository;
import br.com.bolaoboladao.carteira.domain.repository.WalletRepository;
import br.com.bolaoboladao.carteira.domain.service.PaymentEventPublisher;
import br.com.bolaoboladao.carteira.domain.service.WalletCache;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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
        String idempotencyKey = "bet-debit:" + betId;
        return ledgerRepository.lockIdempotencyKey(idempotencyKey)
                .flatMap(ignored -> ledgerRepository.findByIdempotencyKey(idempotencyKey))
                .flatMap(existing -> existing != null
                        ? paymentEventPublisher.publishPaymentAccepted(betId)
                        : findAndLockWalletOrThrow(userId)
                .flatMap(wallet -> getWalletBalanceUseCase.calculateBalanceFromDatabase(userId)
                        .flatMap(currentBalance -> {
                            if (currentBalance.compareTo(amount) < 0) {
                                return paymentEventPublisher.publishPaymentRefused(betId);
                            }
                            return ledgerRepository.save(new Ledger(UUID.randomUUID(), wallet.id(), Ledger.Reason.BET,
                                            Ledger.Operation.DEBIT, amount, LocalDateTime.now(), betId, null,
                                            "Débito do palpite", idempotencyKey, currentBalance, currentBalance.subtract(amount)))
                                    .flatMap(ignore -> walletCache.invalidateBalance(userId))
                                    .flatMap(ignore -> walletCache.invalidateStatement(wallet.id()))
                                    .flatMap(ignore -> paymentEventPublisher.publishPaymentAccepted(betId));
                        })));
    }

    @WithTransaction
    public Uni<Void> executeBetSettled(UUID betId, UUID userId, BigDecimal amount) {
        String idempotencyKey = "bet-settled:" + betId;
        return ledgerRepository.lockIdempotencyKey(idempotencyKey)
                .flatMap(ignored -> ledgerRepository.findByIdempotencyKey(idempotencyKey))
                .flatMap(existing -> {
                    if (existing != null) {
                        return Uni.createFrom().voidItem();
                    }
                    return findAndLockWalletOrThrow(userId)
                            .flatMap(wallet -> getWalletBalanceUseCase.calculateBalanceFromDatabase(userId)
                                    .flatMap(currentBalance -> ledgerRepository.save(new Ledger(
                                            UUID.randomUUID(), wallet.id(), Ledger.Reason.WIN,
                                            Ledger.Operation.CREDIT, amount, LocalDateTime.now(), betId, null,
                                            "Prêmio de aposta ganha", idempotencyKey, currentBalance, currentBalance.add(amount)
                                    )).flatMap(ignore -> walletCache.invalidateBalance(userId))
                                            .flatMap(ignore -> walletCache.invalidateStatement(wallet.id()))));
                });
    }

    @WithTransaction
    public Uni<Void> executeBetRefundRequested(UUID betId, UUID userId, BigDecimal amount) {
        String idempotencyKey = "bet-refund:" + betId;
        return ledgerRepository.lockIdempotencyKey(idempotencyKey)
                .flatMap(ignored -> ledgerRepository.findByIdempotencyKey(idempotencyKey))
                .flatMap(existing -> {
                    if (existing != null) {
                        return paymentEventPublisher.publishPaymentRefunded(betId);
                    }
                    return ledgerRepository.findByIdempotencyKey("bet-debit:" + betId)
                            .flatMap(debit -> {
                                if (debit == null || debit.reason() != Ledger.Reason.BET || !debit.isDebit()) {
                                    return Uni.createFrom().failure(new IllegalStateException(
                                            "Débito original não encontrado para o estorno " + betId));
                                }
                                return findAndLockWalletOrThrow(userId)
                                        .flatMap(wallet -> {
                                            if (!wallet.id().equals(debit.walletId())) {
                                                return Uni.createFrom().failure(new IllegalStateException(
                                                        "Débito original não pertence à carteira do usuário " + userId));
                                            }
                                            return getWalletBalanceUseCase.calculateBalanceFromDatabase(userId)
                                                .flatMap(before -> ledgerRepository.save(new Ledger(
                                                        UUID.randomUUID(), wallet.id(), Ledger.Reason.BET_REFUND,
                                                        Ledger.Operation.CREDIT, debit.amount(), LocalDateTime.now(), debit.id(),
                                                        null, "Estorno por cancelamento de partida", idempotencyKey,
                                                        before, before.add(debit.amount())
                                                )).flatMap(ignored -> walletCache.invalidateBalance(userId))
                                                        .flatMap(ignored -> walletCache.invalidateStatement(wallet.id()))
                                                        .flatMap(ignored -> paymentEventPublisher.publishPaymentRefunded(betId)));
                                        });
                            });
                });
    }

    private Uni<Wallet> findAndLockWalletOrThrow(UUID userId) {
        return walletRepository.lockUser(userId)
                .flatMap(ignored -> walletRepository.findAndLockByUserId(userId)
                        .onItem().ifNull().switchTo(() -> {
                            Wallet newWallet = new Wallet(UUID.randomUUID(), userId);
                            return walletRepository.save(newWallet).replaceWith(newWallet);
                        }));
    }

    private Uni<Void> recordLedgerEntry(UUID walletId, Ledger.Reason reason, Ledger.Operation operation, BigDecimal amount) {
        return ledgerRepository.save(new Ledger(
                UUID.randomUUID(), walletId, reason, operation, amount, LocalDateTime.now()
        ));
    }
}
