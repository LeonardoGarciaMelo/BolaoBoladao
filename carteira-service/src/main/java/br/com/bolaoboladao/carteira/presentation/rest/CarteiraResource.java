package br.com.bolaoboladao.carteira.presentation.rest;

import br.com.bolaoboladao.carteira.application.GetWalletBalanceUseCase;
import br.com.bolaoboladao.carteira.application.GetWalletStatementUseCase;
import br.com.bolaoboladao.carteira.application.CreateWalletUseCase;
import br.com.bolaoboladao.carteira.presentation.rest.dto.BalanceResponse;
import br.com.bolaoboladao.carteira.presentation.rest.dto.LedgerEntryResponse;
import br.com.bolaoboladao.carteira.presentation.rest.dto.PageResponse;
import br.com.bolaoboladao.carteira.presentation.rest.dto.UserLedgerEntryResponse;
import br.com.bolaoboladao.carteira.presentation.rest.dto.UserWalletResponse;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.Timeout;

import java.util.UUID;
import java.math.RoundingMode;

@Path("/wallet")
@Produces(MediaType.APPLICATION_JSON)
public class CarteiraResource {

    private final GetWalletBalanceUseCase getWalletBalanceUseCase;
    private final GetWalletStatementUseCase getWalletStatementUseCase;
    private final CreateWalletUseCase createWalletUseCase;

    @Inject
    public CarteiraResource(GetWalletBalanceUseCase getWalletBalanceUseCase,
                            GetWalletStatementUseCase getWalletStatementUseCase,
                            CreateWalletUseCase createWalletUseCase) {
        this.getWalletBalanceUseCase = getWalletBalanceUseCase;
        this.getWalletStatementUseCase = getWalletStatementUseCase;
        this.createWalletUseCase = createWalletUseCase;
    }

    @GET
    @Path("/me")
    @Timeout(3000)
    public Uni<UserWalletResponse> getMe(@Context ContainerRequestContext requestContext) {
        UUID userId = authenticatedUser(requestContext);
        return createWalletUseCase.execute(userId)
                .flatMap(wallet -> getWalletBalanceUseCase.execute(userId)
                        .map(balance -> new UserWalletResponse(userId, wallet.id(),
                                balance.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact())));
    }

    @GET
    @Path("/me/statement")
    @Timeout(3000)
    public Uni<PageResponse<UserLedgerEntryResponse>> getMyStatement(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("10") int size,
            @Context ContainerRequestContext requestContext) {
        UUID userId = authenticatedUser(requestContext);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(50, size));
        return getWalletStatementUseCase.executeForUser(userId, safePage, safeSize)
                .map(result -> new PageResponse<>(result.items().stream().map(UserLedgerEntryResponse::from).toList(),
                        result.page(), result.size(), result.total()));
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

    private UUID authenticatedUser(ContainerRequestContext requestContext) {
        UUID userId = (UUID) requestContext.getProperty("authenticatedUserId");
        if (userId == null) {
            throw new ForbiddenException();
        }
        return userId;
    }

}
