package br.com.bolaoboladao.gateway.resource;

import br.com.bolaoboladao.gateway.client.BackendClient;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.GET;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import org.eclipse.microprofile.jwt.JsonWebToken;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/api/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthGatewayResource {

    @Inject
    BackendClient backendClient;

    @ConfigProperty(name = "user-service.url")
    String userServiceUrl;

    @Inject
    JsonWebToken token;

    @POST
    @Path("/register")
    public Uni<Response> register(String body) {
        return forward("/auth/register", body);
    }

    @POST
    @Path("/login")
    public Uni<Response> login(String body) {
        return forward("/auth/login", body);
    }

    @GET
    @Path("/me")
    @Authenticated
    public Uni<Response> me(@Context HttpHeaders headers) {
        return backendClient.adminGet(userServiceUrl + "/auth/me", token.getSubject(), authorization(headers))
                .onItem().transform(GatewayResponses::from);
    }

    private String authorization(HttpHeaders headers) {
        return headers.getHeaderString(HttpHeaders.AUTHORIZATION);
    }

    private Uni<Response> forward(String path, String body) {
        return backendClient.publicPost(userServiceUrl + path, body)
                .onItem().transform(GatewayResponses::from);
    }
}
