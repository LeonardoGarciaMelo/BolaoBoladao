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
class CarteiraGatewaySecurityTest {

    @RegisterExtension
    static final CarteiraBackendStub CARTEIRA_BACKEND = new CarteiraBackendStub();

    @Test
    void deveRecusarCarteiraSemToken() {
        given()
                .when().get("/api/wallet/{userId}/balance", CarteiraBackendStub.AUTHENTICATED_USER_ID)
                .then()
                .statusCode(401);
    }

    @Test
    void deveEncaminharCarteiraComIdentidadeDoTokenEQueryString() throws Exception {
        given()
                .header("Authorization", "Bearer " + token())
                .header("X-Authenticated-User-Id", "forged")
                .queryParam("includePending", true)
                .when().get("/api/wallet/{userId}/balance", CarteiraBackendStub.AUTHENTICATED_USER_ID)
                .then()
                .statusCode(200)
                .body("forwarded", equalTo(true));
    }

    @Test
    void devePreservarRespostaSemCorpoDaCarteira() throws Exception {
        given()
                .header("Authorization", "Bearer " + token())
                .when().get("/api/wallet/forbidden/balance")
                .then()
                .statusCode(403);
    }

    private String token() throws Exception {
        return Jwt.issuer("bolao-user-service")
                .audience("bolao-api")
                .subject(CarteiraBackendStub.AUTHENTICATED_USER_ID)
                .groups(Set.of("USER"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .jws().algorithm(SignatureAlgorithm.RS256)
                .sign(KeyUtils.readPrivateKey("privateKey.pem", SignatureAlgorithm.RS256));
    }
}
