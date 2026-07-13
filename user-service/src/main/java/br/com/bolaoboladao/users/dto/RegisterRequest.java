package br.com.bolaoboladao.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_.-]{3,60}$") String username,
        @NotBlank @Size(min = 12, max = 72) String password
) {
}
