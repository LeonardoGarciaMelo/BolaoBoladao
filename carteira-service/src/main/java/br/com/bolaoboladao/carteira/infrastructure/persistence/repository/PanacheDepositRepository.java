package br.com.bolaoboladao.carteira.infrastructure.persistence.repository;

import br.com.bolaoboladao.carteira.domain.model.Deposit;
import br.com.bolaoboladao.carteira.domain.repository.DepositRepository;
import br.com.bolaoboladao.carteira.infrastructure.persistence.entity.DepositEntity;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PanacheDepositRepository implements DepositRepository, PanacheRepositoryBase<DepositEntity, UUID> {
    @Override
    public Uni<Void> save(Deposit deposit) {
        return persist(toEntity(deposit)).replaceWithVoid();
    }

    @Override
    public Uni<Void> update(Deposit deposit) {
        return findById(deposit.id()).flatMap(entity -> {
            if (entity == null) {
                return Uni.createFrom().failure(new IllegalStateException("Deposit does not exist"));
            }
            copy(deposit, entity);
            return Uni.createFrom().voidItem();
        });
    }

    @Override
    public Uni<Deposit> findByUserAndIdempotencyKey(UUID userId, String idempotencyKey) {
        return find("userId = ?1 and idempotencyKey = ?2", userId, idempotencyKey)
                .firstResult().onItem().ifNotNull().transform(this::toDomain);
    }

    @Override
    public Uni<Deposit> findByIdAndUser(UUID depositId, UUID userId) {
        return find("id = ?1 and userId = ?2", depositId, userId)
                .firstResult().onItem().ifNotNull().transform(this::toDomain);
    }

    @Override
    public Uni<Deposit> findAndLockById(UUID depositId) {
        return find("id", depositId).withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResult().onItem().ifNotNull().transform(this::toDomain);
    }

    @Override
    public Uni<List<Deposit>> findByUserPaged(UUID userId, int page, int size) {
        return find("userId", Sort.by("createdAt").descending(), userId)
                .page(Page.of(page, size)).list()
                .map(items -> items.stream().map(this::toDomain).toList());
    }

    @Override
    public Uni<Long> countByUser(UUID userId) {
        return count("userId", userId);
    }

    @Override
    public Uni<Void> lockIdempotencyKey(UUID userId, String idempotencyKey) {
        return getSession().flatMap(session -> session
                        .createNativeQuery("select pg_advisory_xact_lock(hashtextextended(:key, 0))::text")
                        .setParameter("key", userId + ":" + idempotencyKey)
                        .getSingleResult())
                .replaceWithVoid();
    }

    private DepositEntity toEntity(Deposit deposit) {
        var entity = new DepositEntity();
        copy(deposit, entity);
        return entity;
    }

    private void copy(Deposit source, DepositEntity target) {
        target.setId(source.id());
        target.setUserId(source.userId());
        target.setAmountCents(source.amountCents());
        target.setStatus(source.status());
        target.setIdempotencyKey(source.idempotencyKey());
        target.setProviderChargeId(source.providerChargeId());
        target.setCheckoutUrl(source.checkoutUrl());
        target.setExpiresAt(source.expiresAt());
        target.setCreatedAt(source.createdAt());
        target.setUpdatedAt(source.updatedAt());
        target.setConfirmedAt(source.confirmedAt());
    }

    private Deposit toDomain(DepositEntity entity) {
        return new Deposit(entity.getId(), entity.getUserId(), entity.getAmountCents(), entity.getStatus(),
                entity.getIdempotencyKey(), entity.getProviderChargeId(), entity.getCheckoutUrl(),
                entity.getExpiresAt(), entity.getCreatedAt(), entity.getUpdatedAt(), entity.getConfirmedAt());
    }
}
