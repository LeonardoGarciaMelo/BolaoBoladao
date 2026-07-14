package br.com.bolaoboladao.carteira.infrastructure.persistence.repository;

import br.com.bolaoboladao.carteira.domain.repository.ProcessedEventRepository;
import br.com.bolaoboladao.carteira.infrastructure.persistence.entity.ProcessedEventEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

@ApplicationScoped
public class PanacheProcessedEventRepository implements ProcessedEventRepository, PanacheRepositoryBase<ProcessedEventEntity, UUID> {

    @Override
    @Transactional
    public void markAsProcessed(UUID eventId, String eventType) {
        if (eventId == null) return;
        ProcessedEventEntity entity = new ProcessedEventEntity();
        entity.setEventId(eventId);
        entity.setEventType(eventType);
        entity.setProcessedAt(LocalDateTime.now());
        persist(entity);
    }

    @Override
    public boolean isProcessed(UUID eventId) {
        if (eventId == null) return false;
        return findByIdOptional(eventId).isPresent();
    }
}
