package br.com.bolaoboladao.carteira.presentation.rest;

import br.com.bolaoboladao.carteira.application.GetWalletBalanceUseCase;
import br.com.bolaoboladao.carteira.application.GetWalletStatementUseCase;
import br.com.bolaoboladao.carteira.presentation.rest.dto.BalanceResponse;
import br.com.bolaoboladao.carteira.presentation.rest.dto.LedgerEntryResponse;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.Timeout;

import java.util.UUID;

@Path("/wallet")
@Produces(MediaType.APPLICATION_JSON)
public class CarteiraResource {

    private final GetWalletBalanceUseCase getWalletBalanceUseCase;
    private final GetWalletStatementUseCase getWalletStatementUseCase;

    @Inject
    public CarteiraResource(GetWalletBalanceUseCase getWalletBalanceUseCase,
                            GetWalletStatementUseCase getWalletStatementUseCase) {
        this.getWalletBalanceUseCase = getWalletBalanceUseCase;
        this.getWalletStatementUseCase = getWalletStatementUseCase;
    }

    @GET
    @Path("/{userId}/balance")
    @Timeout(3000)
    public Uni<Response> getBalance(
            @PathParam("userId") UUID userId,
            @Context ContainerRequestContext requestContext) {

        UUID authenticatedUserId = (UUID) requestContext.getProperty("authenticatedUserId");
        if (!userId.equals(authenticatedUserId)) {
            throw new ForbiddenException();
        }
        return getWalletBalanceUseCase.execute(userId)
                .map(balance -> Response.ok(new BalanceResponse(balance)).build());
    }

    @GET
    @Path("/{walletId}/statement")
    @Timeout(3000)
    public Uni<Response> getStatement(
            @PathParam("walletId") UUID walletId,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("10") int size,
            @Context ContainerRequestContext requestContext) {

        UUID authenticatedUserId = (UUID) requestContext.getProperty("authenticatedUserId");
        if (authenticatedUserId == null) {
            throw new ForbiddenException();
        }

        return getWalletStatementUseCase.execute(authenticatedUserId, walletId, page, size)
                .map(statement -> statement.stream().map(LedgerEntryResponse::from).toList())
                .map(statementResponses -> Response.ok(statementResponses).build());
    }

}
