package br.com.bolaoboladao.carteira.application;

import br.com.bolaoboladao.carteira.application.repository.ProcessedEventRepository;
import br.com.bolaoboladao.carteira.domain.model.Deposit;
import br.com.bolaoboladao.carteira.domain.model.Ledger;
import br.com.bolaoboladao.carteira.domain.model.Wallet;
import br.com.bolaoboladao.carteira.domain.repository.DepositRepository;
import br.com.bolaoboladao.carteira.domain.repository.LedgerRepository;
import br.com.bolaoboladao.carteira.domain.repository.WalletRepository;
import br.com.bolaoboladao.carteira.domain.service.WalletCache;
import br.com.bolaoboladao.carteira.infrastructure.payment.ProviderCharge;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessDepositUseCaseTest {
    private final UUID depositId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID chargeId = UUID.randomUUID();
    private final UUID walletId = UUID.randomUUID();
    private DepositRepository deposits;
    private WalletRepository wallets;
    private LedgerRepository ledger;
    private ProcessedEventRepository processedEvents;
    private GetWalletBalanceUseCase balances;
    private WalletCache cache;
    private ProcessDepositUseCase useCase;

    @BeforeEach
    void setUp() {
        deposits = Mockito.mock(DepositRepository.class);
        wallets = Mockito.mock(WalletRepository.class);
        ledger = Mockito.mock(LedgerRepository.class);
        processedEvents = Mockito.mock(ProcessedEventRepository.class);
        balances = Mockito.mock(GetWalletBalanceUseCase.class);
        cache = Mockito.mock(WalletCache.class);
        useCase = new ProcessDepositUseCase();
        useCase.deposits = deposits;
        useCase.wallets = wallets;
        useCase.ledger = ledger;
        useCase.processedEvents = processedEvents;
        useCase.balances = balances;
        useCase.cache = cache;
    }

    @Test
    void paidWebhookCreatesOneDepositCreditAndInvalidatesCaches() {
        UUID eventId = UUID.randomUUID();
        Deposit pending = deposit(Deposit.Status.PENDING);
        when(deposits.findAndLockById(depositId)).thenReturn(Uni.createFrom().item(pending));
        when(processedEvents.isProcessed(eventId)).thenReturn(Uni.createFrom().item(false));
        when(ledger.lockIdempotencyKey("deposit-credit:" + depositId)).thenReturn(Uni.createFrom().voidItem());
        when(ledger.findByIdempotencyKey("deposit-credit:" + depositId)).thenReturn(Uni.createFrom().nullItem());
        when(wallets.lockUser(userId)).thenReturn(Uni.createFrom().voidItem());
        when(wallets.findAndLockByUserId(userId)).thenReturn(Uni.createFrom().item(new Wallet(walletId, userId)));
        when(balances.calculateBalanceFromDatabase(userId)).thenReturn(Uni.createFrom().item(new BigDecimal("10.00")));
        when(ledger.save(Mockito.any())).thenReturn(Uni.createFrom().voidItem());
        when(deposits.update(Mockito.any())).thenReturn(Uni.createFrom().voidItem());
        when(cache.invalidateBalance(userId)).thenReturn(Uni.createFrom().voidItem());
        when(cache.invalidateStatement(walletId)).thenReturn(Uni.createFrom().voidItem());
        when(processedEvents.markAsProcessed(eventId, "CHARGE_PAID")).thenReturn(Uni.createFrom().voidItem());

        Deposit result = useCase.execute(eventId, "CHARGE_PAID", charge(ProviderCharge.Status.PAID))
                .await().indefinitely();

        assertEquals(Deposit.Status.CONFIRMED, result.status());
        ArgumentCaptor<Ledger> saved = ArgumentCaptor.forClass(Ledger.class);
        verify(ledger).save(saved.capture());
        assertEquals(Ledger.Reason.DEPOSIT, saved.getValue().reason());
        assertEquals(Ledger.Operation.CREDIT, saved.getValue().operation());
        assertEquals(new BigDecimal("50.00"), saved.getValue().amount());
        assertEquals(new BigDecimal("10.00"), saved.getValue().balanceBefore());
        assertEquals(new BigDecimal("60.00"), saved.getValue().balanceAfter());
        verify(cache).invalidateBalance(userId);
        verify(cache).invalidateStatement(walletId);
    }

    @Test
    void duplicateEventDoesNotCreditAgain() {
        UUID eventId = UUID.randomUUID();
        when(deposits.findAndLockById(depositId)).thenReturn(Uni.createFrom().item(deposit(Deposit.Status.CONFIRMED)));
        when(processedEvents.isProcessed(eventId)).thenReturn(Uni.createFrom().item(true));

        Deposit result = useCase.execute(eventId, "CHARGE_PAID", charge(ProviderCharge.Status.PAID))
                .await().indefinitely();

        assertEquals(Deposit.Status.CONFIRMED, result.status());
        verify(ledger, never()).save(Mockito.any());
        verify(cache, never()).invalidateBalance(Mockito.any());
    }

    @Test
    void refusedOrExpiredDepositNeverChangesLedger() {
        UUID eventId = UUID.randomUUID();
        when(deposits.findAndLockById(depositId)).thenReturn(Uni.createFrom().item(deposit(Deposit.Status.PENDING)));
        when(processedEvents.isProcessed(eventId)).thenReturn(Uni.createFrom().item(false));
        when(deposits.update(Mockito.any())).thenReturn(Uni.createFrom().voidItem());
        when(processedEvents.markAsProcessed(eventId, "CHARGE_REFUSED")).thenReturn(Uni.createFrom().voidItem());

        Deposit result = useCase.execute(eventId, "CHARGE_REFUSED", charge(ProviderCharge.Status.REFUSED))
                .await().indefinitely();

        assertEquals(Deposit.Status.REFUSED, result.status());
        verify(ledger, never()).save(Mockito.any());
        verify(cache, never()).invalidateBalance(Mockito.any());
    }

    @Test
    void outOfOrderTerminalResultReturnsConflict() {
        UUID eventId = UUID.randomUUID();
        when(deposits.findAndLockById(depositId)).thenReturn(Uni.createFrom().item(deposit(Deposit.Status.EXPIRED)));
        when(processedEvents.isProcessed(eventId)).thenReturn(Uni.createFrom().item(false));

        ApiException conflict = assertThrows(ApiException.class,
                () -> useCase.execute(eventId, "CHARGE_PAID", charge(ProviderCharge.Status.PAID))
                        .await().indefinitely());

        assertEquals(409, conflict.status());
        verify(ledger, never()).save(Mockito.any());
    }

    private Deposit deposit(Deposit.Status status) {
        Instant now = Instant.now();
        return new Deposit(depositId, userId, 5000, status, "deposit-key", chargeId,
                "checkout", now.plusSeconds(900), now, now,
                status == Deposit.Status.CONFIRMED ? now : null);
    }

    private ProviderCharge charge(ProviderCharge.Status status) {
        return new ProviderCharge(chargeId, depositId, 5000, status, "checkout",
                Instant.now().plusSeconds(900), Instant.now());
    }
}
