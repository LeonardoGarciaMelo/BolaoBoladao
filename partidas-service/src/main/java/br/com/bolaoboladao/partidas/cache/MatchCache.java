package br.com.bolaoboladao.partidas.cache;

import br.com.bolaoboladao.partidas.dto.MatchResponse;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@ApplicationScoped
public class MatchCache {

    private static final Logger LOG = Logger.getLogger(MatchCache.class);
    private final ValueCommands<String, MatchResponse> commands;

    @Inject
    public MatchCache(RedisDataSource ds) {
        this.commands = ds.value(MatchResponse.class);
    }

    public Optional<MatchResponse> get(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        try {
            MatchResponse cached = commands.get(key(id));
            if (cached != null) {
                LOG.infof("Cache HIT para a partida %s", id);
                return Optional.of(cached);
            }
            LOG.infof("Cache MISS para a partida %s", id);
            return Optional.empty();
        } catch (Exception e) {
            // Degradação graciosa conforme Aula 4: se o Redis falhar, vai ao banco
            LOG.error("Erro ao buscar no Redis cache, prosseguindo com busca no banco", e);
            return Optional.empty();
        }
    }

    public void put(UUID id, MatchResponse response) {
        if (id == null || response == null) {
            return;
        }
        try {
            // TTL de 1 hora com jitter (aleatório entre 0 e 5 minutos) para evitar Cache Stampede (Aula 4, Seção 5)
            long baseSeconds = Duration.ofHours(1).toSeconds();
            long jitterSeconds = ThreadLocalRandom.current().nextLong(0, 300);
            Duration ttl = Duration.ofSeconds(baseSeconds + jitterSeconds);

            commands.set(key(id), response, new SetArgs().ex(ttl.toSeconds()));
            LOG.infof("Partida %s gravada no cache com TTL de %d segundos", id, ttl.toSeconds());
        } catch (Exception e) {
            LOG.error("Erro ao gravar no Redis cache", e);
        }
    }

    public void evict(UUID id) {
        if (id == null) {
            return;
        }
        try {
            commands.getdel(key(id));
            LOG.infof("Cache invalidado para a partida %s", id);
        } catch (Exception e) {
            LOG.error("Erro ao invalidar o Redis cache", e);
        }
    }

    private String key(UUID id) {
        return "match:" + id.toString();
    }
}
