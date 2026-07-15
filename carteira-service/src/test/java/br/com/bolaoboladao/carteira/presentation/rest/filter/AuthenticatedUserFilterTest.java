package br.com.bolaoboladao.carteira.presentation.rest.filter;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticatedUserFilterTest {
    private static final String USER_HEADER = "X-Authenticated-User-Id";
    private static final String USER_PROPERTY = "authenticatedUserId";

    private AuthenticatedUserFilter filter;
    private ContainerRequestContext request;
    private ResourceInfo resourceInfo;

    @BeforeEach
    void setUp() {
        filter = new AuthenticatedUserFilter();
        request = mock(ContainerRequestContext.class);
        resourceInfo = mock(ResourceInfo.class);
        filter.resourceInfo = resourceInfo;
    }

    @Test
    void webhookAuthenticatedResourceDoesNotRequireGatewayUserHeader() {
        doReturn(WebhookResource.class).when(resourceInfo).getResourceClass();

        assertDoesNotThrow(() -> filter.filter(request));
    }

    @Test
    void regularResourceStillRequiresGatewayUserHeader() {
        doReturn(RegularResource.class).when(resourceInfo).getResourceClass();

        assertThrows(ForbiddenException.class, () -> filter.filter(request));
    }

    @Test
    void regularResourceStoresValidGatewayUserIdentity() {
        UUID userId = UUID.randomUUID();
        doReturn(RegularResource.class).when(resourceInfo).getResourceClass();
        when(request.getHeaderString(USER_HEADER)).thenReturn(userId.toString());

        filter.filter(request);

        verify(request).setProperty(USER_PROPERTY, userId);
    }

    @WebhookAuthenticated
    private static class WebhookResource {
    }

    private static class RegularResource {
    }
}
