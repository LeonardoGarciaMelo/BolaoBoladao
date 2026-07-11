package br.com.bolaoboladao.partidas;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class MatchResourceTest {

    @Test
    void deveCriarPartidaIniciarMarcarGolEEncerrar() {
        String start = LocalDateTime.now().plusHours(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        String body = """
                {
                  "teamHomeName": "Flamengo",
                  "teamAwayName": "Corinthians",
                  "start": "%s"
                }
                """.formatted(start);

        String matchId = given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/partidas")
                .then()
                .statusCode(201)
                .body("teamHome", equalTo("Flamengo"))
                .body("teamAway", equalTo("Corinthians"))
                .body("status", equalTo("SCHEDULED"))
                .extract().path("id");

        given()
                .when().post("/partidas/{id}/iniciar", matchId)
                .then()
                .statusCode(200)
                .body("status", equalTo("IN_PROGRESS"));

        given()
                .contentType(ContentType.JSON)
                .body("{\"side\": \"HOME\"}")
                .when().post("/partidas/{id}/gol", matchId)
                .then()
                .statusCode(200)
                .body("teamHomeScore", equalTo(1))
                .body("teamAwayScore", equalTo(0));

        given()
                .when().post("/partidas/{id}/encerrar", matchId)
                .then()
                .statusCode(200)
                .body("status", equalTo("FINISHED"))
                .body("end", notNullValue());

        given()
                .when().get("/partidas/{id}/eventos", matchId)
                .then()
                .statusCode(200)
                .body("size()", is(3))
                .body("[0].eventType", equalTo("MATCH_STARTED"))
                .body("[1].eventType", equalTo("TEAM_HOME_SCORED"))
                .body("[2].eventType", equalTo("MATCH_ENDED"));
    }

    @Test
    void naoDeveMarcarGolEmPartidaNaoIniciada() {
        String start = LocalDateTime.now().plusHours(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        String body = """
                {
                  "teamHomeName": "Palmeiras",
                  "teamAwayName": "São Paulo",
                  "start": "%s"
                }
                """.formatted(start);

        String matchId = given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/partidas")
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .contentType(ContentType.JSON)
                .body("{\"side\": \"HOME\"}")
                .when().post("/partidas/{id}/gol", matchId)
                .then()
                .statusCode(409);
    }

    @Test
    void deveRetornar404ParaPartidaInexistente() {
        given()
                .when().get("/partidas/{id}", "00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404);
    }
}
