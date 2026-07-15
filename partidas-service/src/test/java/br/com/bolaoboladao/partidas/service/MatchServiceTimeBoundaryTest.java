package br.com.bolaoboladao.partidas.service;

import br.com.bolaoboladao.partidas.domain.Match;
import br.com.bolaoboladao.partidas.domain.MatchStatus;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MatchServiceTimeBoundaryTest {

    @Test
    void deveAceitarGolAntesERecusarExatamenteNoTerminoPrevisto() {
        Match match = new Match();
        match.status = MatchStatus.IN_PROGRESS;
        match.expectedEnd = OffsetDateTime.of(2026, 7, 15, 20, 0, 0, 0, ZoneOffset.UTC);
        MatchService service = new MatchService();

        assertDoesNotThrow(() -> service.ensureLiveBeforeExpectedEnd(
                match, match.expectedEnd.minusNanos(1)));
        assertThrows(InvalidMatchStateException.class, () ->
                service.ensureLiveBeforeExpectedEnd(match, match.expectedEnd));
    }
}
