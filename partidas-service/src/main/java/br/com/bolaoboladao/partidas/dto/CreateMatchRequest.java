package br.com.bolaoboladao.partidas.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreateMatchRequest(
        @NotBlank String teamHomeName,
        @NotBlank String teamAwayName,
        @NotNull @Future LocalDateTime start
) {
}
