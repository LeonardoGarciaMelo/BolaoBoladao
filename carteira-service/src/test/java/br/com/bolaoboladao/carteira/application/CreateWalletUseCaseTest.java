package br.com.bolaoboladao.carteira.application;

import br.com.bolaoboladao.carteira.domain.model.Wallet;
import br.com.bolaoboladao.carteira.domain.repository.WalletRepository;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateWalletUseCaseTest {

    @Test
    void deveCriarCarteiraUmaUnicaVezEmChamadasRepetidas() {
        WalletRepository repository = mock(WalletRepository.class);
        AtomicReference<Wallet> stored = new AtomicReference<>();
        UUID userId = UUID.randomUUID();

        when(repository.lockUser(userId)).thenReturn(Uni.createFrom().voidItem());
        when(repository.findByUserId(userId)).thenAnswer(ignored -> {
            Wallet wallet = stored.get();
            return wallet == null ? Uni.createFrom().nullItem() : Uni.createFrom().item(wallet);
        });
        when(repository.save(any(Wallet.class))).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return Uni.createFrom().voidItem();
        });

        CreateWalletUseCase useCase = new CreateWalletUseCase(repository);
        Wallet first = useCase.execute(userId).await().indefinitely();
        Wallet retry = useCase.execute(userId).await().indefinitely();

        assertEquals(first.id(), retry.id());
        assertEquals(userId, retry.userId());
        verify(repository, times(1)).save(any(Wallet.class));
    }
}
