package br.com.bolaoboladao.partidas.repository;

import br.com.bolaoboladao.partidas.domain.Match;
import br.com.bolaoboladao.partidas.domain.MatchStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;

@ApplicationScoped
public class MatchRepository implements PanacheRepositoryBase<Match, UUID> {
    @Inject EntityManager entityManager;

    public List<Match> findByStatus(MatchStatus status) {
        return list("status", status);
    }

    public Match findByIdForUpdate(UUID id) {
        return find("id", id).withLock(LockModeType.PESSIMISTIC_WRITE).firstResult();
    }

    public List<UUID> findScheduledDue(OffsetDateTime now, int limit) {
        return entityManager.createQuery(
                        "select m.id from Match m where m.status = :status and m.start <= :now order by m.start",
                        UUID.class)
                .setParameter("status", MatchStatus.SCHEDULED).setParameter("now", now)
                .setMaxResults(limit).getResultList();
    }

    public List<UUID> findInProgressDue(OffsetDateTime now, int limit) {
        return entityManager.createQuery(
                        "select m.id from Match m where m.status = :status and m.expectedEnd <= :now order by m.expectedEnd",
                        UUID.class)
                .setParameter("status", MatchStatus.IN_PROGRESS).setParameter("now", now)
                .setMaxResults(limit).getResultList();
    }
}
