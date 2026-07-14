package br.com.bolaoboladao.users;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.util.KeyUtils;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AuthResourceTest {

    private static final String SUFFIX = UUID.randomUUID().toString().substring(0, 8);
    private static final String ANA_USERNAME = "ana-" + SUFFIX;
    private static final String BIA_USERNAME = "bia-" + SUFFIX;
    private static final String REGISTER_BIA = """
            {"name":"Beatriz Costa","username":"%s","password":"senha-segura-123"}
            """.formatted(BIA_USERNAME);

    @Test
    void deveCadastrarUsuarioEEmitirTokenNoLogin() throws Exception {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name":"Ana Silva","username":"%s","password":"senha-segura-123"}
                        """.formatted(ANA_USERNAME))
                .when().post("/auth/register")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("name", equalTo("Ana Silva"))
                .body("username", equalTo(ANA_USERNAME));

        String token = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"username":"%s","password":"senha-segura-123"}
                        """.formatted(ANA_USERNAME))
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
        org.junit.jupiter.api.Assertions.assertEquals(ANA_USERNAME, claims.getStringClaimValue("preferred_username"));
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

    @Test
    void deveEmitirRoleAdminEExporIdentidadeVerificada() {
        String token = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"username":"admin","password":"admin-seguro-123"}
                        """)
                .when().post("/auth/login")
                .then().statusCode(200)
                .extract().path("accessToken");

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/auth/me")
                .then().statusCode(200)
                .body("username", equalTo("admin"))
                .body("roles", hasItems("USER", "ADMIN"));

        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("q", "admin")
                .when().get("/admin/users")
                .then().statusCode(200)
                .body("items[0].username", equalTo("admin"));
    }
}
