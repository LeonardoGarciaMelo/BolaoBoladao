package br.com.bolaoboladao.carteira.infrastructure.persistence.repository;

import br.com.bolaoboladao.carteira.domain.model.DailyBalance;
import br.com.bolaoboladao.carteira.domain.repository.DailyBalanceRepository;
import br.com.bolaoboladao.carteira.infrastructure.persistence.entity.DailyBalanceEntity;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.util.UUID;

@ApplicationScoped
public class PanacheDailyBalanceRepository implements DailyBalanceRepository, PanacheRepositoryBase<DailyBalanceEntity, UUID> {

    @Override
    public Uni<Void> save(DailyBalance dailyBalance) {
        var entity = new DailyBalanceEntity();
        entity.setId(dailyBalance.id());
        entity.setWalletId(dailyBalance.walletId());
        entity.setBalance(dailyBalance.balance());
        entity.setBalanceDate(dailyBalance.date());
        return persist(entity).replaceWithVoid();
    }

    @Override
    public Uni<DailyBalance> findByWalletIdAndDate(UUID walletId, LocalDate date) {
        return find("walletId = ?1 and balanceDate = ?2", walletId, date).firstResult()
                .onItem().ifNotNull().transform(this::toDomain);
    }

    @Override
    public Uni<DailyBalance> findLatestByWalletIdBeforeDate(UUID walletId, LocalDate date) {
        return find("walletId = ?1 and balanceDate <= ?2 order by balanceDate desc", walletId, date)
                .firstResult()
                .onItem().ifNotNull().transform(this::toDomain);
    }

    private DailyBalance toDomain(DailyBalanceEntity entity) {
        return new DailyBalance(
                entity.getId(),
                entity.getWalletId(),
                entity.getBalance(),
                entity.getBalanceDate()
        );
    }
}
