package br.com.bolaoboladao.carteira.application;

import br.com.bolaoboladao.carteira.domain.model.Ledger;
import br.com.bolaoboladao.carteira.domain.model.Wallet;
import br.com.bolaoboladao.carteira.domain.repository.LedgerRepository;
import br.com.bolaoboladao.carteira.domain.repository.WalletRepository;
import br.com.bolaoboladao.carteira.domain.service.WalletCache;
import br.com.bolaoboladao.carteira.presentation.rest.dto.AdminCreditResponse;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@ApplicationScoped
public class AdminCreditUseCase {
    private final WalletRepository walletRepository;
    private final LedgerRepository ledgerRepository;
    private final GetWalletBalanceUseCase balanceUseCase;
    private final WalletCache walletCache;

    @ConfigProperty(name = "admin.credit.max-cents")
    long maxCents;

    @Inject
    public AdminCreditUseCase(WalletRepository walletRepository, LedgerRepository ledgerRepository,
                              GetWalletBalanceUseCase balanceUseCase, WalletCache walletCache) {
        this.walletRepository = walletRepository;
        this.ledgerRepository = ledgerRepository;
        this.balanceUseCase = balanceUseCase;
        this.walletCache = walletCache;
    }

    @WithTransaction
    public Uni<AdminCreditResponse> execute(UUID userId, UUID adminId, long amountCents,
                                            String reason, String idempotencyKey) {
        if (amountCents < 1 || amountCents > maxCents) {
            return Uni.createFrom().failure(new BadRequestException("Valor deve estar entre 1 e " + maxCents + " centavos"));
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Uni.createFrom().failure(new BadRequestException("Idempotency-Key é obrigatório"));
        }
        String normalizedReason = reason.trim();
        if (normalizedReason.length() < 10 || normalizedReason.length() > 500) {
            return Uni.createFrom().failure(new BadRequestException(
                    "A justificativa deve ter entre 10 e 500 caracteres"));
        }
        BigDecimal amount = BigDecimal.valueOf(amountCents, 2);
        return ledgerRepository.lockIdempotencyKey(idempotencyKey)
                .flatMap(ignored -> ledgerRepository.findByIdempotencyKey(idempotencyKey))
                .flatMap(existing -> existing == null
                        ? createCredit(userId, adminId, amount, normalizedReason, idempotencyKey)
                        : replay(existing, userId, adminId, amount, normalizedReason));
    }

    private Uni<AdminCreditResponse> createCredit(UUID userId, UUID adminId, BigDecimal amount,
                                                   String reason, String idempotencyKey) {
        return walletRepository.lockUser(userId)
                .flatMap(ignored -> walletRepository.findAndLockByUserId(userId)
                        .onItem().ifNull().switchTo(() -> {
                            Wallet wallet = new Wallet(UUID.randomUUID(), userId);
                            return walletRepository.save(wallet).replaceWith(wallet);
                        }))
                .flatMap(wallet -> balanceUseCase.calculateBalanceFromDatabase(userId)
                        .flatMap(before -> {
                            BigDecimal after = before.add(amount);
                            Ledger entry = new Ledger(UUID.randomUUID(), wallet.id(), Ledger.Reason.ADMIN_CREDIT,
                                    Ledger.Operation.CREDIT, amount, LocalDateTime.now(), userId, adminId,
                                    reason, idempotencyKey, before, after);
                            return ledgerRepository.save(entry)
                                    .flatMap(ignored -> walletCache.invalidateBalance(userId))
                                    .flatMap(ignored -> walletCache.invalidateStatement(wallet.id()))
                                    .replaceWith(toResponse(entry));
                        }));
    }

    private Uni<AdminCreditResponse> replay(Ledger existing, UUID userId, UUID adminId,
                                            BigDecimal amount, String reason) {
        boolean sameRequest = existing.reason() == Ledger.Reason.ADMIN_CREDIT
                && userId.equals(existing.referenceId())
                && adminId.equals(existing.createdBy())
                && amount.compareTo(existing.amount()) == 0
                && reason.equals(existing.note());
        return sameRequest
                ? Uni.createFrom().item(toResponse(existing))
                : Uni.createFrom().failure(new ClientErrorException("Idempotency-Key já utilizada com outros dados", 409));
    }

    private AdminCreditResponse toResponse(Ledger entry) {
        return new AdminCreditResponse(entry.id(), entry.referenceId(), entry.walletId(), cents(entry.amount()),
                cents(entry.balanceBefore()), cents(entry.balanceAfter()), entry.note(), entry.createdBy(), entry.occurredAt());
    }

    private long cents(BigDecimal value) {
        return value.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    }
}
