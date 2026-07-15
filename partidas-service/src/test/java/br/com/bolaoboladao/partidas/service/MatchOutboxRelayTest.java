package br.com.bolaoboladao.partidas.service;

import br.com.bolaoboladao.partidas.domain.Match;
import br.com.bolaoboladao.partidas.domain.MatchEvent;
import br.com.bolaoboladao.partidas.domain.MatchEventType;
import br.com.bolaoboladao.partidas.domain.Team;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatchOutboxRelayTest {

    @Test
    void deveEnriquecerEventoComTimesEHorarioAgendado() {
        Match match = new Match();
        match.id = UUID.randomUUID();
        match.teamHome = new Team("Aurora");
        match.teamAway = new Team("Estrela");
        match.start = OffsetDateTime.of(2026, 7, 20, 20, 0, 0, 0, ZoneOffset.UTC);

        MatchEvent event = new MatchEvent();
        event.id = 42L;
        event.match = match;
        event.eventType = MatchEventType.MATCH_CREATED;
        event.teamHomeScoreAtEvent = 0;
        event.teamAwayScoreAtEvent = 0;
        event.occurredAt = match.start.minusDays(1);

        var payload = MatchOutboxRelay.toDomainEvent(event);

        assertEquals("Aurora", payload.team_home());
        assertEquals("Estrela", payload.team_away());
        assertEquals(match.start, payload.scheduled_start());
        assertEquals(0, payload.score().team_home());
        assertEquals(0, payload.score().team_away());
    }
}
