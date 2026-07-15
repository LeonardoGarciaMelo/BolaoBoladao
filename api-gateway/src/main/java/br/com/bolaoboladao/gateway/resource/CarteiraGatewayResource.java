package br.com.bolaoboladao.gateway.resource;

import br.com.bolaoboladao.gateway.client.BackendClient;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
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

    @POST
    public Uni<Response> postRoot(String body, @Context UriInfo uriInfo, @Context HttpHeaders headers) {
        return forwardPost("", uriInfo, body, headers.getHeaderString("Idempotency-Key"));
    }

    @POST
    @Path("/{path: .+}")
    public Uni<Response> post(@PathParam("path") String path, String body,
                              @Context UriInfo uriInfo, @Context HttpHeaders headers) {
        return forwardPost(path, uriInfo, body, headers.getHeaderString("Idempotency-Key"));
    }

    private Uni<Response> forward(String path, UriInfo uriInfo) {
        return backendClient.get(target(path, uriInfo), authenticatedUserId())
                .onItem().transform(GatewayResponses::from);
    }

    private Uni<Response> forwardPost(String path, UriInfo uriInfo, String body, String idempotencyKey) {
        return backendClient.post(target(path, uriInfo), authenticatedUserId(), idempotencyKey, body)
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
