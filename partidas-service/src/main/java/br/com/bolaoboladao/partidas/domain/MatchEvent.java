package br.com.bolaoboladao.partidas.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Registro histórico (append-only) dos eventos ocorridos em uma partida.
 * Esta é a fonte de verdade interna do serviço; a publicação no Kafka
 * (tópico match-events) é feita a partir daqui - ver ADR-002.
 */
@Entity
@Table(name = "match_event")
public class MatchEvent extends PanacheEntity {

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    public Match match;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    public MatchEventType eventType;

    @Column(name = "team_home_score_at_event")
    public Integer teamHomeScoreAtEvent;

    @Column(name = "team_away_score_at_event")
    public Integer teamAwayScoreAtEvent;

    @NotNull
    @Column(name = "occurred_at", nullable = false)
    public LocalDateTime occurredAt;

    @NotNull
    @Column(name = "published", nullable = false)
    public boolean published = false;

    public MatchEvent() {
    }

    @jakarta.persistence.PrePersist
    void onCreate() {
        if (this.occurredAt == null) {
            this.occurredAt = LocalDateTime.now();
        }
    }
}
