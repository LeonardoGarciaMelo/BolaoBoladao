package br.com.bolaoboladao.partidas.mapper;

import br.com.bolaoboladao.partidas.domain.Match;
import br.com.bolaoboladao.partidas.domain.MatchEvent;
import br.com.bolaoboladao.partidas.dto.MatchEventResponse;
import br.com.bolaoboladao.partidas.dto.MatchResponse;

public final class MatchMapper {

    private MatchMapper() {
    }

    public static MatchResponse toResponse(Match match) {
        return new MatchResponse(
                match.id,
                match.teamHome.name,
                match.teamAway.name,
                match.teamHomeScore,
                match.teamAwayScore,
                match.start,
                match.end,
                match.status
        );
    }

    public static MatchEventResponse toResponse(MatchEvent event) {
        return new MatchEventResponse(
                event.match.id,
                event.eventType,
                event.teamHomeScoreAtEvent,
                event.teamAwayScoreAtEvent,
                event.occurredAt
        );
    }
}
