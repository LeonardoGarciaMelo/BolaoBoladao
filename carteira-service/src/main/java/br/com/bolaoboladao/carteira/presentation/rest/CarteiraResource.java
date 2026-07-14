package br.com.bolaoboladao.carteira.presentation.rest;

import br.com.bolaoboladao.carteira.application.GetWalletBalanceUseCase;
import br.com.bolaoboladao.carteira.application.GetWalletStatementUseCase;
import br.com.bolaoboladao.carteira.domain.repository.WalletRepository;
import br.com.bolaoboladao.carteira.presentation.rest.dto.BalanceResponse;
import br.com.bolaoboladao.carteira.presentation.rest.dto.LedgerEntryResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
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

    @Inject
    WalletRepository walletRepository;

    @GET
    @Path("/{userId}/balance")
    public Response getBalance(
            @PathParam("userId") UUID userId,
            @HeaderParam("X-Authenticated-User-Id") String authenticatedUserIdHeader) {
        UUID authenticatedUserId = authenticatedUserId(authenticatedUserIdHeader);
        if (!userId.equals(authenticatedUserId)) {
            throw new ForbiddenException();
        }
        var balance = getWalletBalanceUseCase.execute(userId);
        return Response.ok(new BalanceResponse(balance)).build();
    }

    @GET
    @Path("/{walletId}/statement")
    public Response getStatement(
            @PathParam("walletId") UUID walletId,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("10") int size,
            @HeaderParam("X-Authenticated-User-Id") String authenticatedUserIdHeader) {

        UUID authenticatedUserId = authenticatedUserId(authenticatedUserIdHeader);
        boolean ownsWallet = walletRepository.findByUserId(authenticatedUserId)
                .map(wallet -> wallet.id().equals(walletId))
                .orElse(false);
        if (!ownsWallet) {
            throw new ForbiddenException();
        }

        var statement = getWalletStatementUseCase.execute(walletId, page, size)
                .stream()
                .map(LedgerEntryResponse::from)
                .toList();
        return Response.ok(statement).build();
    }

    private UUID authenticatedUserId(String header) {
        try {
            return UUID.fromString(header);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ForbiddenException();
        }
    }
}
