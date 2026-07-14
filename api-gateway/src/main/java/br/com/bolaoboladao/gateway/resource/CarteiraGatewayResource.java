package br.com.bolaoboladao.gateway.resource;

import br.com.bolaoboladao.gateway.client.BackendClient;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/api/wallet")
@Authenticated
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class CarteiraGatewayResource {

    @Inject
    BackendClient backendClient;

    @Inject
    JsonWebToken token;

    @ConfigProperty(name = "carteira-service.url")
    String carteiraServiceUrl;

    @GET
    public Uni<Response> root(@Context UriInfo uriInfo) {
        return forward("", uriInfo);
    }

    @GET
    @Path("/{path: .*}")
    public Uni<Response> get(@PathParam("path") String path, @Context UriInfo uriInfo) {
        return forward(path, uriInfo);
    }

    private Uni<Response> forward(String path, UriInfo uriInfo) {
        return backendClient.get(target(path, uriInfo), authenticatedUserId())
                .onItem().transform(GatewayResponses::from);
    }

    private String target(String path, UriInfo uriInfo) {
        StringBuilder target = new StringBuilder(carteiraServiceUrl).append("/wallet");
        if (!path.isBlank()) {
            target.append('/').append(path);
        }
        String query = uriInfo.getRequestUri().getRawQuery();
        if (query != null && !query.isBlank()) {
            target.append('?').append(query);
        }
        return target.toString();
    }

    private String authenticatedUserId() {
        String subject = token.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new NotAuthorizedException("Bearer");
        }
        return subject;
    }
}
