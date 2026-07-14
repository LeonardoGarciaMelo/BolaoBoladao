package br.com.bolaoboladao.gateway.resource;

import br.com.bolaoboladao.gateway.client.BackendClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/api/admin/partidas")
@RolesAllowed("ADMIN")
public class AdminPartidasGatewayResource {
    @Inject BackendClient backendClient;
    @Inject ObjectMapper objectMapper;
    @Inject JsonWebToken token;
    @ConfigProperty(name = "partidas-service.url") String partidasServiceUrl;
    @ConfigProperty(name = "apostas-service.url") String apostasServiceUrl;

    @GET
    public Uni<Response> list(@Context UriInfo uriInfo, @Context HttpHeaders headers) {
        return adminGet(partidasServiceUrl + "/admin/partidas" + query(uriInfo), headers);
    }

    @POST
    public Uni<Response> create(String body, @Context HttpHeaders headers,
                                @HeaderParam("Idempotency-Key") String idempotencyKey) {
        return adminPost(partidasServiceUrl + "/admin/partidas", headers, idempotencyKey, body);
    }

    @POST
    @Path("/{id}/cancel")
    public Uni<Response> cancel(@PathParam("id") String id, String body, @Context HttpHeaders headers,
                                @HeaderParam("Idempotency-Key") String idempotencyKey) {
        return backendClient.adminPost(partidasServiceUrl + "/admin/partidas/" + id + "/cancel",
                        token.getSubject(), headers.getHeaderString(HttpHeaders.AUTHORIZATION), idempotencyKey, body)
                .onItem().transform(response -> {
                    if (response.statusCode() >= 400) return GatewayResponses.from(response);
                    try {
                        ObjectNode result = objectMapper.createObjectNode();
                        result.set("cancellation", objectMapper.readTree(response.bodyAsString()));
                        ObjectNode refunds = result.putObject("refunds");
                        refunds.put("matchId", id);
                        refunds.put("status", "PENDING");
                        refunds.put("total", 0);
                        refunds.put("pending", 0);
                        refunds.put("completed", 0);
                        refunds.put("failed", 0);
                        return Response.accepted(result).build();
                    } catch (Exception exception) {
                        return Response.serverError().entity("{\"message\":\"Falha ao compor cancelamento\"}").build();
                    }
                });
    }

    @GET
    @Path("/{id}/refunds")
    public Uni<Response> refunds(@PathParam("id") String id, @Context HttpHeaders headers) {
        return adminGet(apostasServiceUrl + "/admin/matches/" + id + "/refunds", headers);
    }

    @POST
    @Path("/{id}/refunds/retry")
    public Uni<Response> retryRefunds(@PathParam("id") String id, @Context HttpHeaders headers) {
        return adminPost(apostasServiceUrl + "/admin/matches/" + id + "/refunds/retry",
                headers, null, "{}");
    }

    private Uni<Response> adminGet(String target, HttpHeaders headers) {
        return backendClient.adminGet(target, token.getSubject(), headers.getHeaderString(HttpHeaders.AUTHORIZATION))
                .onItem().transform(GatewayResponses::from);
    }

    private Uni<Response> adminPost(String target, HttpHeaders headers, String idempotencyKey, String body) {
        return backendClient.adminPost(target, token.getSubject(), headers.getHeaderString(HttpHeaders.AUTHORIZATION), idempotencyKey, body)
                .onItem().transform(GatewayResponses::from);
    }

    private String query(UriInfo uriInfo) {
        String query = uriInfo.getRequestUri().getRawQuery();
        return query == null ? "" : "?" + query;
    }
}
