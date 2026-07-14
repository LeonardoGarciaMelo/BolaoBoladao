package br.com.bolaoboladao.partidas.resource;

import br.com.bolaoboladao.partidas.service.InvalidMatchStateException;
import br.com.bolaoboladao.partidas.service.MatchNotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.time.Instant;
import java.util.Map;

@Provider
public class DomainExceptionMapper implements ExceptionMapper<RuntimeException> {

    @Override
    public Response toResponse(RuntimeException exception) {
        if (exception instanceof jakarta.ws.rs.WebApplicationException wae) {
            return wae.getResponse();
        }
        if (exception instanceof MatchNotFoundException) {
            return build(Response.Status.NOT_FOUND, exception.getMessage());
        }
        if (exception instanceof InvalidMatchStateException) {
            return build(Response.Status.CONFLICT, exception.getMessage());
        }
        if (exception instanceof jakarta.validation.ConstraintViolationException cve) {
            return build(Response.Status.BAD_REQUEST, cve.getMessage());
        }
        // Fallback: não vaza stacktrace pro cliente
        exception.printStackTrace();
        return build(Response.Status.INTERNAL_SERVER_ERROR, "Erro interno inesperado");
    }

    private Response build(Response.Status status, String message) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of(
                        "timestamp", Instant.now().toString(),
                        "status", status.getStatusCode(),
                        "error", status.getReasonPhrase(),
                        "message", message
                ))
                .build();
    }
}
