package br.com.bolaoboladao.gateway.resource;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

final class GatewayResponses {
    private GatewayResponses() {
    }

    static Response from(HttpResponse<Buffer> response) {
        String contentType = response.getHeader("Content-Type");
        Response.ResponseBuilder builder = Response.status(response.statusCode())
                .type(contentType == null ? MediaType.APPLICATION_JSON : contentType);

        if (response.body() != null) {
            builder.entity(response.body().getBytes());
        }

        return builder.build();
    }
}
