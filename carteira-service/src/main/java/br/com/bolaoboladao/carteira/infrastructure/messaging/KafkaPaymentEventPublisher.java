package br.com.bolaoboladao.carteira.infrastructure.messaging;

import br.com.bolaoboladao.carteira.domain.service.PaymentEventPublisher;
import br.com.bolaoboladao.carteira.infrastructure.messaging.dto.PaymentEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.UUID;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@ApplicationScoped
public class KafkaPaymentEventPublisher implements PaymentEventPublisher {

    @Inject
    @Channel("wallet-events")
    Emitter<String> walletEventEmitter;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public void publishPaymentAccepted(UUID betId) {
        try {
            String payload = objectMapper.writeValueAsString(new PaymentEvent("PAYMENT_ACCEPTED", betId));
            walletEventEmitter.send(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing PaymentEvent", e);
        }
    }

    @Override
    public void publishPaymentRefused(UUID betId) {
        try {
            String payload = objectMapper.writeValueAsString(new PaymentEvent("PAYMENT_REFUSED", betId));
            walletEventEmitter.send(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing PaymentEvent", e);
        }
    }
}
