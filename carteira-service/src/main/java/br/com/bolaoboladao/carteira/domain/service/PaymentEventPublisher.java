package br.com.bolaoboladao.carteira.domain.service;

import io.smallrye.mutiny.Uni;

import java.util.UUID;

public interface PaymentEventPublisher {
    Uni<Void> publishPaymentAccepted(UUID betId);

    Uni<Void> publishPaymentRefused(UUID betId);

    Uni<Void> publishPaymentRefunded(UUID betId);

    Uni<Void> publishPaymentRefundFailed(UUID betId);
}
