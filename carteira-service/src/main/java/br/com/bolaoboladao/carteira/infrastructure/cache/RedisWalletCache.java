package br.com.bolaoboladao.carteira.infrastructure.cache;

import br.com.bolaoboladao.carteira.domain.model.Ledger;
import br.com.bolaoboladao.carteira.domain.service.WalletCache;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class RedisWalletCache implements WalletCache {

    private static final Logger LOG = Logger.getLogger(RedisWalletCache.class);
    private static final String TTL_SECONDS = "300";

    @Inject
    ReactiveRedisDataSource reactiveRedisClient;

    @Inject
    ObjectMapper objectMapper;

    @Override
    @Fallback(fallbackMethod = "fallbackGetBalance")
    @Retry(maxRetries = 2, delay = 200)
    @CircuitBreaker(requestVolumeThreshold = 4, delay = 5000)
    public Uni<Optional<BigDecimal>> getBalance(UUID userId) {
        return reactiveRedisClient.value(String.class).get(userId.toString())
                .map(response -> response != null ? Optional.of(new BigDecimal(response)) : Optional.empty());
    }

    public Uni<Optional<BigDecimal>> fallbackGetBalance(UUID userId) {
        LOG.warnf("Redis indisponível para leitura de saldo: %s", userId);
        return Uni.createFrom().item(Optional.empty());
    }

    @Override
    @Fallback(fallbackMethod = "fallbackSetBalance")
    @Retry(maxRetries = 2, delay = 200)
    @CircuitBreaker(requestVolumeThreshold = 4, delay = 5000)
    public Uni<Void> setBalance(UUID userId, BigDecimal balance) {
        return reactiveRedisClient.value(String.class)
                .setex(userId.toString(), Long.parseLong(TTL_SECONDS), balance.toString())
                .replaceWithVoid();
    }

    public Uni<Void> fallbackSetBalance(UUID userId, BigDecimal balance) {
        LOG.warnf("Redis indisponível para escrita de saldo: %s", userId);
        return Uni.createFrom().voidItem();
    }

    @Override
    @Fallback(fallbackMethod = "fallbackInvalidateBalance")
    @Retry(maxRetries = 2, delay = 200)
    @CircuitBreaker(requestVolumeThreshold = 4, delay = 5000)
    public Uni<Void> invalidateBalance(UUID userId) {
        return reactiveRedisClient.key().del(userId.toString()).replaceWithVoid();
    }

    public Uni<Void> fallbackInvalidateBalance(UUID userId) {
        LOG.warnf("Redis indisponível para invalidação de saldo: %s", userId);
        return Uni.createFrom().voidItem();
    }

    @Override
    @Fallback(fallbackMethod = "fallbackGetStatement")
    @Retry(maxRetries = 2, delay = 200)
    @CircuitBreaker(requestVolumeThreshold = 4, delay = 5000)
    public Uni<Optional<List<Ledger>>> getStatement(UUID walletId, int page, int size) {
        return reactiveRedisClient.hash(String.class).hget(statementKey(walletId), page + ":" + size)
                .map(response -> {
                    if (response != null) {
                        try {
                            List<Ledger> statement = objectMapper.readValue(response, new TypeReference<>() {});
                            return Optional.of(statement);
                        } catch (JsonProcessingException e) {
                            LOG.warnf("Erro ao desserializar extrato: %s", e.getMessage());
                        }
                    }
                    return Optional.empty();
                });
    }

    public Uni<Optional<List<Ledger>>> fallbackGetStatement(UUID walletId, int page, int size) {
        LOG.warnf("Redis indisponível para leitura de extrato: %s", walletId);
        return Uni.createFrom().item(Optional.empty());
    }

    @Override
    @Fallback(fallbackMethod = "fallbackSetStatement")
    @Retry(maxRetries = 2, delay = 200)
    @CircuitBreaker(requestVolumeThreshold = 4, delay = 5000)
    public Uni<Void> setStatement(UUID walletId, int page, int size, List<Ledger> statement) {
        try {
            String json = objectMapper.writeValueAsString(statement);
            String key = statementKey(walletId);
            return reactiveRedisClient.hash(String.class).hset(key, page + ":" + size, json)
                    .chain(() -> reactiveRedisClient.key().expire(key, Long.parseLong(TTL_SECONDS)))
                    .replaceWithVoid();
        } catch (JsonProcessingException e) {
            LOG.warnf("Erro ao serializar extrato: %s", e.getMessage());
            return Uni.createFrom().voidItem();
        }
    }

    public Uni<Void> fallbackSetStatement(UUID walletId, int page, int size, List<Ledger> statement) {
        LOG.warnf("Redis indisponível para escrita de extrato: %s", walletId);
        return Uni.createFrom().voidItem();
    }

    @Override
    @Fallback(fallbackMethod = "fallbackInvalidateStatement")
    @Retry(maxRetries = 2, delay = 200)
    @CircuitBreaker(requestVolumeThreshold = 4, delay = 5000)
    public Uni<Void> invalidateStatement(UUID walletId) {
        return reactiveRedisClient.key().del(statementKey(walletId)).replaceWithVoid();
    }

    public Uni<Void> fallbackInvalidateStatement(UUID walletId) {
        LOG.warnf("Redis indisponível para invalidação de extrato: %s", walletId);
        return Uni.createFrom().voidItem();
    }

    private String statementKey(UUID walletId) {
        return walletId.toString() + ":statement";
    }
}
