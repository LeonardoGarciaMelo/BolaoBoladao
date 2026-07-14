package br.com.bolaoboladao.gateway.resource;

import br.com.bolaoboladao.gateway.client.BackendClient;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
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

    private Uni<Response> forward(String path, String body) {
        return backendClient.publicPost(userServiceUrl + path, body)
                .onItem().transform(GatewayResponses::from);
    }
}
