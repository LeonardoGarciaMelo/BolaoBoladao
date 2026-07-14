package br.com.bolaoboladao.partidas.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record CreateMatchRequest(
        @NotBlank String teamHomeName,
        @NotBlank String teamAwayName,
        @NotNull @Future OffsetDateTime start
) {
}
