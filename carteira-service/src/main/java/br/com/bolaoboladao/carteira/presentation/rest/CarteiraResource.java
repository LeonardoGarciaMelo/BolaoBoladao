package br.com.bolaoboladao.carteira.presentation.rest;

import br.com.bolaoboladao.carteira.application.GetWalletBalanceUseCase;
import br.com.bolaoboladao.carteira.application.GetWalletStatementUseCase;
import br.com.bolaoboladao.carteira.presentation.rest.dto.BalanceResponse;
import br.com.bolaoboladao.carteira.presentation.rest.dto.LedgerEntryResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

@Path("/wallet")
@Produces(MediaType.APPLICATION_JSON)
public class CarteiraResource {

    @Inject
    GetWalletBalanceUseCase getWalletBalanceUseCase;

    @Inject
    GetWalletStatementUseCase getWalletStatementUseCase;

    @GET
    @Path("/{userId}/balance")
    public Response getBalance(@PathParam("userId") UUID userId) {
        var balance = getWalletBalanceUseCase.execute(userId);
        return Response.ok(new BalanceResponse(balance)).build();
    }

    @GET
    @Path("/{walletId}/statement")
    public Response getStatement(
            @PathParam("walletId") UUID walletId,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("10") int size) {

        var statement = getWalletStatementUseCase.execute(walletId, page, size)
                .stream()
                .map(LedgerEntryResponse::from)
                .toList();
        return Response.ok(statement).build();
    }
}
