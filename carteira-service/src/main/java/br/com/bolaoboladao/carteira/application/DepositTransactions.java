package br.com.bolaoboladao.carteira.application;

import br.com.bolaoboladao.carteira.domain.model.Deposit;
import br.com.bolaoboladao.carteira.domain.repository.DepositRepository;
import br.com.bolaoboladao.carteira.infrastructure.payment.ProviderCharge;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class DepositTransactions {
    @Inject DepositRepository deposits;

    @WithTransaction
    public Uni<PreparedDeposit> prepare(UUID userId, long amountCents, String idempotencyKey) {
        return deposits.lockIdempotencyKey(userId, idempotencyKey)
                .flatMap(ignored -> deposits.findByUserAndIdempotencyKey(userId, idempotencyKey))
                .flatMap(existing -> {
                    if (existing != null) {
                        if (existing.amountCents() != amountCents) {
                            return Uni.createFrom().failure(new ApiException(409, "IDEMPOTENCY_CONFLICT",
                                    "A chave de idempotência já foi usada com outro valor."));
                        }
                        return Uni.createFrom().item(new PreparedDeposit(existing, false));
                    }

                    Instant now = Instant.now();
                    Deposit created = new Deposit(UUID.randomUUID(), userId, amountCents, Deposit.Status.CREATING,
                            idempotencyKey, null, null, null, now, now, null);
                    return deposits.save(created).replaceWith(new PreparedDeposit(created, true));
                });
    }

    @WithTransaction
    public Uni<Deposit> attachProvider(UUID depositId, ProviderCharge charge) {
        return deposits.findAndLockById(depositId).flatMap(deposit -> {
            if (deposit == null) {
                return Uni.createFrom().failure(new ApiException(404, "DEPOSIT_NOT_FOUND", "Depósito não encontrado."));
            }
            validateProvider(deposit, charge);
            if (deposit.providerChargeId() != null) return Uni.createFrom().item(deposit);

            Deposit updated = new Deposit(deposit.id(), deposit.userId(), deposit.amountCents(),
                    Deposit.Status.PENDING, deposit.idempotencyKey(), charge.chargeId(), charge.checkoutUrl(),
                    charge.expiresAt(), deposit.createdAt(), Instant.now(), null);
            return deposits.update(updated).replaceWith(updated);
        });
    }

    @WithTransaction
    public Uni<Deposit> getOwned(UUID userId, UUID depositId) {
        return deposits.findByIdAndUser(depositId, userId)
                .flatMap(deposit -> deposit == null
                        ? Uni.createFrom().failure(new ApiException(404, "DEPOSIT_NOT_FOUND", "Depósito não encontrado."))
                        : Uni.createFrom().item(deposit));
    }

    @WithTransaction
    public Uni<DepositPage> list(UUID userId, int page, int size) {
        return deposits.findByUserPaged(userId, page, size)
                .flatMap(items -> deposits.countByUser(userId)
                        .map(total -> new DepositPage(items, page, size, total)));
    }

    public static void validateProvider(Deposit deposit, ProviderCharge charge) {
        if (!deposit.id().equals(charge.merchantReference()) || deposit.amountCents() != charge.amountCents()
                || (deposit.providerChargeId() != null && !deposit.providerChargeId().equals(charge.chargeId()))) {
            throw new ApiException(409, "PROVIDER_DATA_MISMATCH", "Os dados da cobrança não correspondem ao depósito.");
        }
    }

    public record PreparedDeposit(Deposit deposit, boolean created) {}

    public record DepositPage(List<Deposit> items, int page, int size, long total) {}
}
