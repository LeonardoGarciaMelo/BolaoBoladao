package br.com.bolaoboladao.partidas.repository;

import br.com.bolaoboladao.partidas.domain.Match;
import br.com.bolaoboladao.partidas.domain.MatchStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class MatchRepository implements PanacheRepositoryBase<Match, UUID> {

    public List<Match> findByStatus(MatchStatus status) {
        return list("status", status);
    }
}
