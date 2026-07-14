package br.com.bolaoboladao.carteira.infrastructure.schedule;

import br.com.bolaoboladao.carteira.application.ConsolidateDailyBalanceUseCase;
import br.com.bolaoboladao.carteira.domain.model.Wallet;
import br.com.bolaoboladao.carteira.domain.repository.WalletRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import java.time.LocalDate;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DailyBalanceJob {

    private static final Logger LOG = Logger.getLogger(DailyBalanceJob.class);

    @Inject
    ConsolidateDailyBalanceUseCase consolidateDailyBalanceUseCase;

    @Inject
    WalletRepository walletRepository;

    @Scheduled(cron = "0 0 0 * * ?")
    @WithTransaction
    public Uni<Void> consolidateBalances() {
        var yesterday = LocalDate.now().minusDays(1);
        LOG.infof("Starting daily balance consolidation for date: %s", yesterday);

        return walletRepository.findAllWallets()
                .flatMap(wallets -> {
                    return Multi.createFrom().iterable(wallets)
                            .onItem().transformToUniAndConcatenate(wallet ->
                                    consolidateDailyBalanceUseCase.execute(wallet.id(), yesterday)
                                            .onFailure().recoverWithUni(e -> {
                                                LOG.errorf(e, "Error consolidating balance for wallet %s", wallet.id());
                                                return Uni.createFrom().voidItem();
                                            })
                            )
                            .collect().asList()
                            .invoke(() -> LOG.infof("Daily balance consolidation finished. Processed %d wallets.", wallets.size()))
                            .replaceWithVoid();
                });
    }
}
