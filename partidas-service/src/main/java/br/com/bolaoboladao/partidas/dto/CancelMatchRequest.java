package br.com.bolaoboladao.partidas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelMatchRequest(@NotBlank @Size(min = 10, max = 500) String reason) {
}
