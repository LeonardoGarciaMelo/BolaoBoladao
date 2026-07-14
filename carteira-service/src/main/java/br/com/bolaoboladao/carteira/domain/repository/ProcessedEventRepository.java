package br.com.bolaoboladao.carteira.domain.repository;

import io.smallrye.mutiny.Uni;
import java.util.UUID;

public interface ProcessedEventRepository {
    Uni<Void> markAsProcessed(UUID eventId, String eventType);
    Uni<Boolean> isProcessed(UUID eventId);
}
