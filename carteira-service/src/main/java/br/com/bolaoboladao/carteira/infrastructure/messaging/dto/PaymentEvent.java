package br.com.bolaoboladao.carteira.infrastructure.messaging.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record PaymentEvent(
        @JsonProperty("event_type") String eventType,
        @JsonProperty("bet_id") UUID betId
) {
}
