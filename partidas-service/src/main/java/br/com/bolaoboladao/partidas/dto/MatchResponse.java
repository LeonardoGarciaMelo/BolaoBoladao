package br.com.bolaoboladao.partidas.dto;

import br.com.bolaoboladao.partidas.domain.MatchStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record MatchResponse(
        UUID id,
        String teamHome,
        String teamAway,
        Integer teamHomeScore,
        Integer teamAwayScore,
        LocalDateTime start,
        LocalDateTime end,
        MatchStatus status
) {
}
