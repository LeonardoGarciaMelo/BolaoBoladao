package br.com.bolaoboladao.users.service;

import br.com.bolaoboladao.users.domain.User;
import br.com.bolaoboladao.users.domain.UserRole;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;

@ApplicationScoped
public class AdminBootstrap {
    @Inject EntityManager entityManager;

    @ConfigProperty(name = "admin.bootstrap.name")
    Optional<String> name;

    @ConfigProperty(name = "admin.bootstrap.username")
    Optional<String> username;

    @ConfigProperty(name = "admin.bootstrap.password")
    Optional<String> password;

    @ConfigProperty(name = "admin.bootstrap.password-file")
    Optional<String> passwordFile;

    @Transactional
    void onStart(@Observes StartupEvent ignored) {
        Optional<String> resolvedPassword = resolvePassword();
        if (name.isEmpty() && username.isEmpty() && resolvedPassword.isEmpty()) {
            return;
        }
        if (name.isEmpty() || username.isEmpty() || resolvedPassword.isEmpty()) {
            throw new IllegalStateException("Bootstrap do administrador exige nome, username e senha");
        }
        String normalizedUsername = username.orElseThrow().trim().toLowerCase(Locale.ROOT);
        entityManager.createNativeQuery(
                        "select pg_advisory_xact_lock(hashtextextended(:username, 0))::text")
                .setParameter("username", normalizedUsername)
                .getSingleResult();
        Optional<User> existing = User.<User>find("username", normalizedUsername).firstResultOptional();
        if (existing.isPresent()) {
            if (existing.orElseThrow().role != UserRole.ADMIN) {
                throw new IllegalStateException("Username configurado para o administrador já pertence a um usuário comum");
            }
            return;
        }
        if (resolvedPassword.orElseThrow().length() < 12) {
            throw new IllegalStateException("Senha do administrador deve possuir ao menos 12 caracteres");
        }
        User admin = new User();
        admin.id = UUID.randomUUID();
        admin.name = name.orElseThrow().trim();
        admin.username = normalizedUsername;
        admin.passwordHash = BcryptUtil.bcryptHash(resolvedPassword.orElseThrow());
        admin.role = UserRole.ADMIN;
        admin.persist();
    }

    private Optional<String> resolvePassword() {
        if (passwordFile.isPresent() && !passwordFile.orElseThrow().isBlank()) {
            try {
                return Optional.of(Files.readString(Path.of(passwordFile.orElseThrow())).trim());
            } catch (Exception exception) {
                throw new IllegalStateException("Não foi possível ler o secret do administrador", exception);
            }
        }
        return password.filter(value -> !value.isBlank());
    }
}
