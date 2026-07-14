package br.com.bolaoboladao.partidas.repository;

import br.com.bolaoboladao.partidas.domain.Team;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.Optional;
import java.util.List;

@ApplicationScoped
public class TeamRepository implements PanacheRepository<Team> {
    @Inject EntityManager entityManager;

    public void lockNames(String first, String second) {
        java.util.stream.Stream.of(first, second)
                .map(name -> name.toLowerCase(java.util.Locale.ROOT))
                .distinct()
                .sorted()
                .forEach(name -> entityManager.createNativeQuery(
                                "select pg_advisory_xact_lock(hashtextextended(:name, 0))::text")
                        .setParameter("name", name)
                        .getSingleResult());
    }

    public Optional<Team> findByName(String name) {
        return find("lower(name) = lower(?1)", name).firstResultOptional();
    }

    public List<Team> search(String query, int limit) {
        return find("lower(name) like lower(?1) order by name", "%" + query + "%")
                .page(0, limit)
                .list();
    }
}
