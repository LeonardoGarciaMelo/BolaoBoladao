package br.com.bolaoboladao.partidas.service;

public class InvalidMatchStateException extends RuntimeException {
    public InvalidMatchStateException(String message) {
        super(message);
    }
}
