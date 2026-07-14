package br.com.bolaoboladao.carteira.domain.model;

import java.util.UUID;

public record Wallet(UUID id, UUID userId) {
    public boolean belongsTo(UUID targetUserId) {
        return userId != null && userId.equals(targetUserId);
    }
}
