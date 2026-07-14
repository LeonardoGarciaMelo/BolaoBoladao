package br.com.bolaoboladao.partidas.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Partida entre dois times. Segue o modelo definido no diagrama de arquitetura
 * do grupo (docs/arquitetura.md): id UUID, team_home, team_away, start/end,
 * placar e status do jogo.
 */
@Entity
@Table(name = "match")
public class Match extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "team_home_id", nullable = false)
    public Team teamHome;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "team_away_id", nullable = false)
    public Team teamAway;

    @Column(name = "team_home_score")
    public Integer teamHomeScore;

    @Column(name = "team_away_score")
    public Integer teamAwayScore;

    @NotNull
    @Column(name = "start_at", nullable = false)
    public OffsetDateTime start;

    @Column(name = "end_at")
    public OffsetDateTime end;

    @Column(name = "canceled_at")
    public OffsetDateTime canceledAt;

    @Column(name = "canceled_by")
    public UUID canceledBy;

    @Column(name = "cancel_reason", length = 500)
    public String cancelReason;

    @Column(name = "cancel_idempotency_key", unique = true, length = 100)
    public String cancelIdempotencyKey;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    public MatchStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    public OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;

    public Match() {
    }

    @jakarta.persistence.PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = MatchStatus.SCHEDULED;
        }
    }

    @jakarta.persistence.PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
    }
}
