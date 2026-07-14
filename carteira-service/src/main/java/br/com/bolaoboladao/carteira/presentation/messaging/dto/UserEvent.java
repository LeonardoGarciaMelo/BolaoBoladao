package br.com.bolaoboladao.carteira.presentation.messaging.dto;

import java.util.UUID;

public record UserEvent(UUID eventId, String eventType, UUID userId, String name) {
}
