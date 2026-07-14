package br.com.bolaoboladao.gateway.resource;

import br.com.bolaoboladao.gateway.client.BackendClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Path("/api/partidas")
@Authenticated
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class PartidasGatewayResource {

    @Inject
    BackendClient backendClient;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "partidas-service.url")
    String partidasServiceUrl;

    @GET
    public Uni<Response> list(@Context UriInfo uriInfo, @Context HttpHeaders headers) {
        return forwardGet("", uriInfo, headers);
    }

    @GET
    @Path("/{path: .*}")
    public Uni<Response> get(@PathParam("path") String path, @Context UriInfo uriInfo, @Context HttpHeaders headers) {
        return forwardGet(path, uriInfo, headers);
    }

    @POST
    public Uni<Response> create(String body, @Context UriInfo uriInfo, @Context HttpHeaders headers) {
        return forwardPost("", uriInfo, headers, body);
    }

    @POST
    @Path("/{path: .*}")
    public Uni<Response> post(@PathParam("path") String path, String body, @Context UriInfo uriInfo, @Context HttpHeaders headers) {
        return forwardPost(path, uriInfo, headers, body);
    }

    private Uni<Response> forwardGet(String path, UriInfo uriInfo, HttpHeaders headers) {
        return backendClient.get(target(path, uriInfo), authenticatedUserId(headers))
                .onItem().transform(GatewayResponses::from);
    }

    private Uni<Response> forwardPost(String path, UriInfo uriInfo, HttpHeaders headers, String body) {
        return backendClient.post(target(path, uriInfo), authenticatedUserId(headers), body)
                .onItem().transform(GatewayResponses::from);
    }

    private String target(String path, UriInfo uriInfo) {
        StringBuilder target = new StringBuilder(partidasServiceUrl).append("/partidas");
        if (!path.isBlank()) {
            target.append('/').append(path);
        }
        String query = uriInfo.getRequestUri().getQuery();
        if (query != null && !query.isBlank()) {
            target.append('?').append(query);
        }
        return target.toString();
    }

    private String authenticatedUserId(HttpHeaders headers) {
        String authorization = headers.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new NotAuthorizedException("Bearer");
        }
        String token = authorization.substring("Bearer ".length());
        try {
            String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
            String subject = objectMapper.readTree(payload).path("sub").asText();
            if (subject.isBlank()) {
                throw new NotAuthorizedException("Bearer");
            }
            return subject;
        } catch (NotAuthorizedException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new NotAuthorizedException("Bearer", exception);
        }
    }
}
