package br.com.bolaoboladao.carteira.infrastructure.messaging;

import br.com.bolaoboladao.carteira.domain.service.PaymentEventPublisher;
import br.com.bolaoboladao.carteira.infrastructure.messaging.dto.PaymentEvent;
import br.com.bolaoboladao.carteira.infrastructure.persistence.entity.OutboxEventEntity;
import br.com.bolaoboladao.carteira.infrastructure.persistence.repository.PanacheOutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

@ApplicationScoped
public class KafkaPaymentEventPublisher implements PaymentEventPublisher {

    private static final Logger LOG = Logger.getLogger(KafkaPaymentEventPublisher.class);
    private static final String TOPIC = "wallet-events";

    @Inject
    @Channel(TOPIC)
    Emitter<String> walletEventEmitter;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    PanacheOutboxEventRepository outboxRepository;

    @Override
    @Transactional
    @Fallback(fallbackMethod = "fallbackPublishPaymentAccepted")
    @Retry(maxRetries = 2, delay = 200)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5000)
    public void publishPaymentAccepted(UUID betId) {
        try {
            String payload = objectMapper.writeValueAsString(new PaymentEvent("PAYMENT_ACCEPTED", betId));
            walletEventEmitter.send(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing PaymentEvent", e);
        }
    }

    public void fallbackPublishPaymentAccepted(UUID betId) {
        LOG.warnf("Kafka indisponível, gravando evento PAYMENT_ACCEPTED no outbox para a bet %s", betId);
        try {
            String payload = objectMapper.writeValueAsString(new PaymentEvent("PAYMENT_ACCEPTED", betId));
            saveToOutbox(TOPIC, payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing PaymentEvent", e);
        }
    }

    @Override
    @Transactional
    @Fallback(fallbackMethod = "fallbackPublishPaymentRefused")
    @Retry(maxRetries = 2, delay = 200)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5000)
    public void publishPaymentRefused(UUID betId) {
        try {
            String payload = objectMapper.writeValueAsString(new PaymentEvent("PAYMENT_REFUSED", betId));
            walletEventEmitter.send(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing PaymentEvent", e);
        }
    }

    public void fallbackPublishPaymentRefused(UUID betId) {
        LOG.warnf("Kafka indisponível, gravando evento PAYMENT_REFUSED no outbox para a bet %s", betId);
        try {
            String payload = objectMapper.writeValueAsString(new PaymentEvent("PAYMENT_REFUSED", betId));
            saveToOutbox(TOPIC, payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing PaymentEvent", e);
        }
    }

    private void saveToOutbox(String topic, String payload) {
        OutboxEventEntity outboxEvent = new OutboxEventEntity(
                UUID.randomUUID(),
                topic,
                payload,
                LocalDateTime.now()
        );
        outboxRepository.persist(outboxEvent);
    }
}
