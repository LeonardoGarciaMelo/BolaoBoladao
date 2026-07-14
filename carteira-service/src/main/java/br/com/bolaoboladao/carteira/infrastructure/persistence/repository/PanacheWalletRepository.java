package br.com.bolaoboladao.carteira.infrastructure.persistence.repository;

import br.com.bolaoboladao.carteira.domain.model.Wallet;
import br.com.bolaoboladao.carteira.domain.repository.WalletRepository;
import br.com.bolaoboladao.carteira.infrastructure.persistence.entity.WalletEntity;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;

@ApplicationScoped
public class PanacheWalletRepository implements WalletRepository, PanacheRepositoryBase<WalletEntity, UUID> {

    @Override
    public Uni<Void> save(Wallet wallet) {
        var entity = new WalletEntity();
        entity.setId(wallet.id());
        entity.setUserId(wallet.userId());
        return persist(entity).replaceWithVoid();
    }

    @Override
    public Uni<Wallet> findByUserId(UUID userId) {
        return find("userId", userId).firstResult()
                .onItem().ifNotNull().transform(this::toDomain);
    }

    @Override
    public Uni<Wallet> findAndLockByUserId(UUID userId) {
        return find("userId", userId).withLock(LockModeType.PESSIMISTIC_WRITE).firstResult()
                .onItem().ifNotNull().transform(this::toDomain);
    }

    @Override
    public Uni<List<Wallet>> findWalletsPaged(int page, int size) {
        return findAll().page(page, size).list()
                .onItem().transform(list -> list.stream().map(this::toDomain).toList());
    }

    @Override
    public Uni<Long> countWallets() {
        return count();
    }

    private Wallet toDomain(WalletEntity entity) {
        return new Wallet(entity.getId(), entity.getUserId());
    }
}
