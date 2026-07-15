package br.com.bolaoboladao.carteira.presentation.rest;

import br.com.bolaoboladao.carteira.application.ApiException;
import br.com.bolaoboladao.carteira.presentation.rest.dto.ErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<ApiException> {
    @Override
    public Response toResponse(ApiException exception) {
        return Response.status(exception.status())
                .entity(new ErrorResponse(exception.code(), exception.getMessage()))
                .build();
    }
}
