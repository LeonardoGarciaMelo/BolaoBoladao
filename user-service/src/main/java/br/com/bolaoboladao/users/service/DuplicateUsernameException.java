package br.com.bolaoboladao.users.service;

public class DuplicateUsernameException extends RuntimeException {
    public DuplicateUsernameException() {
        super("Nome de usuário já está em uso");
    }
}
