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

    private static final int PAGE_SIZE = 100;

    @Scheduled(cron = "0 0 0 * * ?")
    public Uni<Void> consolidateBalances() {
        var yesterday = LocalDate.now().minusDays(1);
        LOG.infof("Starting daily balance consolidation for date: %s", yesterday);

        return walletRepository.countWallets()
                .flatMap(totalWallets -> processPages(yesterday, totalWallets))
                .invoke(() -> LOG.infof("Daily balance consolidation finished for date: %s", yesterday));
    }

    private Uni<Void> processPages(LocalDate date, long totalWallets) {
        if (totalWallets == 0) {
            return Uni.createFrom().voidItem();
        }

        int totalPages = (int) Math.ceil((double) totalWallets / PAGE_SIZE);
        
        // Crio uma sequência de páginas 0, 1, 2...
        return Multi.createFrom().range(0, totalPages)
                .onItem().transformToUniAndConcatenate(page -> 
                    processPage(date, page)
                )
                .collect().last() // Consumo a stream até o final
                .replaceWithVoid();
    }

    private Uni<Void> processPage(LocalDate date, int page) {
        return walletRepository.findWalletsPaged(page, PAGE_SIZE)
                .flatMap(wallets -> Multi.createFrom().iterable(wallets)
                        .onItem().transformToUniAndConcatenate(wallet ->
                                consolidateDailyBalanceUseCase.execute(wallet.id(), date)
                                        .onFailure().recoverWithUni(e -> {
                                            LOG.errorf(e, "Error consolidating balance for wallet %s", wallet.id());
                                            return Uni.createFrom().voidItem();
                                        })
                        )
                        .collect().asList()
                        .replaceWithVoid()
                );
    }
}
