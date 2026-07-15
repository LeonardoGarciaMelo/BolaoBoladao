package br.com.bolaoboladao.carteira.presentation.rest;

import br.com.bolaoboladao.carteira.application.CreateDepositUseCase;
import br.com.bolaoboladao.carteira.application.DepositTransactions;
import br.com.bolaoboladao.carteira.application.ReconcileDepositUseCase;
import br.com.bolaoboladao.carteira.presentation.rest.dto.CreateDepositRequest;
import br.com.bolaoboladao.carteira.presentation.rest.dto.DepositResponse;
import br.com.bolaoboladao.carteira.presentation.rest.dto.PageResponse;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@Path("/wallet/me/deposits")
@Produces(MediaType.APPLICATION_JSON)
public class DepositResource {
    @Inject CreateDepositUseCase createDeposit;
    @Inject ReconcileDepositUseCase reconcileDeposit;
    @Inject DepositTransactions transactions;

    @POST
    public Uni<Response> create(CreateDepositRequest request,
                                @HeaderParam("Idempotency-Key") String idempotencyKey,
                                @Context ContainerRequestContext context) {
        long amount = request == null ? 0 : request.amountCents();
        return createDeposit.execute(user(context), amount, idempotencyKey)
                .map(result -> Response.status(result.created() ? 201 : 200)
                        .entity(DepositResponse.from(result.deposit())).build());
    }

    @GET
    public Uni<PageResponse<DepositResponse>> list(@QueryParam("page") @DefaultValue("0") int page,
                                                    @QueryParam("size") @DefaultValue("10") int size,
                                                    @Context ContainerRequestContext context) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(50, size));
        return transactions.list(user(context), safePage, safeSize)
                .map(result -> new PageResponse<>(result.items().stream().map(DepositResponse::from).toList(),
                        result.page(), result.size(), result.total()));
    }

    @GET
    @Path("/{depositId}")
    public Uni<DepositResponse> get(@PathParam("depositId") UUID depositId,
                                    @Context ContainerRequestContext context) {
        return transactions.getOwned(user(context), depositId).map(DepositResponse::from);
    }

    @POST
    @Path("/{depositId}/reconcile")
    public Uni<DepositResponse> reconcile(@PathParam("depositId") UUID depositId,
                                          @Context ContainerRequestContext context) {
        return reconcileDeposit.execute(user(context), depositId).map(DepositResponse::from);
    }

    private UUID user(ContainerRequestContext context) {
        UUID userId = (UUID) context.getProperty("authenticatedUserId");
        if (userId == null) throw new jakarta.ws.rs.ForbiddenException();
        return userId;
    }
}
