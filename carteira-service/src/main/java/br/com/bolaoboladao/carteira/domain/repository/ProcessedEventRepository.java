package br.com.bolaoboladao.carteira.domain.repository;

import java.util.UUID;

public interface ProcessedEventRepository {
    void markAsProcessed(UUID eventId, String eventType);
    boolean isProcessed(UUID eventId);
}
