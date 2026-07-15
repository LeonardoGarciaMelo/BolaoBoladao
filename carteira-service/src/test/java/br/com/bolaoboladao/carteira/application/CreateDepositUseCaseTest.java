package br.com.bolaoboladao.carteira.application;

import br.com.bolaoboladao.carteira.domain.model.Deposit;
import br.com.bolaoboladao.carteira.infrastructure.payment.PaymentProviderClient;
import br.com.bolaoboladao.carteira.infrastructure.payment.ProviderCharge;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateDepositUseCaseTest {
    private final UUID userId = UUID.randomUUID();
    private final UUID depositId = UUID.randomUUID();
    private final UUID chargeId = UUID.randomUUID();
    private DepositTransactions transactions;
    private PaymentProviderClient provider;
    private ProcessDepositUseCase processDeposit;
    private CreateDepositUseCase useCase;

    @BeforeEach
    void setUp() {
        transactions = Mockito.mock(DepositTransactions.class);
        provider = Mockito.mock(PaymentProviderClient.class);
        processDeposit = Mockito.mock(ProcessDepositUseCase.class);
        useCase = new CreateDepositUseCase();
        useCase.transactions = transactions;
        useCase.provider = provider;
        useCase.processDeposit = processDeposit;
        useCase.minimum = 100;
        useCase.maximum = 1_000_000;
        useCase.returnUrl = "http://localhost:8080/carteira";
    }

    @Test
    void persistsCreatingBeforePreparingProviderAndAttachesCharge() {
        Deposit creating = deposit(Deposit.Status.CREATING, null);
        Deposit pending = deposit(Deposit.Status.PENDING, chargeId);
        ProviderCharge charge = charge(ProviderCharge.Status.PENDING);
        when(transactions.prepare(userId, 5000, "deposit-key"))
                .thenReturn(Uni.createFrom().item(new DepositTransactions.PreparedDeposit(creating, true)));
        when(provider.create(depositId, 5000, "http://localhost:8080/carteira?depositId=" + depositId))
                .thenReturn(Uni.createFrom().item(charge));
        when(transactions.attachProvider(depositId, charge)).thenReturn(Uni.createFrom().item(pending));

        CreateDepositUseCase.Result result = useCase.execute(userId, 5000, "deposit-key")
                .await().indefinitely();

        assertEquals(true, result.created());
        assertEquals(Deposit.Status.PENDING, result.deposit().status());
        verify(processDeposit, never()).execute(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void recoverableProviderFailureKeepsSameExternalReferenceForRetry() {
        Deposit creating = deposit(Deposit.Status.CREATING, null);
        when(transactions.prepare(userId, 5000, "deposit-key"))
                .thenReturn(Uni.createFrom().item(new DepositTransactions.PreparedDeposit(creating, true)));
        when(provider.create(Mockito.eq(depositId), Mockito.eq(5000L), Mockito.anyString()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("timeout")));

        ApiException unavailable = assertThrows(ApiException.class,
                () -> useCase.execute(userId, 5000, "deposit-key").await().indefinitely());
        assertEquals(503, unavailable.status());

        ProviderCharge charge = charge(ProviderCharge.Status.PENDING);
        Deposit pending = deposit(Deposit.Status.PENDING, chargeId);
        when(provider.create(Mockito.eq(depositId), Mockito.eq(5000L), Mockito.anyString()))
                .thenReturn(Uni.createFrom().item(charge));
        when(transactions.attachProvider(depositId, charge)).thenReturn(Uni.createFrom().item(pending));

        Deposit retried = useCase.retry(creating).await().indefinitely();
        assertEquals(chargeId, retried.providerChargeId());
        verify(provider, Mockito.times(2)).create(Mockito.eq(depositId), Mockito.eq(5000L), Mockito.anyString());
    }

    @Test
    void validatesIdempotencyKeyAndAmountBoundsBeforePersistence() {
        assertEquals(400, assertThrows(ApiException.class,
                () -> useCase.execute(userId, 99, "key")).status());
        assertEquals(400, assertThrows(ApiException.class,
                () -> useCase.execute(userId, 100, " ")).status());
        verify(transactions, never()).prepare(Mockito.any(), Mockito.anyLong(), Mockito.anyString());
    }

    private Deposit deposit(Deposit.Status status, UUID providerChargeId) {
        Instant now = Instant.now();
        return new Deposit(depositId, userId, 5000, status, "deposit-key", providerChargeId,
                providerChargeId == null ? null : "checkout", now.plusSeconds(900), now, now, null);
    }

    private ProviderCharge charge(ProviderCharge.Status status) {
        Instant now = Instant.now();
        return new ProviderCharge(chargeId, depositId, 5000, status, "checkout", now.plusSeconds(900), now);
    }
}
