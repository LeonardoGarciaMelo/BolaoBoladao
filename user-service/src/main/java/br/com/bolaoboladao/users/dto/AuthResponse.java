package br.com.bolaoboladao.users.dto;

public record AuthResponse(String accessToken, String tokenType, long expiresIn) {
}
