package br.com.bolaoboladao.carteira.infrastructure.messaging;

import br.com.bolaoboladao.carteira.domain.service.PaymentEventPublisher;
import br.com.bolaoboladao.carteira.infrastructure.messaging.dto.PaymentEvent;
import br.com.bolaoboladao.carteira.infrastructure.persistence.entity.OutboxEventEntity;
import br.com.bolaoboladao.carteira.infrastructure.persistence.repository.PanacheOutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.UUID;

@ApplicationScoped
public class KafkaPaymentEventPublisher implements PaymentEventPublisher {
    private static final String TOPIC = "wallet-events";

    @Inject ObjectMapper objectMapper;
    @Inject PanacheOutboxEventRepository outboxRepository;

    @Override
    @WithTransaction
    public Uni<Void> publishPaymentAccepted(UUID betId) {
        return save("PAYMENT_ACCEPTED", betId);
    }

    @Override
    @WithTransaction
    public Uni<Void> publishPaymentRefused(UUID betId) {
        return save("PAYMENT_REFUSED", betId);
    }

    @Override
    @WithTransaction
    public Uni<Void> publishPaymentRefunded(UUID betId) {
        return save("PAYMENT_REFUNDED", betId);
    }

    @Override
    @WithTransaction
    public Uni<Void> publishPaymentRefundFailed(UUID betId) {
        return save("PAYMENT_REFUND_FAILED", betId);
    }

    private Uni<Void> save(String eventType, UUID betId) {
        try {
            String payload = objectMapper.writeValueAsString(new PaymentEvent(eventType, betId));
            return outboxRepository.persist(new OutboxEventEntity(
                    UUID.randomUUID(), TOPIC, payload, LocalDateTime.now())).replaceWithVoid();
        } catch (JsonProcessingException exception) {
            return Uni.createFrom().failure(new IllegalStateException("Falha ao serializar evento de pagamento", exception));
        }
    }
}
