package br.com.bolaoboladao.carteira.application;

import br.com.bolaoboladao.carteira.domain.model.Ledger;
import br.com.bolaoboladao.carteira.domain.repository.LedgerRepository;
import br.com.bolaoboladao.carteira.domain.repository.WalletRepository;
import br.com.bolaoboladao.carteira.domain.service.WalletCache;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminCreditUseCaseTest {
    private final WalletRepository wallets = mock(WalletRepository.class);
    private final LedgerRepository ledger = mock(LedgerRepository.class);
    private final GetWalletBalanceUseCase balances = mock(GetWalletBalanceUseCase.class);
    private final WalletCache cache = mock(WalletCache.class);
    private AdminCreditUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AdminCreditUseCase(wallets, ledger, balances, cache);
        useCase.maxCents = 1_000_000;
        when(ledger.lockIdempotencyKey(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Uni.createFrom().voidItem());
    }

    @Test
    void deveValidarLimitesEChaveObrigatoria() {
        UUID user = UUID.randomUUID();
        UUID admin = UUID.randomUUID();

        assertThrows(BadRequestException.class,
                () -> useCase.execute(user, admin, 0, "Crédito de ajuste", "key").await().indefinitely());
        assertThrows(BadRequestException.class,
                () -> useCase.execute(user, admin, 1_000_001, "Crédito de ajuste", "key").await().indefinitely());
        assertThrows(BadRequestException.class,
                () -> useCase.execute(user, admin, 100, "Crédito de ajuste", " ").await().indefinitely());
        assertThrows(BadRequestException.class,
                () -> useCase.execute(user, admin, 100, "          x", "key").await().indefinitely());
    }

    @Test
    void deveReutilizarRespostaIdenticaEConflitarComDadosDiferentes() {
        UUID user = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        Ledger existing = new Ledger(UUID.randomUUID(), UUID.randomUUID(), Ledger.Reason.ADMIN_CREDIT,
                Ledger.Operation.CREDIT, new BigDecimal("25.00"), LocalDateTime.now(), user, admin,
                "Crédito administrativo", "idem-1", BigDecimal.ZERO, new BigDecimal("25.00"));
        when(ledger.findByIdempotencyKey("idem-1")).thenReturn(Uni.createFrom().item(existing));

        var replay = useCase.execute(user, admin, 2500, "Crédito administrativo", "idem-1")
                .await().indefinitely();
        assertEquals(existing.id(), replay.ledgerEntryId());
        assertEquals(2500, replay.amountCents());

        ClientErrorException conflict = assertThrows(ClientErrorException.class,
                () -> useCase.execute(user, admin, 2600, "Crédito administrativo", "idem-1")
                        .await().indefinitely());
        assertEquals(409, conflict.getResponse().getStatus());
    }
}
