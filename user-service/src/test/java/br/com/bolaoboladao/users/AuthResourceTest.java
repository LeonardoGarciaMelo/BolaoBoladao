package br.com.bolaoboladao.users;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.util.KeyUtils;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AuthResourceTest {

    private static final String REGISTER_BIA = """
            {"name":"Beatriz Costa","username":"bia","password":"senha-segura-123"}
            """;

    @Test
    void deveCadastrarUsuarioEEmitirTokenNoLogin() throws Exception {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name":"Ana Silva","username":"ana","password":"senha-segura-123"}
                        """)
                .when().post("/auth/register")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("name", equalTo("Ana Silva"))
                .body("username", equalTo("ana"));

        String token = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"username":"ana","password":"senha-segura-123"}
                        """)
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("tokenType", equalTo("Bearer"))
                .body("expiresIn", equalTo(3600))
                .extract().path("accessToken");

        JsonWebSignature signature = new JsonWebSignature();
        signature.setCompactSerialization(token);
        signature.setKey(KeyUtils.readPublicKey("publicKey.pem", SignatureAlgorithm.RS256));
        assertTrue(signature.verifySignature());

        JwtClaims claims = JwtClaims.parse(signature.getPayload());
        assertFalse(claims.getSubject().isBlank());
        assertTrue(claims.getAudience().contains("bolao-api"));
        assertTrue(claims.getIssuedAt().getValue() > 0);
        assertTrue(claims.getExpirationTime().getValue() > claims.getIssuedAt().getValue());
        assertFalse(claims.getJwtId().isBlank());
        org.junit.jupiter.api.Assertions.assertEquals("ana", claims.getStringClaimValue("preferred_username"));
        org.junit.jupiter.api.Assertions.assertEquals(List.of("USER"), claims.getStringListClaimValue("roles"));
        org.junit.jupiter.api.Assertions.assertEquals("bolao-user-service", claims.getIssuer());
    }

    @Test
    void deveRecusarUsernameDuplicado() {
        given().contentType(ContentType.JSON).body(REGISTER_BIA)
                .when().post("/auth/register")
                .then().statusCode(201);

        given().contentType(ContentType.JSON).body(REGISTER_BIA)
                .when().post("/auth/register")
                .then().statusCode(409);
    }

    @Test
    void deveValidarCadastroERecusarCredenciaisInvalidas() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name":"A","username":"x","password":"curta"}
                        """)
                .when().post("/auth/register")
                .then().statusCode(400);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"username":"desconhecido","password":"senha-segura-123"}
                        """)
                .when().post("/auth/login")
                .then().statusCode(401);
    }
}
