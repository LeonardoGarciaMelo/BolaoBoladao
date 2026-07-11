package br.com.bolaoboladao.partidas.dto;

import br.com.bolaoboladao.partidas.domain.MatchEventType;

import java.time.LocalDateTime;
import java.util.UUID;

public record MatchEventResponse(
        UUID matchId,
        MatchEventType eventType,
        Integer teamHomeScoreAtEvent,
        Integer teamAwayScoreAtEvent,
        LocalDateTime occurredAt
) {
}
