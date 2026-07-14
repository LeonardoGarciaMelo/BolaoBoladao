package br.com.bolaoboladao.carteira.application;

import br.com.bolaoboladao.carteira.domain.model.Wallet;
import br.com.bolaoboladao.carteira.domain.repository.WalletRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;

@ApplicationScoped
public class CreateWalletUseCase {

    @Inject
    WalletRepository walletRepository;

    @Transactional
    public void execute(UUID userId) {
        if (walletRepository.findByUserId(userId).isPresent()) {
            return;
        }
        walletRepository.save(new Wallet(UUID.randomUUID(), userId));
    }
}
