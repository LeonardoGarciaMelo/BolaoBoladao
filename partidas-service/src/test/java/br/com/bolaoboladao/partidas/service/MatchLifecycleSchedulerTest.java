package br.com.bolaoboladao.partidas.service;

import br.com.bolaoboladao.partidas.repository.MatchRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

class MatchLifecycleSchedulerTest {
    @Test
    void deveProcessarInicioAntesDoFimNoCatchUp() {
        UUID matchId = UUID.randomUUID();
        Clock clock = Clock.fixed(Instant.parse("2026-07-20T23:00:00Z"), ZoneOffset.UTC);
        OffsetDateTime now = OffsetDateTime.now(clock);
        MatchRepository repository = mock(MatchRepository.class);
        MatchService service = mock(MatchService.class);
        when(repository.findScheduledDue(now, 100)).thenReturn(List.of(matchId));
        when(repository.findInProgressDue(now, 100)).thenReturn(List.of(matchId));

        MatchLifecycleScheduler scheduler = new MatchLifecycleScheduler();
        scheduler.matchRepository = repository;
        scheduler.matchService = service;
        scheduler.clock = clock;
        scheduler.advanceLifecycle();

        InOrder order = inOrder(repository, service);
        order.verify(repository).findScheduledDue(now, 100);
        order.verify(service).startDueMatch(matchId, now);
        order.verify(repository).findInProgressDue(now, 100);
        order.verify(service).endDueMatch(matchId, now);
    }
}
