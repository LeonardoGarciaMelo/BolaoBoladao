package br.com.bolaoboladao.partidas.service;

import br.com.bolaoboladao.partidas.cache.MatchCache;
import br.com.bolaoboladao.partidas.domain.*;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class MatchService {
    public static final int DEFAULT_DURATION_MINUTES = 105;
    private enum ScoreOperation { ADD, ANNUL }

    @Inject MatchRepository matchRepository;
    @Inject TeamRepository teamRepository;
    @Inject MatchEventRepository matchEventRepository;
    @Inject MatchCache matchCache;
    @Inject Clock clock;

    public record CreationResult(Match match, boolean created) {}

    public CreationResult createMatch(CreateMatchRequest request, UUID adminId, String idempotencyKey) {
        String key = requireKey(idempotencyKey);
        int duration = request.durationMinutes() == null ? DEFAULT_DURATION_MINUTES : request.durationMinutes();
        String homeName = normalizeTeamName(request.teamHomeName());
        String awayName = normalizeTeamName(request.teamAwayName());
        String fingerprint = fingerprint("CREATE", adminId, homeName, awayName,
                request.start().toInstant(), duration);

        return QuarkusTransaction.requiringNew().call(() -> {
            MatchEvent replay = findReplay(key, fingerprint);
            if (replay != null) return new CreationResult(replay.match, false);

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
            match.durationMinutes = duration;
            match.expectedEnd = request.start().plusMinutes(duration);
            match.status = MatchStatus.SCHEDULED;
            matchRepository.persist(match);
            recordEvent(match, MatchEventType.MATCH_CREATED, adminId, null, key, fingerprint, now());
            return new CreationResult(match, true);
        });
    }

    public Match startMatch(UUID matchId, UUID adminId, String idempotencyKey) {
        String key = requireKey(idempotencyKey);
        String fingerprint = fingerprint("START", matchId, adminId);
        return QuarkusTransaction.requiringNew().call(() -> {
            MatchEvent replay = findReplay(key, fingerprint);
            if (replay != null) return replay.match;
            Match match = getOrThrowForUpdate(matchId);
            OffsetDateTime current = now();
            if (match.status != MatchStatus.SCHEDULED) {
                throw new InvalidMatchStateException("Só é possível iniciar uma partida agendada. Status atual: " + match.status);
            }
            if (!current.isBefore(match.expectedEnd)) {
                throw new InvalidMatchStateException("O término previsto original da partida já passou");
            }
            match.status = MatchStatus.IN_PROGRESS;
            match.startedAt = current;
            match.expectedEnd = current.plusMinutes(match.durationMinutes);
            recordEvent(match, MatchEventType.MATCH_STARTED, adminId, null, key, fingerprint, current);
            evict(matchId);
            return match;
        });
    }

    public Match registerScore(UUID matchId, ScoreEventRequest request, UUID adminId, String idempotencyKey) {
        return changeScore(matchId, request, adminId, idempotencyKey, ScoreOperation.ADD);
    }

    public Match annulScore(UUID matchId, ScoreEventRequest request, UUID adminId, String idempotencyKey) {
        return changeScore(matchId, request, adminId, idempotencyKey, ScoreOperation.ANNUL);
    }

    private Match changeScore(UUID matchId, ScoreEventRequest request, UUID adminId,
                              String idempotencyKey, ScoreOperation operation) {
        String key = requireKey(idempotencyKey);
        String action = operation == ScoreOperation.ANNUL ? "ANNUL_GOAL" : "GOAL";
        String fingerprint = fingerprint(action, matchId, adminId, request.side());
        return QuarkusTransaction.requiringNew().call(() -> {
            MatchEvent replay = findReplay(key, fingerprint);
            if (replay != null) return replay.match;
            Match match = getOrThrowForUpdate(matchId);
            OffsetDateTime current = now();
            ensureLiveBeforeExpectedEnd(match, current);

            MatchEventType eventType;
            if (request.side() == ScoreEventRequest.Side.HOME) {
                match.teamHomeScore = adjustedScore(match.teamHomeScore, operation);
                eventType = operation == ScoreOperation.ANNUL
                        ? MatchEventType.TEAM_HOME_GOAL_ANNULLED : MatchEventType.TEAM_HOME_SCORED;
            } else {
                match.teamAwayScore = adjustedScore(match.teamAwayScore, operation);
                eventType = operation == ScoreOperation.ANNUL
                        ? MatchEventType.TEAM_AWAY_GOAL_ANNULLED : MatchEventType.TEAM_AWAY_SCORED;
            }
            recordEvent(match, eventType, adminId, null, key, fingerprint, current);
            evict(matchId);
            return match;
        });
    }

    public Match endMatch(UUID matchId, UUID adminId, String idempotencyKey) {
        String key = requireKey(idempotencyKey);
        String fingerprint = fingerprint("END", matchId, adminId);
        return QuarkusTransaction.requiringNew().call(() -> {
            MatchEvent replay = findReplay(key, fingerprint);
            if (replay != null) return replay.match;
            Match match = getOrThrowForUpdate(matchId);
            if (match.status != MatchStatus.IN_PROGRESS) {
                throw new InvalidMatchStateException("Só é possível encerrar uma partida em andamento. Status atual: " + match.status);
            }
            OffsetDateTime current = now();
            finish(match, current, adminId, key, fingerprint, current);
            return match;
        });
    }

    public Match cancelMatch(UUID matchId, String reason, UUID adminId, String idempotencyKey) {
        String key = requireKey(idempotencyKey);
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.length() < 10 || normalizedReason.length() > 500) {
            throw new BadRequestException("A justificativa deve ter entre 10 e 500 caracteres");
        }
        String fingerprint = fingerprint("CANCEL", matchId, adminId, normalizedReason);
        return QuarkusTransaction.requiringNew().call(() -> {
            MatchEvent replay = findReplay(key, fingerprint);
            if (replay != null) return replay.match;
            Match match = getOrThrowForUpdate(matchId);

            // Compatibilidade com cancelamentos criados antes do índice por comando.
            if (match.status == MatchStatus.CANCELED && key.equals(match.cancelIdempotencyKey)) {
                if (!normalizedReason.equals(match.cancelReason)) throw idempotencyConflict();
                return match;
            }
            if (match.status == MatchStatus.FINISHED) {
                throw new InvalidMatchStateException("Não é possível cancelar uma partida encerrada");
            }
            if (match.status != MatchStatus.SCHEDULED && match.status != MatchStatus.IN_PROGRESS) {
                throw new InvalidMatchStateException("Status não permite cancelamento: " + match.status);
            }
            match.status = MatchStatus.CANCELED;
            match.canceledAt = now();
            match.canceledBy = adminId;
            match.cancelReason = normalizedReason;
            match.cancelIdempotencyKey = key;
            recordEvent(match, MatchEventType.MATCH_CANCELED, adminId, normalizedReason, key, fingerprint, match.canceledAt);
            evict(matchId);
            return match;
        });
    }

    /** Inicia uma partida vencida usando os instantes previstos, mesmo em catch-up. */
    public void startDueMatch(UUID matchId, OffsetDateTime processedAt) {
        QuarkusTransaction.requiringNew().run(() -> {
            Match match = getOrThrowForUpdate(matchId);
            if (match.status != MatchStatus.SCHEDULED || match.start.isAfter(processedAt)) return;
            match.status = MatchStatus.IN_PROGRESS;
            match.startedAt = match.start;
            recordEvent(match, MatchEventType.MATCH_STARTED, null, null, null, null, processedAt);
            evict(matchId);
        });
    }

    /** Encerra uma partida vencida no instante previsto; occurredAt registra o processamento. */
    public void endDueMatch(UUID matchId, OffsetDateTime processedAt) {
        QuarkusTransaction.requiringNew().run(() -> {
            Match match = getOrThrowForUpdate(matchId);
            if (match.status != MatchStatus.IN_PROGRESS || match.expectedEnd.isAfter(processedAt)) return;
            finish(match, match.expectedEnd, null, null, null, processedAt);
        });
    }

    public MatchResponse findResponseById(UUID matchId) {
        return matchCache.get(matchId).orElseGet(() -> {
            MatchResponse response = MatchMapper.toResponse(getOrThrow(matchId));
            matchCache.put(matchId, response);
            return response;
        });
    }

    public Match findByIdOrThrow(UUID matchId) { return getOrThrow(matchId); }
    public List<Match> findAll() { return matchRepository.listAll(); }
    public List<MatchEvent> findEvents(UUID matchId) { getOrThrow(matchId); return matchEventRepository.findByMatchId(matchId); }

    private void finish(Match match, OffsetDateTime endedAt, UUID actorId, String key,
                        String fingerprint, OffsetDateTime occurredAt) {
        match.status = MatchStatus.FINISHED;
        match.end = endedAt;
        recordEvent(match, MatchEventType.MATCH_ENDED, actorId, null, key, fingerprint, occurredAt);
        evict(match.id);
    }

    void ensureLiveBeforeExpectedEnd(Match match, OffsetDateTime current) {
        if (match.status != MatchStatus.IN_PROGRESS) {
            throw new InvalidMatchStateException("Só é possível alterar o placar de uma partida em andamento. Status atual: " + match.status);
        }
        if (!current.isBefore(match.expectedEnd)) {
            throw new InvalidMatchStateException("O término previsto da partida já foi alcançado");
        }
    }

    private int adjustedScore(int current, ScoreOperation operation) {
        if (operation == ScoreOperation.ANNUL && current == 0) {
            throw new InvalidMatchStateException("Não há gol para anular");
        }
        if (operation == ScoreOperation.ADD && current == 99) {
            throw new InvalidMatchStateException("O placar máximo por time é 99");
        }
        return operation == ScoreOperation.ANNUL ? current - 1 : current + 1;
    }

    private MatchEvent findReplay(String key, String fingerprint) {
        matchEventRepository.lockCommandKey(key);
        MatchEvent existing = matchEventRepository.findByCommandKey(key);
        if (existing == null) return null;
        if (!fingerprint.equals(existing.commandFingerprint)) throw idempotencyConflict();
        return existing;
    }

    private InvalidMatchStateException idempotencyConflict() {
        return new InvalidMatchStateException("Idempotency-Key já utilizada com outro comando ou conteúdo");
    }

    private Match getOrThrow(UUID matchId) {
        return matchRepository.findByIdOptional(matchId).orElseThrow(() -> new MatchNotFoundException(matchId));
    }

    private Match getOrThrowForUpdate(UUID matchId) {
        Match match = matchRepository.findByIdForUpdate(matchId);
        if (match == null) throw new MatchNotFoundException(matchId);
        return match;
    }

    private Team resolveTeam(String name) {
        return teamRepository.findByName(name).orElseGet(() -> { Team team = new Team(name); teamRepository.persist(team); return team; });
    }

    private String normalizeTeamName(String name) { return name.trim().replaceAll("\\s+", " "); }

    private String requireKey(String key) {
        if (key == null || key.isBlank()) throw new BadRequestException("Idempotency-Key é obrigatório");
        String normalized = key.trim();
        if (normalized.length() > 150) throw new BadRequestException("Idempotency-Key deve ter no máximo 150 caracteres");
        return normalized;
    }

    private String fingerprint(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException("Não foi possível gerar a impressão do comando", exception);
        }
    }

    private OffsetDateTime now() { return OffsetDateTime.now(clock); }
    private void evict(UUID matchId) { matchCache.evict(matchId); }

    private void recordEvent(Match match, MatchEventType type, UUID actorId, String reason,
                             String key, String fingerprint, OffsetDateTime occurredAt) {
        MatchEvent event = new MatchEvent();
        event.match = match;
        event.eventType = type;
        event.teamHomeScoreAtEvent = match.teamHomeScore;
        event.teamAwayScoreAtEvent = match.teamAwayScore;
        event.occurredAt = occurredAt;
        event.actorId = actorId;
        event.reason = reason;
        event.commandKey = key;
        event.commandFingerprint = fingerprint;
        event.durationMinutesAtEvent = match.durationMinutes;
        event.expectedEndAtEvent = match.expectedEnd;
        event.startedAtEvent = match.startedAt;
        event.endedAtEvent = match.end;
        matchEventRepository.persist(event);
    }
}
