package br.com.bolaoboladao.users.service;

import br.com.bolaoboladao.users.domain.User;
import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.util.KeyUtils;
import io.smallrye.jwt.util.ResourceUtils;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.PrivateKey;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class TokenService {

    @ConfigProperty(name = "jwt.issuer")
    String issuer;

    @ConfigProperty(name = "jwt.audience")
    String audience;

    @ConfigProperty(name = "jwt.expiration-seconds")
    long expirationSeconds;

    @ConfigProperty(name = "jwt.private-key-location")
    String privateKeyLocation;

    public String issue(User user) {
        Instant issuedAt = Instant.now();
        Set<String> roles = user.role == br.com.bolaoboladao.users.domain.UserRole.ADMIN
                ? Set.of("USER", "ADMIN")
                : Set.of("USER");
        return Jwt.issuer(issuer)
                .audience(audience)
                .subject(user.id.toString())
                .preferredUserName(user.username)
                .groups(roles)
                .claim("roles", roles)
                .claim("jti", UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(expirationSeconds))
                .jws()
                .algorithm(SignatureAlgorithm.RS256)
                .sign(signingKey());
    }

    private PrivateKey signingKey() {
        try {
            return KeyUtils.decodePrivateKey(
                    ResourceUtils.readResource(privateKeyLocation),
                    SignatureAlgorithm.RS256);
        } catch (Exception exception) {
            throw new IllegalStateException("Não foi possível carregar a chave privada JWT", exception);
        }
    }
}
