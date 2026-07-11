package br.com.bolaoboladao.partidas.service;

import java.util.UUID;

public class MatchNotFoundException extends RuntimeException {
    public MatchNotFoundException(UUID id) {
        super("Partida não encontrada: " + id);
    }
}
