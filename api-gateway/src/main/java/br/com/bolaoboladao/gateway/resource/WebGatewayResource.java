package br.com.bolaoboladao.gateway.resource;

import br.com.bolaoboladao.gateway.client.BackendClient;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/")
public class WebGatewayResource {

    @Inject
    BackendClient backendClient;

    @ConfigProperty(name = "web-service.url")
    String webServiceUrl;

    @GET
    public Uni<Response> index(@Context UriInfo uriInfo) {
        return proxy("", uriInfo);
    }

    @GET
    @Path("{path: .*}")
    public Uni<Response> asset(@PathParam("path") String path, @Context UriInfo uriInfo) {
        return proxy(path, uriInfo);
    }

    private Uni<Response> proxy(String path, UriInfo uriInfo) {
        StringBuilder target = new StringBuilder(webServiceUrl).append('/');
        if (!path.isBlank()) {
            target.append(path);
        }
        String query = uriInfo.getRequestUri().getRawQuery();
        if (query != null) {
            target.append('?').append(query);
        }

        return backendClient.publicGet(target.toString())
                .onItem().transform(GatewayResponses::from);
    }
}
