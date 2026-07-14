package br.com.bolaoboladao.gateway.resource;

import br.com.bolaoboladao.gateway.client.BackendClient;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/api/admin/users")
@RolesAllowed("ADMIN")
public class AdminUsersGatewayResource {
    @Inject BackendClient backendClient;
    @Inject JsonWebToken token;
    @ConfigProperty(name = "user-service.url") String userServiceUrl;

    @GET
    public Uni<Response> search(@Context UriInfo uriInfo, @Context HttpHeaders headers) {
        String query = uriInfo.getRequestUri().getRawQuery();
        String target = userServiceUrl + "/admin/users" + (query == null ? "" : "?" + query);
        return backendClient.adminGet(target, token.getSubject(), headers.getHeaderString(HttpHeaders.AUTHORIZATION))
                .onItem().transform(GatewayResponses::from);
    }
}
