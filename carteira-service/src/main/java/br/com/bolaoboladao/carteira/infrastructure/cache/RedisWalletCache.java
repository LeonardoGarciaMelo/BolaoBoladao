package br.com.bolaoboladao.carteira.infrastructure.cache;

import br.com.bolaoboladao.carteira.domain.service.WalletCache;
import io.quarkus.redis.client.RedisClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class RedisWalletCache implements WalletCache {

    @Inject
    RedisClient redisClient;

    @Override
    public Optional<BigDecimal> getBalance(UUID userId) {
        io.vertx.redis.client.Response response = redisClient.get(userId.toString());
        if (response != null) {
            return Optional.of(new BigDecimal(response.toString()));
        }
        return Optional.empty();
    }

    @Override
    public void setBalance(UUID userId, BigDecimal balance) {
        redisClient.set(Arrays.asList(userId.toString(), balance.toString()));
    }

    @Override
    public void invalidateBalance(UUID userId) {
        redisClient.del(Arrays.asList(userId.toString()));
    }
}
