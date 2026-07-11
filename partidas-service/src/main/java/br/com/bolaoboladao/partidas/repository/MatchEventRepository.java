package br.com.bolaoboladao.partidas.repository;

import br.com.bolaoboladao.partidas.domain.MatchEvent;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class MatchEventRepository implements PanacheRepository<MatchEvent> {

    public List<MatchEvent> findByMatchId(UUID matchId) {
        return list("match.id = ?1 order by occurredAt asc", matchId);
    }
}
