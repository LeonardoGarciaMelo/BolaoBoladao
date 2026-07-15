package br.com.bolaoboladao.carteira.presentation.rest.filter;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;

import java.util.UUID;

@Provider
public class AuthenticatedUserFilter implements ContainerRequestFilter {

    private static final String AUTH_HEADER = "X-Authenticated-User-Id";
    private static final String AUTH_PROPERTY = "authenticatedUserId";

    @Context
    ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (usesWebhookAuthentication()) {
            return;
        }
        String header = requestContext.getHeaderString(AUTH_HEADER);
        if (header != null) {
            try {
                UUID userId = UUID.fromString(header);
                requestContext.setProperty(AUTH_PROPERTY, userId);
            } catch (IllegalArgumentException e) {
                throw new ForbiddenException("Invalid User ID format in header.");
            }
        } else {
            throw new ForbiddenException("Missing User ID in header.");
        }
    }

    private boolean usesWebhookAuthentication() {
        if (resourceInfo == null) return false;
        return resourceInfo.getResourceClass() != null
                && resourceInfo.getResourceClass().isAnnotationPresent(WebhookAuthenticated.class);
    }
}
