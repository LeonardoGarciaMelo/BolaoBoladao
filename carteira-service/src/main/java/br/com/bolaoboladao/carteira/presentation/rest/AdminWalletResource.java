package br.com.bolaoboladao.carteira.presentation.rest;

import br.com.bolaoboladao.carteira.application.AdminCreditUseCase;
import br.com.bolaoboladao.carteira.application.GetWalletBalanceUseCase;
import br.com.bolaoboladao.carteira.domain.repository.LedgerRepository;
import br.com.bolaoboladao.carteira.domain.repository.WalletRepository;
import br.com.bolaoboladao.carteira.presentation.rest.dto.*;
import io.smallrye.mutiny.Uni;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Path("/admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
public class AdminWalletResource {
    @Inject GetWalletBalanceUseCase balanceUseCase;
    @Inject AdminCreditUseCase adminCreditUseCase;
    @Inject WalletRepository walletRepository;
    @Inject LedgerRepository ledgerRepository;
    @Inject JsonWebToken token;

    @GET
    @Path("/wallets/users/{userId}")
    @WithSession
    public Uni<AdminWalletResponse> wallet(@PathParam("userId") UUID userId) {
        return walletRepository.findByUserId(userId)
                .flatMap(wallet -> wallet == null
                        ? Uni.createFrom().item(new AdminWalletResponse(userId, null, 0))
                        : balanceUseCase.execute(userId)
                                .map(balance -> new AdminWalletResponse(userId, wallet.id(), cents(balance))));
    }

    @POST
    @Path("/wallets/users/{userId}/credits")
    public Uni<Response> credit(@PathParam("userId") UUID userId, @Valid AdminCreditRequest request,
                                @HeaderParam("Idempotency-Key") String idempotencyKey) {
        return adminCreditUseCase.execute(userId, UUID.fromString(token.getSubject()), request.amountCents(),
                        request.reason(), idempotencyKey)
                .map(result -> Response.status(Response.Status.CREATED).entity(result).build());
    }

    @GET
    @Path("/activity")
    @WithSession
    public Uni<PageResponse<LedgerEntryResponse>> activity(@QueryParam("offset") @DefaultValue("0") int offset,
                                                           @QueryParam("size") @DefaultValue("20") int size,
                                                           @QueryParam("until") OffsetDateTime until) {
        int safeOffset = Math.max(0, offset);
        int safeSize = Math.max(1, Math.min(size, 50));
        LocalDateTime snapshot = (until == null ? OffsetDateTime.now(ZoneOffset.UTC) : until)
                .withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        return ledgerRepository.findAdminCredits(safeOffset, safeSize, snapshot)
                .flatMap(entries -> ledgerRepository.countAdminCredits(snapshot)
                        .map(total -> new PageResponse<>(entries.stream().map(LedgerEntryResponse::from).toList(),
                                safeOffset, safeSize, total)));
    }

    private long cents(BigDecimal value) {
        return value.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    }
}
