package br.com.bolaoboladao.partidas.service;

import br.com.bolaoboladao.partidas.cache.MatchCache;
import br.com.bolaoboladao.partidas.domain.Match;
import br.com.bolaoboladao.partidas.domain.MatchEvent;
import br.com.bolaoboladao.partidas.domain.MatchEventType;
import br.com.bolaoboladao.partidas.domain.MatchStatus;
import br.com.bolaoboladao.partidas.domain.Team;
import br.com.bolaoboladao.partidas.dto.CreateMatchRequest;
import br.com.bolaoboladao.partidas.dto.MatchResponse;
import br.com.bolaoboladao.partidas.dto.ScoreEventRequest;
import br.com.bolaoboladao.partidas.mapper.MatchMapper;
import br.com.bolaoboladao.partidas.repository.MatchEventRepository;
import br.com.bolaoboladao.partidas.repository.MatchRepository;
import br.com.bolaoboladao.partidas.repository.TeamRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras de negócio do serviço de Partidas.
 *
 * Nota sobre eventos: cada transição relevante grava um MatchEvent (fonte de
 * verdade interna, append-only). A publicação desses eventos no Kafka
 * (tópico match-events, conforme %MatchEvent do diagrama) agora é feita pelo
 * MatchOutboxRelay em background.
 */
@ApplicationScoped
public class MatchService {

    @Inject
    MatchRepository matchRepository;

    @Inject
    TeamRepository teamRepository;

    @Inject
    MatchEventRepository matchEventRepository;

    @Inject
    MatchCache matchCache;

    public Match createMatch(CreateMatchRequest request, UUID adminId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            String homeName = normalizeTeamName(request.teamHomeName());
            String awayName = normalizeTeamName(request.teamAwayName());
            teamRepository.lockNames(homeName, awayName);
            Team home = resolveTeam(homeName);
            Team away = resolveTeam(awayName);

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

            recordEvent(match, MatchEventType.MATCH_CREATED, adminId, null);

            return match;
        });
    }

    public Match startMatch(UUID matchId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Match match = getOrThrowForUpdate(matchId);

            if (match.status != MatchStatus.SCHEDULED) {
                throw new InvalidMatchStateException(
                        "Só é possível iniciar uma partida agendada. Status atual: " + match.status);
            }

            match.status = MatchStatus.IN_PROGRESS;

            recordEvent(match, MatchEventType.MATCH_STARTED, null, null);
            matchCache.evict(matchId);
            return match;
        });
    }

    public Match registerScore(UUID matchId, ScoreEventRequest request) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Match match = getOrThrowForUpdate(matchId);

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

            recordEvent(match, eventType, null, null);
            matchCache.evict(matchId);
            return match;
        });
    }

    public Match endMatch(UUID matchId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Match match = getOrThrowForUpdate(matchId);

            if (match.status != MatchStatus.IN_PROGRESS) {
                throw new InvalidMatchStateException(
                        "Só é possível encerrar uma partida em andamento. Status atual: " + match.status);
            }

            match.status = MatchStatus.FINISHED;
            match.end = OffsetDateTime.now(ZoneOffset.UTC);

            recordEvent(match, MatchEventType.MATCH_ENDED, null, null);
            matchCache.evict(matchId);
            return match;
        });
    }

    public Match cancelMatch(UUID matchId, String reason, UUID adminId, String idempotencyKey) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Match match = getOrThrowForUpdate(matchId);
            String normalizedReason = reason.trim();
            if (normalizedReason.length() < 10 || normalizedReason.length() > 500) {
                throw new BadRequestException("A justificativa deve ter entre 10 e 500 caracteres");
            }
            if (match.status == MatchStatus.CANCELED) {
                if (idempotencyKey.equals(match.cancelIdempotencyKey)) {
                    return match;
                }
                throw new InvalidMatchStateException("Partida já cancelada com outra chave de idempotência");
            }
            if (match.status == MatchStatus.FINISHED) {
                throw new InvalidMatchStateException("Não é possível cancelar uma partida encerrada");
            }
            if (match.status != MatchStatus.SCHEDULED && match.status != MatchStatus.IN_PROGRESS) {
                throw new InvalidMatchStateException("Status não permite cancelamento: " + match.status);
            }
            match.status = MatchStatus.CANCELED;
            match.canceledAt = OffsetDateTime.now(ZoneOffset.UTC);
            match.canceledBy = adminId;
            match.cancelReason = normalizedReason;
            match.cancelIdempotencyKey = idempotencyKey;
            recordEvent(match, MatchEventType.MATCH_CANCELED, adminId, match.cancelReason);
            matchCache.evict(matchId);
            return match;
        });
    }

    public MatchResponse findResponseById(UUID matchId) {
        return matchCache.get(matchId)
                .orElseGet(() -> {
                    Match match = getOrThrow(matchId);
                    MatchResponse response = MatchMapper.toResponse(match);
                    matchCache.put(matchId, response);
                    return response;
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

    private Match getOrThrowForUpdate(UUID matchId) {
        Match match = matchRepository.findByIdForUpdate(matchId);
        if (match == null) throw new MatchNotFoundException(matchId);
        return match;
    }

    private Team resolveTeam(String name) {
        return teamRepository.findByName(name)
                .orElseGet(() -> {
                    Team team = new Team(name);
                    teamRepository.persist(team);
                    return team;
                });
    }

    private String normalizeTeamName(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }

    private void recordEvent(Match match, MatchEventType type, UUID actorId, String reason) {
        MatchEvent event = new MatchEvent();
        event.match = match;
        event.eventType = type;
        event.teamHomeScoreAtEvent = match.teamHomeScore;
        event.teamAwayScoreAtEvent = match.teamAwayScore;
        event.occurredAt = OffsetDateTime.now(ZoneOffset.UTC);
        event.actorId = actorId;
        event.reason = reason;
        matchEventRepository.persist(event);
    }
}
