package br.com.bolaoboladao.partidas.dto;

import br.com.bolaoboladao.partidas.domain.MatchStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MatchResponse(
        UUID id,
        String teamHome,
        String teamAway,
        Integer teamHomeScore,
        Integer teamAwayScore,
        OffsetDateTime start,
        Integer durationMinutes,
        OffsetDateTime expectedEnd,
        OffsetDateTime startedAt,
        OffsetDateTime end,
        MatchStatus status,
        boolean bettingOpen,
        OffsetDateTime canceledAt,
        UUID canceledBy,
        String cancelReason
) {
}
