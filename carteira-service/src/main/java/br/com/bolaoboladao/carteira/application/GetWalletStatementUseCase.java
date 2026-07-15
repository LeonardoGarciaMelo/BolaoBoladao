package br.com.bolaoboladao.carteira.application;

import br.com.bolaoboladao.carteira.domain.model.Ledger;
import br.com.bolaoboladao.carteira.domain.model.Wallet;
import br.com.bolaoboladao.carteira.domain.repository.LedgerRepository;
import br.com.bolaoboladao.carteira.domain.repository.WalletRepository;
import br.com.bolaoboladao.carteira.domain.service.WalletCache;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class GetWalletStatementUseCase {

    private final LedgerRepository ledgerRepository;
    private final WalletCache walletCache;
    private final WalletRepository walletRepository;
    private final CreateWalletUseCase createWalletUseCase;

    public record StatementPage(List<Ledger> items, int page, int size, long total) {}

    @Inject
    public GetWalletStatementUseCase(LedgerRepository ledgerRepository,
                                     WalletCache walletCache,
                                     WalletRepository walletRepository,
                                     CreateWalletUseCase createWalletUseCase) {
        this.ledgerRepository = ledgerRepository;
        this.walletCache = walletCache;
        this.walletRepository = walletRepository;
        this.createWalletUseCase = createWalletUseCase;
    }

    @WithTransaction
    public Uni<StatementPage> executeForUser(UUID authenticatedUserId, int page, int size) {
        return createWalletUseCase.execute(authenticatedUserId)
                .flatMap(wallet -> fetchAndCacheStatement(wallet.id(), page, size)
                        .flatMap(items -> ledgerRepository.countByWalletId(wallet.id())
                                .map(total -> new StatementPage(items, page, size, total))));
    }

    @WithTransaction
    public Uni<List<Ledger>> execute(UUID authenticatedUserId, UUID walletId, int page, int size) {
        return walletRepository.findByUserId(authenticatedUserId)
                .onItem().ifNull().switchTo(() -> {
                    Wallet newWallet = new Wallet(UUID.randomUUID(), authenticatedUserId);
                    return walletRepository.save(newWallet).replaceWith(newWallet);
                })
                .flatMap(wallet -> {
                    if (!wallet.belongsTo(authenticatedUserId) || !wallet.id().equals(walletId)) {
                        return Uni.createFrom().failure(new ForbiddenException());
                    }
                    return fetchAndCacheStatement(walletId, page, size);
                });
    }

    private Uni<List<Ledger>> fetchAndCacheStatement(UUID walletId, int page, int size) {
        return walletCache.getStatement(walletId, page, size)
                .flatMap(cachedStatement -> {
                    if (cachedStatement.isPresent()) {
                        return Uni.createFrom().item(cachedStatement.get());
                    }
                    return ledgerRepository.findByWalletIdPaged(walletId, page, size)
                            .flatMap(statement -> walletCache.setStatement(walletId, page, size, statement)
                                    .replaceWith(statement));
                });
    }
}
