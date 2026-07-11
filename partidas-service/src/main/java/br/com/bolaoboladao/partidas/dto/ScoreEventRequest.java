package br.com.bolaoboladao.partidas.dto;

import jakarta.validation.constraints.NotNull;

public record ScoreEventRequest(
        @NotNull Side side
) {
    public enum Side {
        HOME, AWAY
    }
}
