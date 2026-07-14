package br.com.bolaoboladao.carteira.presentation.rest;

import br.com.bolaoboladao.carteira.domain.model.Wallet;
import br.com.bolaoboladao.carteira.domain.repository.WalletRepository;
import jakarta.ws.rs.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CarteiraResourceAuthorizationTest {
    private static final UUID AUTHENTICATED_USER_ID = UUID.fromString("22121193-3c26-4c26-812d-123456789012");
    private static final UUID WALLET_ID = UUID.fromString("33121193-3c26-4c26-812d-123456789012");

    private CarteiraResource resource;

    @BeforeEach
    void setUp() {
        resource = new CarteiraResource();
        resource.walletRepository = new InMemoryWalletRepository(new Wallet(WALLET_ID, AUTHENTICATED_USER_ID));
    }

    @Test
    void deveRecusarConsultaDeSaldoDeOutroUsuario() {
        UUID anotherUserId = UUID.fromString("44121193-3c26-4c26-812d-123456789012");

        assertThrows(ForbiddenException.class,
                () -> resource.getBalance(anotherUserId, AUTHENTICATED_USER_ID.toString()));
    }

    @Test
    void deveRecusarExtratoDeCarteiraQueNaoPertenceAoUsuario() {
        UUID anotherWalletId = UUID.fromString("55121193-3c26-4c26-812d-123456789012");

        assertThrows(ForbiddenException.class,
                () -> resource.getStatement(anotherWalletId, 0, 10, AUTHENTICATED_USER_ID.toString()));
    }

    @Test
    void deveRecusarIdentidadeAusenteOuInvalida() {
        assertThrows(ForbiddenException.class, () -> resource.getBalance(AUTHENTICATED_USER_ID, null));
        assertThrows(ForbiddenException.class, () -> resource.getStatement(WALLET_ID, 0, 10, "invalida"));
    }

    private record InMemoryWalletRepository(Wallet wallet) implements WalletRepository {
        @Override
        public void save(Wallet wallet) {
        }

        @Override
        public Optional<Wallet> findByUserId(UUID userId) {
            return wallet.userId().equals(userId) ? Optional.of(wallet) : Optional.empty();
        }

        @Override
        public Optional<Wallet> findAndLockByUserId(UUID userId) {
            return findByUserId(userId);
        }

        @Override
        public List<Wallet> findAllWallets() {
            return List.of(wallet);
        }
    }
}
