package br.com.bolaoboladao.carteira.presentation.messaging;

import br.com.bolaoboladao.carteira.application.CreateWalletUseCase;
import br.com.bolaoboladao.carteira.application.ProcessBetPaymentUseCase;
import br.com.bolaoboladao.carteira.domain.repository.ProcessedEventRepository;
import br.com.bolaoboladao.carteira.presentation.messaging.dto.BetEvent;
import br.com.bolaoboladao.carteira.presentation.messaging.dto.UserEvent;
import br.com.bolaoboladao.carteira.presentation.messaging.exception.InvalidEventException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class KafkaEventConsumer {

    private static final Logger LOG = Logger.getLogger(KafkaEventConsumer.class);

    @Inject
    ProcessedEventRepository processedEventRepository;

    @Inject
    CreateWalletUseCase createWalletUseCase;

    @Inject
    ProcessBetPaymentUseCase processBetPaymentUseCase;

    @Inject
    ObjectMapper objectMapper;

    @Incoming("user-events")
    @Transactional
    @Retry(maxRetries = 3, delay = 2000, abortOn = InvalidEventException.class)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5000)
    public void consumeUserEvent(String message) {
        var event = parseEvent(message, UserEvent.class);

        if (alreadyProcessed(event.eventId())) return;

        switch (event.eventType()) {
            case "USER_CREATED" -> createWalletUseCase.execute(event.userId());
            default -> LOG.warnf("Unknown user event type: %s", event.eventType());
        }

        markProcessed(event.eventId(), event.eventType());
    }

    @Incoming("bet-events")
    @Transactional
    @Retry(maxRetries = 3, delay = 2000, abortOn = InvalidEventException.class)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5000)
    public void consumeBetEvent(String message) {
        var event = parseEvent(message, BetEvent.class);

        if (alreadyProcessed(event.eventId())) return;

        switch (event.eventType()) {
            case "BET_CREATED" -> processBetPaymentUseCase.executeBetCreated(
                    event.betId(), event.userId(), event.amount());
            case "BET_SETTLED" -> processBetPaymentUseCase.executeBetSettled(
                    event.betId(), event.userId(), event.amount());
            default -> LOG.warnf("Unknown bet event type: %s", event.eventType());
        }

        markProcessed(event.eventId(), event.eventType());
    }


    private <T> T parseEvent(String message, Class<T> type) {
        try {
            return objectMapper.readValue(message, type);
        } catch (JsonProcessingException e) {
            throw new InvalidEventException("Malformed event payload: " + message, e);
        }
    }

    private boolean alreadyProcessed(UUID eventId) {
        if (eventId != null && processedEventRepository.isProcessed(eventId)) {
            LOG.infof("Event already processed: %s", eventId);
            return true;
        }
        return false;
    }

    private void markProcessed(UUID eventId, String eventType) {
        if (eventId != null) {
            processedEventRepository.markAsProcessed(eventId, eventType);
        }
    }
}
