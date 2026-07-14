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
import io.smallrye.mutiny.Uni;
import java.util.UUID;
import org.eclipse.microprofile.faulttolerance.Timeout;

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
    @Timeout(3000)
    public Uni<Response> getBalance(
            @PathParam("userId") UUID userId,
            @HeaderParam("X-Authenticated-User-Id") String authenticatedUserIdHeader) {
        UUID authenticatedUserId = authenticatedUserId(authenticatedUserIdHeader);
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
            @HeaderParam("X-Authenticated-User-Id") String authenticatedUserIdHeader) {

        UUID authenticatedUserId = authenticatedUserId(authenticatedUserIdHeader);
        return walletRepository.findByUserId(authenticatedUserId)
                .flatMap(wallet -> {
                    boolean ownsWallet = wallet != null && wallet.id().equals(walletId);
                    if (!ownsWallet) {
                        return Uni.createFrom().failure(new ForbiddenException());
                    }

                    return getWalletStatementUseCase.execute(walletId, page, size)
                            .map(statement -> statement.stream().map(LedgerEntryResponse::from).toList())
                            .map(statementResponses -> Response.ok(statementResponses).build());
                });
    }

    private UUID authenticatedUserId(String header) {
        try {
            return UUID.fromString(header);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ForbiddenException();
        }
    }
}
