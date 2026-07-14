package br.com.bolaoboladao.carteira.infrastructure.persistence.repository;

import br.com.bolaoboladao.carteira.domain.model.Ledger;
import br.com.bolaoboladao.carteira.domain.repository.LedgerRepository;
import br.com.bolaoboladao.carteira.infrastructure.persistence.entity.LedgerEntity;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PanacheLedgerRepository implements LedgerRepository, PanacheRepositoryBase<LedgerEntity, UUID> {

    @Override
    public Uni<Void> save(Ledger ledger) {
        var entity = new LedgerEntity();
        entity.setId(ledger.id());
        entity.setWalletId(ledger.walletId());
        entity.setReason(ledger.reason());
        entity.setOperation(ledger.operation());
        entity.setAmount(ledger.amount());
        entity.setOccurredAt(ledger.occurredAt());
        return persist(entity).replaceWithVoid();
    }

    @Override
    public Uni<List<Ledger>> findByWalletIdAndDate(UUID walletId, LocalDate date) {
        return find("walletId = ?1 and CAST(occurredAt AS date) = ?2 order by occurredAt asc", walletId, date)
                .list()
                .onItem().transform(list -> list.stream().map(this::toDomain).toList());
    }

    @Override
    public Uni<List<Ledger>> findByWalletIdAndDateBetween(UUID walletId, LocalDate startDate, LocalDate endDate) {
        return find("walletId = ?1 and CAST(occurredAt AS date) >= ?2 and CAST(occurredAt AS date) <= ?3 order by occurredAt asc",
                walletId, startDate, endDate)
                .list()
                .onItem().transform(list -> list.stream().map(this::toDomain).toList());
    }

    @Override
    public Uni<List<Ledger>> findByWalletIdPaged(UUID walletId, int page, int size) {
        return find("walletId", Sort.by("occurredAt").descending(), walletId)
                .page(Page.of(page, size))
                .list()
                .onItem().transform(list -> list.stream().map(this::toDomain).toList());
    }

    private Ledger toDomain(LedgerEntity entity) {
        return new Ledger(
                entity.getId(),
                entity.getWalletId(),
                entity.getReason(),
                entity.getOperation(),
                entity.getAmount(),
                entity.getOccurredAt()
        );
    }
}
