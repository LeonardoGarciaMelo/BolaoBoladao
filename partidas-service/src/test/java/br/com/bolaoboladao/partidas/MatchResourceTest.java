package br.com.bolaoboladao.partidas;

import br.com.bolaoboladao.partidas.cache.MatchCache;
import br.com.bolaoboladao.partidas.dto.MatchResponse;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class MatchResourceTest {

    @Inject
    MatchCache matchCache;

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

    @Test
    void deveTestarCacheEOutbox() throws InterruptedException {
        String start = LocalDateTime.now().plusHours(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String body = """
                {
                  "teamHomeName": "Santos",
                  "teamAwayName": "Vasco",
                  "start": "%s"
                }
                """.formatted(start);

        String matchIdStr = given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/partidas")
                .then()
                .statusCode(201)
                .extract().path("id");
        UUID matchId = UUID.fromString(matchIdStr);

        // 1. Verificar que o cache está vazio inicialmente
        java.util.Optional<MatchResponse> cachedBefore = matchCache.get(matchId);
        org.junit.jupiter.api.Assertions.assertTrue(cachedBefore.isEmpty(), "Cache deve estar vazio antes de buscar");

        // 2. Fazer GET para preencher o cache (Miss seguido de Put)
        MatchResponse response = given()
                .when().get("/partidas/{id}", matchIdStr)
                .then()
                .statusCode(200)
                .extract().as(MatchResponse.class);

        // 3. Verificar que o cache agora está populado
        java.util.Optional<MatchResponse> cachedAfter = matchCache.get(matchId);
        org.junit.jupiter.api.Assertions.assertTrue(cachedAfter.isPresent(), "Cache deve ter sido populado");
        org.junit.jupiter.api.Assertions.assertEquals("Santos", cachedAfter.get().teamHome());

        // 4. Iniciar a partida (deve invalidar cache e registrar evento)
        given()
                .when().post("/partidas/{id}/iniciar", matchIdStr)
                .then()
                .statusCode(200);

        // 5. Verificar que o cache foi invalidado (Evict)
        java.util.Optional<MatchResponse> cachedAfterUpdate = matchCache.get(matchId);
        org.junit.jupiter.api.Assertions.assertTrue(cachedAfterUpdate.isEmpty(), "Cache deve ter sido invalidado no update");

        // 6. Testar o Outbox: aguardar o scheduler publicar e marcar como publicado
        boolean outboxProcessado = false;
        for (int i = 0; i < 15; i++) {
            Thread.sleep(500);
            Long unpublishedCount = io.quarkus.arc.Arc.container().instance(br.com.bolaoboladao.partidas.repository.MatchEventRepository.class).get()
                    .count("match.id = ?1 and published = false", matchId);
            if (unpublishedCount == 0) {
                outboxProcessado = true;
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(outboxProcessado, "Eventos do outbox devem ser marcados como publicado = true após processamento do scheduler");
    }
}
