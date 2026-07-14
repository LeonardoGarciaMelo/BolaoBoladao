package br.com.bolaoboladao.users.dto;

import java.util.UUID;

public record UserResponse(UUID id, String name, String username, java.util.Set<String> roles) {
}
