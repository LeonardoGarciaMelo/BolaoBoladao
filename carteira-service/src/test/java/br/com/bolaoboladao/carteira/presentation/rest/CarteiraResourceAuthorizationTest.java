package br.com.bolaoboladao.carteira.presentation.rest;

import br.com.bolaoboladao.carteira.application.GetWalletBalanceUseCase;
import br.com.bolaoboladao.carteira.application.GetWalletStatementUseCase;
import br.com.bolaoboladao.carteira.application.CreateWalletUseCase;
import br.com.bolaoboladao.carteira.domain.model.Ledger;
import br.com.bolaoboladao.carteira.domain.model.Wallet;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class CarteiraResourceAuthorizationTest {
    private static final UUID AUTHENTICATED_USER_ID = UUID.fromString("22121193-3c26-4c26-812d-123456789012");
    private static final UUID WALLET_ID = UUID.fromString("33121193-3c26-4c26-812d-123456789012");

    private CarteiraResource resource;
    private ContainerRequestContext requestContext;
    private CreateWalletUseCase createWalletUseCase;
    private GetWalletBalanceUseCase getWalletBalanceUseCase;
    private GetWalletStatementUseCase getWalletStatementUseCase;

    @BeforeEach
    void setUp() {
        getWalletBalanceUseCase = Mockito.mock(GetWalletBalanceUseCase.class);
        getWalletStatementUseCase = Mockito.mock(GetWalletStatementUseCase.class);
        createWalletUseCase = Mockito.mock(CreateWalletUseCase.class);

        resource = new CarteiraResource(getWalletBalanceUseCase, getWalletStatementUseCase, createWalletUseCase);

        requestContext = Mockito.mock(ContainerRequestContext.class);
        when(requestContext.getProperty("authenticatedUserId")).thenReturn(AUTHENTICATED_USER_ID);
    }

    @Test
    void deveRecusarConsultaDeSaldoDeOutroUsuario() {
        UUID anotherUserId = UUID.fromString("44121193-3c26-4c26-812d-123456789012");

        assertThrows(ForbiddenException.class,
                () -> resource.getBalance(anotherUserId, requestContext));
    }

    @Test
    void deveRecusarIdentidadeAusenteOuInvalida() {
        ContainerRequestContext invalidContext = Mockito.mock(ContainerRequestContext.class);
        when(invalidContext.getProperty("authenticatedUserId")).thenReturn(null);

        assertThrows(ForbiddenException.class, () -> resource.getBalance(AUTHENTICATED_USER_ID, invalidContext));
        assertThrows(ForbiddenException.class, () -> resource.getStatement(WALLET_ID, 0, 10, invalidContext));
        assertThrows(ForbiddenException.class, () -> resource.getMe(invalidContext));
        assertThrows(ForbiddenException.class, () -> resource.getMyStatement(0, 10, invalidContext));
    }

    @Test
    void deveRetornarCarteiraDoUsuarioComSaldoEmCentavos() {
        when(createWalletUseCase.execute(AUTHENTICATED_USER_ID))
                .thenReturn(Uni.createFrom().item(new Wallet(WALLET_ID, AUTHENTICATED_USER_ID)));
        when(getWalletBalanceUseCase.execute(AUTHENTICATED_USER_ID))
                .thenReturn(Uni.createFrom().item(new BigDecimal("12.34")));

        var response = resource.getMe(requestContext).await().indefinitely();

        org.junit.jupiter.api.Assertions.assertEquals(AUTHENTICATED_USER_ID, response.userId());
        org.junit.jupiter.api.Assertions.assertEquals(WALLET_ID, response.walletId());
        org.junit.jupiter.api.Assertions.assertEquals(1234L, response.balanceCents());
    }

    @Test
    void devePaginarExtratoDoUsuarioEConverterValorParaCentavos() {
        Ledger entry = new Ledger(UUID.randomUUID(), WALLET_ID, Ledger.Reason.ADMIN_CREDIT,
                Ledger.Operation.CREDIT, new BigDecimal("5.25"), LocalDateTime.now(), UUID.randomUUID(),
                AUTHENTICATED_USER_ID, "Crédito de teste", "statement-key", BigDecimal.ZERO,
                new BigDecimal("5.25"));
        when(getWalletStatementUseCase.executeForUser(AUTHENTICATED_USER_ID, 0, 50))
                .thenReturn(Uni.createFrom().item(new GetWalletStatementUseCase.StatementPage(
                        List.of(entry), 0, 50, 1)));

        var response = resource.getMyStatement(-1, 500, requestContext).await().indefinitely();

        org.junit.jupiter.api.Assertions.assertEquals(0, response.page());
        org.junit.jupiter.api.Assertions.assertEquals(50, response.size());
        org.junit.jupiter.api.Assertions.assertEquals(1, response.total());
        org.junit.jupiter.api.Assertions.assertEquals(525L, response.items().getFirst().amountCents());
        org.junit.jupiter.api.Assertions.assertEquals("ADMIN_CREDIT", response.items().getFirst().reason());
    }
}
