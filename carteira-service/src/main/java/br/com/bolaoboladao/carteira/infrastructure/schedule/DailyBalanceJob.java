package br.com.bolaoboladao.carteira.infrastructure.schedule;

import br.com.bolaoboladao.carteira.application.ConsolidateDailyBalanceUseCase;
import br.com.bolaoboladao.carteira.domain.model.Wallet;
import br.com.bolaoboladao.carteira.domain.repository.WalletRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
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
    @Transactional
    public void consolidateBalances() {
        var yesterday = LocalDate.now().minusDays(1);
        LOG.infof("Starting daily balance consolidation for date: %s", yesterday);

        var wallets = walletRepository.findAll();
        for (Wallet wallet : wallets) {
            try {
                consolidateDailyBalanceUseCase.execute(wallet.id(), yesterday);
            } catch (Exception e) {
                LOG.errorf(e, "Error consolidating balance for wallet %s", wallet.id());
            }
        }

        LOG.infof("Daily balance consolidation finished. Processed %d wallets.", wallets.size());
    }
}
