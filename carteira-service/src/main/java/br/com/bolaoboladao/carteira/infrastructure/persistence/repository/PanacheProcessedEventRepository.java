package br.com.bolaoboladao.carteira.infrastructure.persistence.repository;

import br.com.bolaoboladao.carteira.application.repository.ProcessedEventRepository;
import br.com.bolaoboladao.carteira.infrastructure.persistence.entity.ProcessedEventEntity;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@ApplicationScoped
public class PanacheProcessedEventRepository implements ProcessedEventRepository, PanacheRepositoryBase<ProcessedEventEntity, UUID> {

    @Override
    public Uni<Void> markAsProcessed(UUID eventId, String eventType) {
        if (eventId == null) return Uni.createFrom().voidItem();
        ProcessedEventEntity entity = new ProcessedEventEntity();
        entity.setEventId(eventId);
        entity.setEventType(eventType);
        entity.setProcessedAt(LocalDateTime.now());
        return persist(entity).replaceWithVoid();
    }

    @Override
    public Uni<Boolean> isProcessed(UUID eventId) {
        if (eventId == null) return Uni.createFrom().item(false);
        return findById(eventId).onItem().transform(Objects::nonNull);
    }
}
