package br.com.bolaoboladao.partidas.service;

import br.com.bolaoboladao.partidas.repository.MatchRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

@ApplicationScoped
public class MatchLifecycleScheduler {
    private static final Logger LOG = Logger.getLogger(MatchLifecycleScheduler.class);
    private static final int BATCH_SIZE = 100;

    @Inject MatchRepository matchRepository;
    @Inject MatchService matchService;
    @Inject Clock clock;

    @Scheduled(every = "${match.lifecycle.interval:1s}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void advanceLifecycle() {
        OffsetDateTime processedAt = OffsetDateTime.now(clock);
        for (UUID matchId : matchRepository.findScheduledDue(processedAt, BATCH_SIZE)) {
            safely(() -> matchService.startDueMatch(matchId, processedAt), matchId, "iniciar");
        }
        // A segunda consulta ocorre após os commits de início e também captura o catch-up completo.
        for (UUID matchId : matchRepository.findInProgressDue(processedAt, BATCH_SIZE)) {
            safely(() -> matchService.endDueMatch(matchId, processedAt), matchId, "encerrar");
        }
    }

    private void safely(Runnable transition, UUID matchId, String action) {
        try {
            transition.run();
        } catch (RuntimeException exception) {
            LOG.errorf(exception, "Falha ao %s automaticamente a partida %s", action, matchId);
        }
    }
}
