package br.com.bolaoboladao.carteira.presentation.messaging.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BetEvent(UUID eventId, String eventType, UUID betId, UUID userId, BigDecimal amount) {
}
