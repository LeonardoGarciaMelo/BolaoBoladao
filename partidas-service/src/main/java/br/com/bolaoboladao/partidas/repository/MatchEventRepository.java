package br.com.bolaoboladao.partidas.repository;

import br.com.bolaoboladao.partidas.domain.MatchEvent;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class MatchEventRepository implements PanacheRepository<MatchEvent> {

    @Inject EntityManager entityManager;

    public List<MatchEvent> findByMatchId(UUID matchId) {
        return list("match.id = ?1 order by occurredAt asc", matchId);
    }

    public void lockCommandKey(String key) {
        entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(?1, 0))")
                .setParameter(1, key).getSingleResult();
    }

    public MatchEvent findByCommandKey(String key) {
        return find("commandKey", key).firstResult();
    }
}
