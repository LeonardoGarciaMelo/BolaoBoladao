package br.com.bolaoboladao.gateway.resource;

import br.com.bolaoboladao.gateway.client.BackendClient;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/api/admin/wallets/users")
@RolesAllowed("ADMIN")
public class AdminWalletsGatewayResource {
    @Inject BackendClient backendClient;
    @Inject JsonWebToken token;
    @ConfigProperty(name = "carteira-service.url") String carteiraServiceUrl;

    @GET
    @Path("/{userId}")
    public Uni<Response> wallet(@PathParam("userId") String userId, @Context HttpHeaders headers) {
        return backendClient.adminGet(carteiraServiceUrl + "/admin/wallets/users/" + userId,
                        token.getSubject(), headers.getHeaderString(HttpHeaders.AUTHORIZATION))
                .onItem().transform(GatewayResponses::from);
    }

    @POST
    @Path("/{userId}/credits")
    public Uni<Response> credit(@PathParam("userId") String userId, String body,
                                @HeaderParam("Idempotency-Key") String idempotencyKey,
                                @Context HttpHeaders headers) {
        return backendClient.adminPost(carteiraServiceUrl + "/admin/wallets/users/" + userId + "/credits",
                        token.getSubject(), headers.getHeaderString(HttpHeaders.AUTHORIZATION), idempotencyKey, body)
                .onItem().transform(GatewayResponses::from);
    }
}
