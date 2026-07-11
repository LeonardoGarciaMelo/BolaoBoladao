package br.com.bolaoboladao.partidas.service;

import br.com.bolaoboladao.partidas.domain.Match;
import br.com.bolaoboladao.partidas.domain.MatchEvent;
import br.com.bolaoboladao.partidas.domain.MatchEventType;
import br.com.bolaoboladao.partidas.domain.MatchStatus;
import br.com.bolaoboladao.partidas.domain.Team;
import br.com.bolaoboladao.partidas.dto.CreateMatchRequest;
import br.com.bolaoboladao.partidas.dto.ScoreEventRequest;
import br.com.bolaoboladao.partidas.repository.MatchEventRepository;
import br.com.bolaoboladao.partidas.repository.MatchRepository;
import br.com.bolaoboladao.partidas.repository.TeamRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Regras de negócio do serviço de Partidas.
 *
 * Nota sobre eventos: cada transição relevante grava um MatchEvent (fonte de
 * verdade interna, append-only). A publicação desses eventos no Kafka
 * (tópico match-events, conforme %MatchEvent do diagrama) ainda não está
 * implementada aqui - fica para quando o fluxo assíncrono do grupo for ligado
 * (ver docs/adr/ADR-002-eventos-partida.md).
 */
@ApplicationScoped
public class MatchService {

    @Inject
    MatchRepository matchRepository;

    @Inject
    TeamRepository teamRepository;

    @Inject
    MatchEventRepository matchEventRepository;

    public Match createMatch(CreateMatchRequest request) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Team home = resolveTeam(request.teamHomeName());
            Team away = resolveTeam(request.teamAwayName());

            if (home.id.equals(away.id)) {
                throw new InvalidMatchStateException("Um time não pode jogar contra si mesmo");
            }

            Match match = new Match();
            match.teamHome = home;
            match.teamAway = away;
            match.teamHomeScore = 0;
            match.teamAwayScore = 0;
            match.start = request.start();
            match.status = MatchStatus.SCHEDULED;
            matchRepository.persist(match);

            return match;
        });
    }

    public Match startMatch(UUID matchId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Match match = getOrThrow(matchId);

            if (match.status != MatchStatus.SCHEDULED) {
                throw new InvalidMatchStateException(
                        "Só é possível iniciar uma partida agendada. Status atual: " + match.status);
            }

            match.status = MatchStatus.IN_PROGRESS;

            recordEvent(match, MatchEventType.MATCH_STARTED);
            return match;
        });
    }

    public Match registerScore(UUID matchId, ScoreEventRequest request) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Match match = getOrThrow(matchId);

            if (match.status != MatchStatus.IN_PROGRESS) {
                throw new InvalidMatchStateException(
                        "Só é possível registrar gol em partida em andamento. Status atual: " + match.status);
            }

            MatchEventType eventType;
            if (request.side() == ScoreEventRequest.Side.HOME) {
                match.teamHomeScore = match.teamHomeScore + 1;
                eventType = MatchEventType.TEAM_HOME_SCORED;
            } else {
                match.teamAwayScore = match.teamAwayScore + 1;
                eventType = MatchEventType.TEAM_AWAY_SCORED;
            }

            recordEvent(match, eventType);
            return match;
        });
    }

    public Match endMatch(UUID matchId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Match match = getOrThrow(matchId);

            if (match.status != MatchStatus.IN_PROGRESS) {
                throw new InvalidMatchStateException(
                        "Só é possível encerrar uma partida em andamento. Status atual: " + match.status);
            }

            match.status = MatchStatus.FINISHED;
            match.end = LocalDateTime.now();

            recordEvent(match, MatchEventType.MATCH_ENDED);
            return match;
        });
    }

    public Match findByIdOrThrow(UUID matchId) {
        return getOrThrow(matchId);
    }

    public List<Match> findAll() {
        return matchRepository.listAll();
    }

    public List<MatchEvent> findEvents(UUID matchId) {
        getOrThrow(matchId);
        return matchEventRepository.findByMatchId(matchId);
    }

    private Match getOrThrow(UUID matchId) {
        return matchRepository.findByIdOptional(matchId)
                .orElseThrow(() -> new MatchNotFoundException(matchId));
    }

    private Team resolveTeam(String name) {
        return teamRepository.findByName(name)
                .orElseGet(() -> {
                    Team team = new Team(name);
                    teamRepository.persist(team);
                    return team;
                });
    }

    private void recordEvent(Match match, MatchEventType type) {
        MatchEvent event = new MatchEvent();
        event.match = match;
        event.eventType = type;
        event.teamHomeScoreAtEvent = match.teamHomeScore;
        event.teamAwayScoreAtEvent = match.teamAwayScore;
        event.occurredAt = LocalDateTime.now();
        matchEventRepository.persist(event);
    }
}
