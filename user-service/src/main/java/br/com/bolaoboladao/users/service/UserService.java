package br.com.bolaoboladao.users.service;

import br.com.bolaoboladao.users.domain.User;
import br.com.bolaoboladao.users.dto.LoginRequest;
import br.com.bolaoboladao.users.dto.RegisterRequest;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Locale;

@ApplicationScoped
public class UserService {

    @Transactional
    public User register(RegisterRequest request) {
        String username = normalizeUsername(request.username());
        if (User.count("username", username) > 0) {
            throw new DuplicateUsernameException();
        }

        User user = new User();
        user.id = java.util.UUID.randomUUID();
        user.name = request.name().trim();
        user.username = username;
        user.passwordHash = BcryptUtil.bcryptHash(request.password());
        user.persist();
        return user;
    }

    public User authenticate(LoginRequest request) {
        return User.<User>find("username", normalizeUsername(request.username())).firstResultOptional()
                .filter(user -> BcryptUtil.matches(request.password(), user.passwordHash))
                .orElseThrow(InvalidCredentialsException::new);
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
