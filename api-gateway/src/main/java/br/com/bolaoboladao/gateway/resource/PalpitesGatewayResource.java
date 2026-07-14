package br.com.bolaoboladao.gateway.resource;

import br.com.bolaoboladao.gateway.client.BackendClient;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/api/palpites")
@Produces(MediaType.APPLICATION_JSON)
public class PalpitesGatewayResource {

    @Inject
    BackendClient backendClient;

    @ConfigProperty(name = "palpites-service.url", defaultValue = "disabled")
    String palpitesServiceUrl;

    @GET
    @Path("/destaques")
    public Uni<Response> destaques() {
        if ("disabled".equals(palpitesServiceUrl)) {
            return Uni.createFrom().item(Response.noContent().build());
        }

        // TODO(palpites-service): encaminhar o contrato público abaixo, sem dados do visitante:
        // { "highlights": [{ "matchId": "uuid", "teamHome": "Aurora", "teamAway": "Estrela",
        // "start": "2026-07-13T21:30:00Z", "status": "LIVE", "totalStakeCents": 2500 }] }
        return backendClient.publicGet(palpitesServiceUrl + "/palpites/destaques")
                .onItem().transform(GatewayResponses::from);
    }
}
