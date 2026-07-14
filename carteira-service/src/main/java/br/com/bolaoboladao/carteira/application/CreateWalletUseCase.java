package br.com.bolaoboladao.carteira.application;

import br.com.bolaoboladao.carteira.domain.model.Wallet;
import br.com.bolaoboladao.carteira.domain.repository.WalletRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import java.util.UUID;

@ApplicationScoped
public class CreateWalletUseCase {

    private final WalletRepository walletRepository;

    @Inject
    public CreateWalletUseCase(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @WithTransaction
    public Uni<Void> execute(UUID userId) {
        return walletRepository.findByUserId(userId)
                .flatMap(wallet -> {
                    if (wallet != null) {
                        return Uni.createFrom().voidItem();
                    }
                    return walletRepository.save(new Wallet(UUID.randomUUID(), userId));
                });
    }
}
