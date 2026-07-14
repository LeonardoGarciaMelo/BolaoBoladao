package br.com.bolaoboladao.partidas.dto;

import br.com.bolaoboladao.partidas.domain.MatchEventType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MatchEventResponse(
        UUID matchId,
        MatchEventType eventType,
        Integer teamHomeScoreAtEvent,
        Integer teamAwayScoreAtEvent,
        OffsetDateTime occurredAt,
        java.util.UUID actorId,
        String reason
) {
}
