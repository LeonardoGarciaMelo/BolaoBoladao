package br.com.bolaoboladao.carteira.domain.repository;

import br.com.bolaoboladao.carteira.domain.model.Wallet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository {
    void save(Wallet wallet);
    Optional<Wallet> findByUserId(UUID userId);
    Optional<Wallet> findAndLockByUserId(UUID userId);
    List<Wallet> findAllWallets();
}
