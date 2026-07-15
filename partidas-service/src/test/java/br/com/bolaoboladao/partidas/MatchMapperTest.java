package br.com.bolaoboladao.partidas;

import br.com.bolaoboladao.partidas.domain.Match;
import br.com.bolaoboladao.partidas.domain.MatchStatus;
import br.com.bolaoboladao.partidas.mapper.MatchMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchMapperTest {

    @Test
    void deveFecharPalpitesNoLimiteExatoDoHorarioPrevisto() {
        OffsetDateTime scheduledStart = OffsetDateTime.of(2026, 7, 20, 20, 0, 0, 0, ZoneOffset.UTC);
        Match match = new Match();
        match.status = MatchStatus.SCHEDULED;
        match.start = scheduledStart;

        assertTrue(MatchMapper.isBettingOpen(match, scheduledStart.minusNanos(1)));
        assertFalse(MatchMapper.isBettingOpen(match, scheduledStart));

        match.status = MatchStatus.IN_PROGRESS;
        assertFalse(MatchMapper.isBettingOpen(match, scheduledStart.minusHours(1)));
    }
}
