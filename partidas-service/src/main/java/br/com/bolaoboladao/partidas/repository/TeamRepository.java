package br.com.bolaoboladao.partidas.repository;

import br.com.bolaoboladao.partidas.domain.Team;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class TeamRepository implements PanacheRepository<Team> {

    public Optional<Team> findByName(String name) {
        return find("name", name).firstResultOptional();
    }
}
