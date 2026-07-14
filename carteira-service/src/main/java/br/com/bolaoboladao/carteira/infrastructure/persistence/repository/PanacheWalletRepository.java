package br.com.bolaoboladao.carteira.infrastructure.persistence.repository;

import br.com.bolaoboladao.carteira.domain.model.Wallet;
import br.com.bolaoboladao.carteira.domain.repository.WalletRepository;
import br.com.bolaoboladao.carteira.infrastructure.persistence.entity.WalletEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheWalletRepository implements WalletRepository, PanacheRepositoryBase<WalletEntity, UUID> {

    @Override
    public void save(Wallet wallet) {
        var entity = new WalletEntity();
        entity.setId(wallet.id());
        entity.setUserId(wallet.userId());
        persist(entity);
    }

    @Override
    public Optional<Wallet> findByUserId(UUID userId) {
        return find("userId", userId).firstResultOptional()
                .map(this::toDomain);
    }

    @Override
    public List<Wallet> findAll() {
        return listAll().stream()
                .map(this::toDomain)
                .toList();
    }

    private Wallet toDomain(WalletEntity entity) {
        return new Wallet(entity.getId(), entity.getUserId());
    }
}
