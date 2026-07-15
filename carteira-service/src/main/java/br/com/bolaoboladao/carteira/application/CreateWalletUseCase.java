package br.com.bolaoboladao.carteira.application;

import br.com.bolaoboladao.carteira.domain.model.Wallet;
import br.com.bolaoboladao.carteira.domain.repository.WalletRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

@ApplicationScoped
public class CreateWalletUseCase {

    private final WalletRepository walletRepository;

    @Inject
    public CreateWalletUseCase(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @WithTransaction
    public Uni<Wallet> execute(UUID userId) {
        return walletRepository.lockUser(userId)
                .flatMap(ignored -> walletRepository.findByUserId(userId))
                .flatMap(wallet -> {
                    if (wallet != null) {
                        return Uni.createFrom().item(wallet);
                    }
                    Wallet newWallet = new Wallet(UUID.randomUUID(), userId);
                    return walletRepository.save(newWallet).replaceWith(newWallet);
                });
    }
}
