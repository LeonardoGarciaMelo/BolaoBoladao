package br.com.bolaoboladao.carteira.domain.service;

import java.util.UUID;

public interface PaymentEventPublisher {
    void publishPaymentAccepted(UUID betId);
    void publishPaymentRefused(UUID betId);
}
