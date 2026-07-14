package br.com.bolaoboladao.partidas.domain;

/**
 * Espelha os tipos definidos no %MatchEvent do diagrama de arquitetura:
 * TEAM_HOME_SCORED, TEAM_AWAY_SCORED, MATCH_STARTED, MATCH_ENDED.
 */
public enum MatchEventType {
    MATCH_CREATED,
    MATCH_STARTED,
    TEAM_HOME_SCORED,
    TEAM_AWAY_SCORED,
    MATCH_ENDED,
    MATCH_CANCELED
}
