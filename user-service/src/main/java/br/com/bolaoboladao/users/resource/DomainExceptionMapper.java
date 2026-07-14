package br.com.bolaoboladao.users.resource;

import br.com.bolaoboladao.users.service.DuplicateUsernameException;
import br.com.bolaoboladao.users.service.InvalidCredentialsException;
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
        if (exception instanceof DuplicateUsernameException) {
            return build(Response.Status.CONFLICT, exception.getMessage());
        }
        if (exception instanceof InvalidCredentialsException) {
            return build(Response.Status.UNAUTHORIZED, exception.getMessage());
        }
        if (exception instanceof jakarta.validation.ConstraintViolationException validationException) {
            return build(Response.Status.BAD_REQUEST, validationException.getMessage());
        }
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
