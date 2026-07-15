package br.com.bolaoboladao.carteira.domain.repository;

import br.com.bolaoboladao.carteira.domain.model.Deposit;
import io.smallrye.mutiny.Uni;

import java.util.List;
import java.util.UUID;

public interface DepositRepository {
    Uni<Void> save(Deposit deposit);

    Uni<Void> update(Deposit deposit);

    Uni<Deposit> findByUserAndIdempotencyKey(UUID userId, String idempotencyKey);

    Uni<Deposit> findByIdAndUser(UUID depositId, UUID userId);

    Uni<Deposit> findAndLockById(UUID depositId);

    Uni<List<Deposit>> findByUserPaged(UUID userId, int page, int size);

    Uni<Long> countByUser(UUID userId);

    Uni<Void> lockIdempotencyKey(UUID userId, String idempotencyKey);
}
