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
class PartidasGatewaySecurityTest {

    @RegisterExtension
    static final PartidasBackendStub PARTIDAS_BACKEND = new PartidasBackendStub();

    @Test
    void deveRecusarPartidasSemToken() {
        given()
                .when().get("/api/partidas")
                .then()
                .statusCode(401);
    }

    @Test
    void deveExporDestaquesDePalpitesSemToken() {
        given()
                .when().get("/api/palpites/destaques")
                .then()
                .statusCode(204);
    }

    @Test
    void deveRecusarTokenMalformado() {
        given()
                .header("Authorization", "Bearer nao-e-um-jwt")
                .when().get("/api/partidas")
                .then()
                .statusCode(401);
    }

    @Test
    void deveAceitarTokenAssinadoComIssuerEAudienciaEsperados() throws Exception {
        String token = token("bolao-user-service", "bolao-api", Instant.now().plusSeconds(300), Set.of("USER"));

        given()
                .header("Authorization", "Bearer " + token)
                .header("X-Authenticated-User-Id", "forged")
                .when().get("/api/partidas")
                .then()
                .statusCode(200)
                .body(equalTo("[]"));
    }

    @Test
    void deveRecusarTokenExpiradoComIssuerOuAudienciaIncorretos() throws Exception {
        given().header("Authorization", "Bearer " + token("bolao-user-service", "bolao-api", Instant.now().minusSeconds(1), Set.of("USER")))
                .when().get("/api/partidas").then().statusCode(401);
        given().header("Authorization", "Bearer " + token("issuer-errado", "bolao-api", Instant.now().plusSeconds(300), Set.of("USER")))
                .when().get("/api/partidas").then().statusCode(401);
        given().header("Authorization", "Bearer " + token("bolao-user-service", "audiencia-errada", Instant.now().plusSeconds(300), Set.of("USER")))
                .when().get("/api/partidas").then().statusCode(401);
    }

    @Test
    void deveReservarMutacoesParaRotasAdministrativas() throws Exception {
        String userToken = token("bolao-user-service", "bolao-api", Instant.now().plusSeconds(300), Set.of("USER"));
        String adminToken = token("bolao-user-service", "bolao-api", Instant.now().plusSeconds(300), Set.of("USER", "ADMIN"));

        given().header("Authorization", "Bearer " + userToken)
                .when().get("/api/admin/partidas").then().statusCode(403);
        given().header("Authorization", "Bearer " + adminToken)
                .when().get("/api/admin/partidas").then().statusCode(200);
        given().header("Authorization", "Bearer " + userToken)
                .contentType("application/json").body("{}")
                .when().post("/api/partidas").then().statusCode(405);
    }

    private String token(String issuer, String audience, Instant expiresAt, Set<String> groups) throws Exception {
        return Jwt.issuer(issuer)
                .audience(audience)
                .subject("22121193-3c26-4c26-812d-123456789012")
                .groups(groups)
                .issuedAt(Instant.now())
                .expiresAt(expiresAt)
                .jws().algorithm(SignatureAlgorithm.RS256)
                .sign(KeyUtils.readPrivateKey("privateKey.pem", SignatureAlgorithm.RS256));
    }
}
