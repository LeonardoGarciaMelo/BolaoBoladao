package br.com.bolaoboladao.carteira.application;

import br.com.bolaoboladao.carteira.domain.model.Deposit;
import br.com.bolaoboladao.carteira.infrastructure.payment.PaymentProviderClient;
import br.com.bolaoboladao.carteira.infrastructure.payment.ProviderCharge;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.UUID;

@ApplicationScoped
public class CreateDepositUseCase {
    @Inject DepositTransactions transactions;
    @Inject PaymentProviderClient provider;
    @Inject ProcessDepositUseCase processDeposit;

    @ConfigProperty(name = "deposit.min-cents", defaultValue = "100") long minimum;
    @ConfigProperty(name = "deposit.max-cents", defaultValue = "1000000") long maximum;
    @ConfigProperty(name = "payment.return-url") String returnUrl;

    public Uni<Result> execute(UUID userId, long amountCents, String idempotencyKey) {
        validate(amountCents, idempotencyKey);
        return transactions.prepare(userId, amountCents, idempotencyKey)
                .flatMap(prepared -> prepareProviderIfNeeded(prepared)
                        .map(deposit -> new Result(deposit, prepared.created())));
    }

    public Uni<Deposit> retry(Deposit deposit) {
        return prepareProviderIfNeeded(new DepositTransactions.PreparedDeposit(deposit, false));
    }

    private Uni<Deposit> prepareProviderIfNeeded(DepositTransactions.PreparedDeposit prepared) {
        Deposit deposit = prepared.deposit();
        if (deposit.status() != Deposit.Status.CREATING) {
            return Uni.createFrom().item(deposit);
        }
        String separator = returnUrl.contains("?") ? "&" : "?";
        String depositReturnUrl = returnUrl + separator + "depositId=" + deposit.id();
        return provider.create(deposit.id(), deposit.amountCents(), depositReturnUrl)
                .flatMap(charge -> transactions.attachProvider(deposit.id(), charge)
                        .flatMap(attached -> applyTerminalIfNeeded(attached, charge)))
                .onFailure().transform(error -> error instanceof ApiException ? error
                        : new ApiException(503, "PAYMENT_PROVIDER_UNAVAILABLE",
                        "Não foi possível preparar o pagamento agora. Tente novamente."));
    }

    private Uni<Deposit> applyTerminalIfNeeded(Deposit deposit, ProviderCharge charge) {
        return charge.status() == ProviderCharge.Status.PENDING
                ? Uni.createFrom().item(deposit)
                : processDeposit.execute(null, "RECONCILIATION", charge);
    }

    private void validate(long amountCents, String key) {
        if (key == null || key.isBlank() || key.length() > 150) {
            throw new ApiException(400, "INVALID_IDEMPOTENCY_KEY", "Informe uma chave de idempotência válida.");
        }
        if (amountCents < minimum || amountCents > maximum) {
            throw new ApiException(400, "INVALID_DEPOSIT_AMOUNT",
                    "O valor do depósito deve ficar entre R$ 1,00 e R$ 10.000,00.");
        }
    }

    public record Result(Deposit deposit, boolean created) {}
}
