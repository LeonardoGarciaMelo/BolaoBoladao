package br.com.bolaoboladao.carteira.application;

import br.com.bolaoboladao.carteira.application.repository.ProcessedEventRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.UUID;
import java.util.function.Supplier;

@ApplicationScoped
public class IdempotentEventUseCase {

    private static final Logger LOG = Logger.getLogger(IdempotentEventUseCase.class);
    private final ProcessedEventRepository processedEventRepository;

    @Inject
    public IdempotentEventUseCase(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    public Uni<Void> execute(UUID eventId, String eventType, Supplier<Uni<Void>> action) {
        if (eventId == null) {
            return action.get();
        }
        return processedEventRepository.isProcessed(eventId)
                .flatMap(processed -> {
                    if (processed) {
                        LOG.infof("Event already processed: %s", eventId);
                        return Uni.createFrom().voidItem();
                    }
                    return action.get()
                            .flatMap(ignore -> processedEventRepository.markAsProcessed(eventId, eventType));
                });
    }
}
