package br.com.bolaoboladao.carteira.presentation.rest.handler;

import br.com.bolaoboladao.carteira.domain.exception.WalletNotFoundException;
import br.com.bolaoboladao.carteira.presentation.rest.dto.ErrorResponse;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
public class WalletNotFoundExceptionHandler implements ExceptionMapper<WalletNotFoundException> {

    private static final Logger LOG = Logger.getLogger(WalletNotFoundExceptionHandler.class);

    @Override
    @Produces(MediaType.APPLICATION_JSON)
    public Response toResponse(WalletNotFoundException exception) {
        LOG.warnf("Wallet not found: %s", exception.getMessage());
        return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse(exception.getMessage()))
                .build();
    }
}
