package br.com.bolaoboladao.carteira.infrastructure.persistence.entity;

import br.com.bolaoboladao.carteira.domain.model.Deposit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deposit_request", uniqueConstraints = {
        @UniqueConstraint(name = "deposit_user_idempotency_unique", columnNames = {"user_id", "idempotency_key"})
})
@Data
public class DepositEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Deposit.Status status;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKey;

    @Column(name = "provider_charge_id", unique = true)
    private UUID providerChargeId;

    @Column(name = "checkout_url", length = 2048)
    private String checkoutUrl;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;
}
