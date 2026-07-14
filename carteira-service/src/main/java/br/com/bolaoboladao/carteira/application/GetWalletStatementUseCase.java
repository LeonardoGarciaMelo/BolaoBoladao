package br.com.bolaoboladao.carteira.application;

import br.com.bolaoboladao.carteira.domain.model.Ledger;
import br.com.bolaoboladao.carteira.domain.repository.LedgerRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class GetWalletStatementUseCase {

    @Inject
    LedgerRepository ledgerRepository;

    public List<Ledger> execute(UUID walletId, int page, int size) {
        return ledgerRepository.findByWalletIdPaged(walletId, page, size);
    }
}
