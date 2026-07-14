package br.com.bolaoboladao.carteira.presentation.messaging;

import br.com.bolaoboladao.carteira.application.CreateWalletUseCase;
import br.com.bolaoboladao.carteira.application.IdempotentEventUseCase;
import br.com.bolaoboladao.carteira.application.ProcessBetPaymentUseCase;
import br.com.bolaoboladao.carteira.presentation.messaging.dto.BetEvent;
import br.com.bolaoboladao.carteira.presentation.messaging.dto.UserEvent;
import br.com.bolaoboladao.carteira.presentation.messaging.exception.InvalidEventException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class KafkaEventConsumer {

    private static final Logger LOG = Logger.getLogger(KafkaEventConsumer.class);

    private final IdempotentEventUseCase idempotentEventUseCase;
    private final CreateWalletUseCase createWalletUseCase;
    private final ProcessBetPaymentUseCase processBetPaymentUseCase;
    private final ObjectMapper objectMapper;

    @Inject
    public KafkaEventConsumer(IdempotentEventUseCase idempotentEventUseCase,
                              CreateWalletUseCase createWalletUseCase,
                              ProcessBetPaymentUseCase processBetPaymentUseCase,
                              ObjectMapper objectMapper) {
        this.idempotentEventUseCase = idempotentEventUseCase;
        this.createWalletUseCase = createWalletUseCase;
        this.processBetPaymentUseCase = processBetPaymentUseCase;
        this.objectMapper = objectMapper;
    }

    @Incoming("user-events")
    @WithTransaction
    @Retry(delay = 2000, abortOn = InvalidEventException.class)
    @CircuitBreaker(requestVolumeThreshold = 4, delay = 5000)
    public Uni<Void> consumeUserEvent(String message) {
        var event = parseEvent(message, UserEvent.class);
        return idempotentEventUseCase.execute(event.eventId(), event.eventType(), () -> handleUserEvent(event));
    }

    private Uni<Void> handleUserEvent(UserEvent event) {
        if ("USER_CREATED".equals(event.eventType())) {
            return createWalletUseCase.execute(event.userId());
        }
        LOG.warnf("Unknown user event type: %s", event.eventType());
        return Uni.createFrom().voidItem();
    }

    @Incoming("bet-events")
    @WithTransaction
    @Retry(delay = 2000, abortOn = InvalidEventException.class)
    @CircuitBreaker(requestVolumeThreshold = 4, delay = 5000)
    public Uni<Void> consumeBetEvent(String message) {
        var event = parseEvent(message, BetEvent.class);
        return idempotentEventUseCase.execute(event.eventId(), event.eventType(), () -> handleBetEvent(event));
    }

    private Uni<Void> handleBetEvent(BetEvent event) {
        return switch (event.eventType()) {
            case "BET_CREATED" ->
                    processBetPaymentUseCase.executeBetCreated(event.betId(), event.userId(), event.amount());
            case "BET_SETTLED" ->
                    processBetPaymentUseCase.executeBetSettled(event.betId(), event.userId(), event.amount());
            default -> {
                LOG.warnf("Unknown bet event type: %s", event.eventType());
                yield Uni.createFrom().voidItem();
            }
        };
    }

    private <T> T parseEvent(String message, Class<T> type) {
        try {
            return objectMapper.readValue(message, type);
        } catch (JsonProcessingException e) {
            throw new InvalidEventException("Malformed event payload: " + message, e);
        }
    }
}
