package br.com.bolaoboladao.carteira.infrastructure.persistence.repository;

import br.com.bolaoboladao.carteira.domain.model.DailyBalance;
import br.com.bolaoboladao.carteira.domain.repository.DailyBalanceRepository;
import br.com.bolaoboladao.carteira.infrastructure.persistence.entity.DailyBalanceEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheDailyBalanceRepository implements DailyBalanceRepository, PanacheRepositoryBase<DailyBalanceEntity, UUID> {

    @Override
    public void save(DailyBalance dailyBalance) {
        var entity = new DailyBalanceEntity();
        entity.setId(dailyBalance.id());
        entity.setWalletId(dailyBalance.walletId());
        entity.setBalance(dailyBalance.balance());
        entity.setBalanceDate(dailyBalance.date());
        persist(entity);
    }

    @Override
    public Optional<DailyBalance> findByWalletIdAndDate(UUID walletId, LocalDate date) {
        return find("walletId = ?1 and balanceDate = ?2", walletId, date)
                .firstResultOptional()
                .map(entity -> new DailyBalance(
                        entity.getId(),
                        entity.getWalletId(),
                        entity.getBalance(),
                        entity.getBalanceDate()
                ));
    }
}
