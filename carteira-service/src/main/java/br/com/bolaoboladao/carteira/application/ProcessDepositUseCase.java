package br.com.bolaoboladao.carteira.application;

import br.com.bolaoboladao.carteira.application.repository.ProcessedEventRepository;
import br.com.bolaoboladao.carteira.domain.model.Deposit;
import br.com.bolaoboladao.carteira.domain.model.Ledger;
import br.com.bolaoboladao.carteira.domain.model.Wallet;
import br.com.bolaoboladao.carteira.domain.repository.DepositRepository;
import br.com.bolaoboladao.carteira.domain.repository.LedgerRepository;
import br.com.bolaoboladao.carteira.domain.repository.WalletRepository;
import br.com.bolaoboladao.carteira.domain.service.WalletCache;
import br.com.bolaoboladao.carteira.infrastructure.payment.ProviderCharge;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@ApplicationScoped
public class ProcessDepositUseCase {
    @Inject DepositRepository deposits;
    @Inject WalletRepository wallets;
    @Inject LedgerRepository ledger;
    @Inject ProcessedEventRepository processedEvents;
    @Inject GetWalletBalanceUseCase balances;
    @Inject WalletCache cache;

    @WithTransaction
    public Uni<Deposit> execute(UUID eventId, String eventType, ProviderCharge providerCharge) {
        return deposits.findAndLockById(providerCharge.merchantReference()).flatMap(deposit -> {
            if (deposit == null) {
                return Uni.createFrom().failure(new ApiException(409, "DEPOSIT_NOT_FOUND",
                        "A cobrança não corresponde a um depósito conhecido."));
            }
            DepositTransactions.validateProvider(deposit, providerCharge);
            return processed(eventId).flatMap(alreadyProcessed -> {
                if (alreadyProcessed) return Uni.createFrom().item(deposit);
                return transition(deposit, providerCharge)
                        .flatMap(updated -> markProcessed(eventId, eventType).replaceWith(updated));
            });
        });
    }

    private Uni<Deposit> transition(Deposit deposit, ProviderCharge charge) {
        if (charge.status() == ProviderCharge.Status.PENDING) {
            return ensureProviderAttached(deposit, charge, Deposit.Status.PENDING, null);
        }

        Deposit.Status target = switch (charge.status()) {
            case PAID -> Deposit.Status.CONFIRMED;
            case REFUSED -> Deposit.Status.REFUSED;
            case EXPIRED -> Deposit.Status.EXPIRED;
            case PENDING -> throw new IllegalStateException("Pending status must be handled before terminal transition");
        };

        if (deposit.terminal()) {
            if (deposit.status() == target) return Uni.createFrom().item(deposit);
            return Uni.createFrom().failure(new ApiException(409, "DEPOSIT_TERMINAL_CONFLICT",
                    "O depósito já foi encerrado com outro resultado."));
        }

        return target == Deposit.Status.CONFIRMED
                ? credit(deposit, charge)
                : ensureProviderAttached(deposit, charge, target, null);
    }

    private Uni<Deposit> credit(Deposit deposit, ProviderCharge charge) {
        String idempotencyKey = "deposit-credit:" + deposit.id();
        return ledger.lockIdempotencyKey(idempotencyKey)
                .flatMap(ignored -> ledger.findByIdempotencyKey(idempotencyKey))
                .flatMap(existing -> existing != null
                        ? ensureProviderAttached(deposit, charge, Deposit.Status.CONFIRMED, Instant.now())
                        : lockOrCreateWallet(deposit.userId()).flatMap(wallet ->
                        balances.calculateBalanceFromDatabase(deposit.userId()).flatMap(before -> {
                            BigDecimal amount = BigDecimal.valueOf(deposit.amountCents(), 2);
                            Ledger entry = new Ledger(UUID.randomUUID(), wallet.id(), Ledger.Reason.DEPOSIT,
                                    Ledger.Operation.CREDIT, amount, LocalDateTime.now(), deposit.id(), null,
                                    "Crédito de depósito PIX fictício", idempotencyKey, before, before.add(amount));
                            return ledger.save(entry)
                                    .flatMap(ignored -> ensureProviderAttached(deposit, charge,
                                            Deposit.Status.CONFIRMED, Instant.now()))
                                    .flatMap(updated -> cache.invalidateBalance(deposit.userId()).replaceWith(updated))
                                    .flatMap(updated -> cache.invalidateStatement(wallet.id()).replaceWith(updated));
                        })));
    }

    private Uni<Wallet> lockOrCreateWallet(UUID userId) {
        return wallets.lockUser(userId)
                .flatMap(ignored -> wallets.findAndLockByUserId(userId))
                .flatMap(wallet -> {
                    if (wallet != null) return Uni.createFrom().item(wallet);
                    Wallet created = new Wallet(UUID.randomUUID(), userId);
                    return wallets.save(created).replaceWith(created);
                });
    }

    private Uni<Deposit> ensureProviderAttached(Deposit deposit, ProviderCharge charge,
                                                 Deposit.Status status, Instant confirmedAt) {
        Deposit updated = new Deposit(deposit.id(), deposit.userId(), deposit.amountCents(), status,
                deposit.idempotencyKey(), charge.chargeId(),
                charge.checkoutUrl() != null ? charge.checkoutUrl() : deposit.checkoutUrl(),
                charge.expiresAt() != null ? charge.expiresAt() : deposit.expiresAt(),
                deposit.createdAt(), Instant.now(), confirmedAt != null ? confirmedAt : deposit.confirmedAt());
        return deposits.update(updated).replaceWith(updated);
    }

    private Uni<Boolean> processed(UUID eventId) {
        return eventId == null ? Uni.createFrom().item(false) : processedEvents.isProcessed(eventId);
    }

    private Uni<Void> markProcessed(UUID eventId, String eventType) {
        return eventId == null ? Uni.createFrom().voidItem() : processedEvents.markAsProcessed(eventId, eventType);
    }
}
