package br.com.bolaoboladao.carteira.application;

import br.com.bolaoboladao.carteira.domain.model.Ledger;
import br.com.bolaoboladao.carteira.domain.repository.LedgerRepository;
import br.com.bolaoboladao.carteira.domain.service.WalletCache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class GetWalletStatementUseCase {

    @Inject
    LedgerRepository ledgerRepository;

    @Inject
    WalletCache walletCache;

    public List<Ledger> execute(UUID walletId, int page, int size) {
        return walletCache.getStatement(walletId, page, size).orElseGet(() -> {
            List<Ledger> statement = ledgerRepository.findByWalletIdPaged(walletId, page, size);
            walletCache.setStatement(walletId, page, size, statement);
            return statement;
        });
    }
}
