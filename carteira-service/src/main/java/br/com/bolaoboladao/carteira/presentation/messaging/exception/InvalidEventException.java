package br.com.bolaoboladao.carteira.presentation.messaging.exception;

public class InvalidEventException extends RuntimeException {
    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
