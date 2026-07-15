package br.com.bolaoboladao.carteira.application;

import br.com.bolaoboladao.carteira.domain.model.Deposit;
import br.com.bolaoboladao.carteira.infrastructure.payment.PaymentProviderClient;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

@ApplicationScoped
public class ReconcileDepositUseCase {
    @Inject DepositTransactions transactions;
    @Inject CreateDepositUseCase createDeposit;
    @Inject PaymentProviderClient provider;
    @Inject ProcessDepositUseCase processDeposit;

    public Uni<Deposit> execute(UUID userId, UUID depositId) {
        return transactions.getOwned(userId, depositId).flatMap(deposit -> {
            if (deposit.terminal()) return Uni.createFrom().item(deposit);
            if (deposit.status() == Deposit.Status.CREATING) return createDeposit.retry(deposit);
            return provider.get(deposit.providerChargeId())
                    .flatMap(charge -> processDeposit.execute(null, "RECONCILIATION", charge))
                    .onFailure().transform(error -> error instanceof ApiException ? error
                            : new ApiException(503, "PAYMENT_PROVIDER_UNAVAILABLE",
                            "Não foi possível verificar o pagamento agora. Tente novamente."));
        });
    }

}
