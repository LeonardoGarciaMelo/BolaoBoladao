package br.com.bolaoboladao.carteira.presentation.rest.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminCreditRequest(
        @Min(1) long amountCents,
        @NotBlank @Size(min = 10, max = 500) String reason
) {
}
