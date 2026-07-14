package br.com.bolaoboladao.carteira.presentation.rest.dto;

import java.util.UUID;

public record AdminWalletResponse(UUID userId, UUID walletId, long balanceCents) {
}
