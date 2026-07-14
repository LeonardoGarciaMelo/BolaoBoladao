package br.com.bolaoboladao.carteira.infrastructure.cache;

import br.com.bolaoboladao.carteira.domain.model.Ledger;
import br.com.bolaoboladao.carteira.domain.service.WalletCache;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.client.RedisClient;
import io.vertx.redis.client.Response;
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

    private static final Logger LOG = Logger.getLogger(RedisWalletCache.java);
    private static final String TTL_SECONDS = "300";

    @Inject
    RedisClient redisClient;

    @Inject
    ObjectMapper objectMapper;

    @Override
    @Fallback(fallbackMethod = "fallbackGetBalance")
    @Retry(maxRetries = 2, delay = 200)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5000)
    public Optional<BigDecimal> getBalance(UUID userId) {
        Response response = redisClient.get(userId.toString());
        if (response != null) {
            return Optional.of(new BigDecimal(response.toString()));
        }
        return Optional.empty();
    }

    public Optional<BigDecimal> fallbackGetBalance(UUID userId) {
        LOG.warnf("Redis indisponível para leitura de saldo: %s", userId);
        return Optional.empty();
    }

    @Override
    @Fallback(fallbackMethod = "fallbackSetBalance")
    @Retry(maxRetries = 2, delay = 200)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5000)
    public void setBalance(UUID userId, BigDecimal balance) {
        redisClient.set(Arrays.asList(userId.toString(), balance.toString(), "EX", TTL_SECONDS));
    }

    public void fallbackSetBalance(UUID userId, BigDecimal balance) {
        LOG.warnf("Redis indisponível para escrita de saldo: %s", userId);
    }

    @Override
    @Fallback(fallbackMethod = "fallbackInvalidateBalance")
    @Retry(maxRetries = 2, delay = 200)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5000)
    public void invalidateBalance(UUID userId) {
        redisClient.del(Arrays.asList(userId.toString()));
    }

    public void fallbackInvalidateBalance(UUID userId) {
        LOG.warnf("Redis indisponível para invalidação de saldo: %s", userId);
    }

    @Override
    @Fallback(fallbackMethod = "fallbackGetStatement")
    @Retry(maxRetries = 2, delay = 200)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5000)
    public Optional<List<Ledger>> getStatement(UUID walletId, int page, int size) {
        Response response = redisClient.hget(statementKey(walletId), page + ":" + size);
        if (response != null) {
            try {
                List<Ledger> statement = objectMapper.readValue(response.toString(), new TypeReference<>() {});
                return Optional.of(statement);
            } catch (JsonProcessingException e) {
                LOG.warnf("Erro ao desserializar extrato: %s", e.getMessage());
            }
        }
        return Optional.empty();
    }

    public Optional<List<Ledger>> fallbackGetStatement(UUID walletId, int page, int size) {
        LOG.warnf("Redis indisponível para leitura de extrato: %s", walletId);
        return Optional.empty();
    }

    @Override
    @Fallback(fallbackMethod = "fallbackSetStatement")
    @Retry(maxRetries = 2, delay = 200)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5000)
    public void setStatement(UUID walletId, int page, int size, List<Ledger> statement) {
        try {
            String json = objectMapper.writeValueAsString(statement);
            String key = statementKey(walletId);
            redisClient.hset(Arrays.asList(key, page + ":" + size, json));
            redisClient.expire(key, TTL_SECONDS);
        } catch (JsonProcessingException e) {
            LOG.warnf("Erro ao serializar extrato: %s", e.getMessage());
        }
    }

    public void fallbackSetStatement(UUID walletId, int page, int size, List<Ledger> statement) {
        LOG.warnf("Redis indisponível para escrita de extrato: %s", walletId);
    }

    @Override
    @Fallback(fallbackMethod = "fallbackInvalidateStatement")
    @Retry(maxRetries = 2, delay = 200)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5000)
    public void invalidateStatement(UUID walletId) {
        redisClient.del(Arrays.asList(statementKey(walletId)));
    }

    public void fallbackInvalidateStatement(UUID walletId) {
        LOG.warnf("Redis indisponível para invalidação de extrato: %s", walletId);
    }

    private String statementKey(UUID walletId) {
        return walletId.toString() + ":statement";
    }
}
