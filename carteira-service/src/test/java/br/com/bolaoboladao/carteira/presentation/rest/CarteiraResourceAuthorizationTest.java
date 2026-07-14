package br.com.bolaoboladao.carteira.presentation.rest;

import br.com.bolaoboladao.carteira.application.GetWalletBalanceUseCase;
import br.com.bolaoboladao.carteira.application.GetWalletStatementUseCase;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class CarteiraResourceAuthorizationTest {
    private static final UUID AUTHENTICATED_USER_ID = UUID.fromString("22121193-3c26-4c26-812d-123456789012");
    private static final UUID WALLET_ID = UUID.fromString("33121193-3c26-4c26-812d-123456789012");

    private CarteiraResource resource;
    private ContainerRequestContext requestContext;

    @BeforeEach
    void setUp() {
        GetWalletBalanceUseCase getWalletBalanceUseCase = Mockito.mock(GetWalletBalanceUseCase.class);
        GetWalletStatementUseCase getWalletStatementUseCase = Mockito.mock(GetWalletStatementUseCase.class);
        
        resource = new CarteiraResource(getWalletBalanceUseCase, getWalletStatementUseCase);
        
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
    }
}
