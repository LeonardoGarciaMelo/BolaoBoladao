package br.com.bolaoboladao.carteira.domain.repository;

import br.com.bolaoboladao.carteira.domain.model.Wallet;
import io.smallrye.mutiny.Uni;
import java.util.List;
import java.util.UUID;

public interface WalletRepository {

    Uni<Void> save(Wallet wallet);
    Uni<Wallet> findByUserId(UUID userId);
    Uni<Wallet> findAndLockByUserId(UUID userId);
    Uni<List<Wallet>> findAllWallets();
}
