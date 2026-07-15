package br.com.bolaoboladao.partidas.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.OffsetDateTime;

public record CreateMatchRequest(
        @NotBlank String teamHomeName,
        @NotBlank String teamAwayName,
        @NotNull @Future OffsetDateTime start,
        @Min(1) @Max(300) Integer durationMinutes
) {
}
