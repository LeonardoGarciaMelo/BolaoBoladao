package br.com.bolaoboladao.carteira.application;

import br.com.bolaoboladao.carteira.domain.model.Deposit;
import br.com.bolaoboladao.carteira.domain.repository.DepositRepository;
import br.com.bolaoboladao.carteira.infrastructure.payment.ProviderCharge;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DepositTransactionsTest {
    @Test
    void createsAndReplaysByUserAndRejectsAnotherAmount() {
        UUID userId = UUID.randomUUID();
        DepositRepository repository = Mockito.mock(DepositRepository.class);
        DepositTransactions transactions = new DepositTransactions();
        transactions.deposits = repository;

        when(repository.lockIdempotencyKey(userId, "deposit-key")).thenReturn(Uni.createFrom().voidItem());
        when(repository.findByUserAndIdempotencyKey(userId, "deposit-key"))
                .thenReturn(Uni.createFrom().nullItem());
        when(repository.save(Mockito.any())).thenReturn(Uni.createFrom().voidItem());

        var created = transactions.prepare(userId, 5000, "deposit-key").await().indefinitely();
        assertEquals(true, created.created());
        ArgumentCaptor<Deposit> saved = ArgumentCaptor.forClass(Deposit.class);
        verify(repository).save(saved.capture());
        assertEquals(userId, saved.getValue().userId());
        assertEquals(Deposit.Status.CREATING, saved.getValue().status());

        when(repository.findByUserAndIdempotencyKey(userId, "deposit-key"))
                .thenReturn(Uni.createFrom().item(saved.getValue()));
        var replay = transactions.prepare(userId, 5000, "deposit-key").await().indefinitely();
        assertEquals(false, replay.created());
        assertEquals(saved.getValue().id(), replay.deposit().id());

        ApiException conflict = assertThrows(ApiException.class,
                () -> transactions.prepare(userId, 5001, "deposit-key").await().indefinitely());
        assertEquals(409, conflict.status());
    }

    @Test
    void hidesDepositsOwnedByAnotherUser() {
        UUID userId = UUID.randomUUID();
        UUID depositId = UUID.randomUUID();
        DepositRepository repository = Mockito.mock(DepositRepository.class);
        DepositTransactions transactions = new DepositTransactions();
        transactions.deposits = repository;
        when(repository.findByIdAndUser(depositId, userId)).thenReturn(Uni.createFrom().nullItem());

        ApiException notFound = assertThrows(ApiException.class,
                () -> transactions.getOwned(userId, depositId).await().indefinitely());
        assertEquals(404, notFound.status());
    }

    @Test
    void validatesProviderReferenceAmountAndChargeIdentity() {
        UUID depositId = UUID.randomUUID();
        UUID chargeId = UUID.randomUUID();
        Deposit creating = deposit(depositId, null);
        ProviderCharge correct = charge(chargeId, depositId, 5000);

        assertDoesNotThrow(() -> DepositTransactions.validateProvider(creating, correct));
        assertEquals(409, assertThrows(ApiException.class,
                () -> DepositTransactions.validateProvider(creating, charge(chargeId, UUID.randomUUID(), 5000))).status());
        assertEquals(409, assertThrows(ApiException.class,
                () -> DepositTransactions.validateProvider(creating, charge(chargeId, depositId, 5001))).status());
        assertEquals(409, assertThrows(ApiException.class,
                () -> DepositTransactions.validateProvider(deposit(depositId, UUID.randomUUID()), correct)).status());
    }

    private Deposit deposit(UUID id, UUID chargeId) {
        Instant now = Instant.now();
        return new Deposit(id, UUID.randomUUID(), 5000, Deposit.Status.CREATING, "key", chargeId,
                null, null, now, now, null);
    }

    private ProviderCharge charge(UUID chargeId, UUID reference, long amount) {
        return new ProviderCharge(chargeId, reference, amount, ProviderCharge.Status.PENDING,
                "checkout", Instant.now(), Instant.now());
    }
}
