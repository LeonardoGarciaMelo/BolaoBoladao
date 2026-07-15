package br.com.bolaoboladao.gateway;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.util.KeyUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Instant;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class ApostasGatewaySecurityTest {
    @RegisterExtension
    static final ApostasBackendStub BACKEND = new ApostasBackendStub();

    @Test
    void deveEncaminharIdentidadeConfiavelEChaveIdempotente() throws Exception {
        given().header("Authorization", "Bearer " + token())
                .header("X-Authenticated-User-Id", "forged")
                .header("Idempotency-Key", "palpite-key")
                .contentType("application/json").body("{}")
                .when().post("/api/bets")
                .then().statusCode(201)
                .body("identity", equalTo(ApostasBackendStub.USER_ID))
                .body("idempotencyKey", equalTo("palpite-key"));
    }

    private String token() throws Exception {
        return Jwt.issuer("bolao-user-service").audience("bolao-api")
                .subject(ApostasBackendStub.USER_ID).groups(Set.of("USER"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                .jws().algorithm(SignatureAlgorithm.RS256)
                .sign(KeyUtils.readPrivateKey("privateKey.pem", SignatureAlgorithm.RS256));
    }
}
