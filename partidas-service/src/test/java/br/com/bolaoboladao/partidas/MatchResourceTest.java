package br.com.bolaoboladao.partidas;

import br.com.bolaoboladao.partidas.cache.MatchCache;
import br.com.bolaoboladao.partidas.dto.MatchResponse;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.util.KeyUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class MatchResourceTest {

    private static final String ADMIN_ID = "22121193-3c26-4c26-812d-123456789012";

    @Inject
    MatchCache matchCache;

    @Test
    void deveCriarPartidaIniciarMarcarGolEEncerrar() {
        String start = OffsetDateTime.now().plusHours(1).toString();

        String body = """
                {
                  "teamHomeName": "Flamengo",
                  "teamAwayName": "Corinthians",
                  "start": "%s"
                }
                """.formatted(start);

        String matchId = given()
                .header("Authorization", "Bearer " + adminToken())
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/admin/partidas")
                .then()
                .statusCode(201)
                .body("teamHome", equalTo("Flamengo"))
                .body("teamAway", equalTo("Corinthians"))
                .body("status", equalTo("SCHEDULED"))
                .extract().path("id");

        given()
                .header("Authorization", "Bearer " + adminToken())
                .when().post("/admin/partidas/{id}/iniciar", matchId)
                .then()
                .statusCode(200)
                .body("status", equalTo("IN_PROGRESS"));

        given()
                .header("Authorization", "Bearer " + adminToken())
                .contentType(ContentType.JSON)
                .body("{\"side\": \"HOME\"}")
                .when().post("/admin/partidas/{id}/gol", matchId)
                .then()
                .statusCode(200)
                .body("teamHomeScore", equalTo(1))
                .body("teamAwayScore", equalTo(0));

        given()
                .header("Authorization", "Bearer " + adminToken())
                .when().post("/admin/partidas/{id}/encerrar", matchId)
                .then()
                .statusCode(200)
                .body("status", equalTo("FINISHED"))
                .body("end", notNullValue());

        given()
                .when().get("/partidas/{id}/eventos", matchId)
                .then()
                .statusCode(200)
                .body("size()", is(4))
                .body("[0].eventType", equalTo("MATCH_CREATED"))
                .body("[1].eventType", equalTo("MATCH_STARTED"))
                .body("[2].eventType", equalTo("TEAM_HOME_SCORED"))
                .body("[3].eventType", equalTo("MATCH_ENDED"));
    }

    @Test
    void naoDeveMarcarGolEmPartidaNaoIniciada() {
        String start = OffsetDateTime.now().plusHours(1).toString();

        String body = """
                {
                  "teamHomeName": "Palmeiras",
                  "teamAwayName": "São Paulo",
                  "start": "%s"
                }
                """.formatted(start);

        String matchId = given()
                .header("Authorization", "Bearer " + adminToken())
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/admin/partidas")
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .header("Authorization", "Bearer " + adminToken())
                .contentType(ContentType.JSON)
                .body("{\"side\": \"HOME\"}")
                .when().post("/admin/partidas/{id}/gol", matchId)
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
    void catalogoListaPartidasAbertasComPaginacaoEJanelaDePalpite() {
        String id = createMatch("Catálogo A", "Catálogo B");

        given()
                .when().get("/partidas/catalog?view=OPEN&page=0&size=50")
                .then()
                .statusCode(200)
                .body("page", equalTo(0))
                .body("size", equalTo(50))
                .body("items.find { it.id == '%s' }.bettingOpen".formatted(id), equalTo(true))
                .body("items.find { it.id == '%s' }.status".formatted(id), equalTo("SCHEDULED"));
    }

    @Test
    void catalogoFiltraEstadosOrdenaEPagina() {
        String live = createMatch("Catálogo Ao Vivo A", "Catálogo Ao Vivo B");
        given().header("Authorization", "Bearer " + adminToken())
                .when().post("/admin/partidas/{id}/iniciar", live).then().statusCode(200);

        String finished = createMatch("Catálogo Encerrada A", "Catálogo Encerrada B");
        given().header("Authorization", "Bearer " + adminToken())
                .when().post("/admin/partidas/{id}/iniciar", finished).then().statusCode(200);
        given().header("Authorization", "Bearer " + adminToken())
                .when().post("/admin/partidas/{id}/encerrar", finished).then().statusCode(200);

        String canceled = createMatch("Catálogo Cancelada A", "Catálogo Cancelada B");
        given().header("Authorization", "Bearer " + adminToken())
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(ContentType.JSON)
                .body("{\"reason\":\"Cancelamento para testar o catálogo\"}")
                .when().post("/admin/partidas/{id}/cancel", canceled).then().statusCode(202);

        given().when().get("/partidas/catalog?view=LIVE&page=0&size=50").then().statusCode(200)
                .body("items.find { it.id == '%s' }.status".formatted(live), equalTo("IN_PROGRESS"));
        given().when().get("/partidas/catalog?view=FINISHED&page=0&size=50").then().statusCode(200)
                .body("items.find { it.id == '%s' }.status".formatted(finished), equalTo("FINISHED"));
        given().when().get("/partidas/catalog?view=CANCELED&page=0&size=50").then().statusCode(200)
                .body("items.find { it.id == '%s' }.status".formatted(canceled), equalTo("CANCELED"));

        var openResponse = given().when().get("/partidas/catalog?view=OPEN&page=0&size=1")
                .then().statusCode(200).body("page", equalTo(0)).body("size", equalTo(1))
                .body("items.size()", equalTo(1)).extract();
        org.junit.jupiter.api.Assertions.assertTrue(openResponse.path("total") instanceof Number);

        java.util.List<String> starts = given().when().get("/partidas/catalog?view=OPEN&page=0&size=50")
                .then().statusCode(200).extract().jsonPath().getList("items.start", String.class);
        var parsedStarts = starts.stream().map(OffsetDateTime::parse).toList();
        org.junit.jupiter.api.Assertions.assertEquals(parsedStarts.stream().sorted().toList(), parsedStarts);

        given().when().get("/partidas/catalog?view=UNKNOWN").then().statusCode(400);
    }

    @Test
    void deveTestarCacheEOutbox() throws InterruptedException {
        String start = OffsetDateTime.now().plusHours(1).toString();
        String body = """
                {
                  "teamHomeName": "Santos",
                  "teamAwayName": "Vasco",
                  "start": "%s"
                }
                """.formatted(start);

        String matchIdStr = given()
                .header("Authorization", "Bearer " + adminToken())
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/admin/partidas")
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
                .header("Authorization", "Bearer " + adminToken())
                .when().post("/admin/partidas/{id}/iniciar", matchIdStr)
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

    @Test
    void deveCancelarUmaUnicaVezERecusarPartidaEncerrada() {
        String id = createMatch("  Aurora  ", "Estrela");
        String key = UUID.randomUUID().toString();
        String reason = "Cancelamento administrativo válido";

        given().header("Authorization", "Bearer " + adminToken())
                .header("Idempotency-Key", key).contentType(ContentType.JSON)
                .body("{\"reason\":\"" + reason + "\"}")
                .when().post("/admin/partidas/{id}/cancel", id)
                .then().statusCode(202).body("status", equalTo("CANCELED"));

        given().header("Authorization", "Bearer " + adminToken())
                .header("Idempotency-Key", key).contentType(ContentType.JSON)
                .body("{\"reason\":\"" + reason + "\"}")
                .when().post("/admin/partidas/{id}/cancel", id)
                .then().statusCode(202);

        given().when().get("/partidas/{id}/eventos", id).then()
                .body("findAll { it.eventType == 'MATCH_CANCELED' }.size()", equalTo(1));

        String finished = createMatch("Norte", "Sul");
        given().header("Authorization", "Bearer " + adminToken())
                .when().post("/admin/partidas/{id}/iniciar", finished).then().statusCode(200);
        given().header("Authorization", "Bearer " + adminToken())
                .when().post("/admin/partidas/{id}/encerrar", finished).then().statusCode(200);
        given().header("Authorization", "Bearer " + adminToken())
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(ContentType.JSON)
                .body("{\"reason\":\"Cancelamento não permitido\"}")
                .when().post("/admin/partidas/{id}/cancel", finished).then().statusCode(409);
    }

    @Test
    void cancelamentosConcorrentesGeramUmUnicoEvento() throws Exception {
        String id = createMatch("Concorrente A", "Concorrente B");
        String key = UUID.randomUUID().toString();
        String token = adminToken();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var cancel = (java.util.concurrent.Callable<Integer>) () -> {
                start.await();
                return given().header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key).contentType(ContentType.JSON)
                        .body("{\"reason\":\"Cancelamento concorrente válido\"}")
                        .when().post("/admin/partidas/{id}/cancel", id).statusCode();
            };
            var first = executor.submit(cancel);
            var second = executor.submit(cancel);
            start.countDown();
            org.junit.jupiter.api.Assertions.assertEquals(202, first.get());
            org.junit.jupiter.api.Assertions.assertEquals(202, second.get());
        }
        given().when().get("/partidas/{id}/eventos", id).then()
                .body("findAll { it.eventType == 'MATCH_CANCELED' }.size()", equalTo(1));
    }

    @Test
    void criacoesConcorrentesComTimesInvertidosNaoDuplicamTimes() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String firstTeam = "Time A " + suffix;
        String secondTeam = "Time B " + suffix;
        String token = adminToken();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var createFirst = executor.submit(() -> {
                start.await();
                return createMatchWithToken(firstTeam, secondTeam, token);
            });
            var createSecond = executor.submit(() -> {
                start.await();
                return createMatchWithToken(secondTeam, firstTeam, token);
            });
            start.countDown();
            org.junit.jupiter.api.Assertions.assertEquals(201, createFirst.get());
            org.junit.jupiter.api.Assertions.assertEquals(201, createSecond.get());
        }
        var teams = io.quarkus.arc.Arc.container()
                .instance(br.com.bolaoboladao.partidas.repository.TeamRepository.class).get();
        org.junit.jupiter.api.Assertions.assertEquals(2,
                teams.count("lower(name) = ?1 or lower(name) = ?2",
                        firstTeam.toLowerCase(), secondTeam.toLowerCase()));
    }

    private String createMatch(String home, String away) {
        return given().header("Authorization", "Bearer " + adminToken())
                .contentType(ContentType.JSON)
                .body("{\"teamHomeName\":\"" + home + "\",\"teamAwayName\":\"" + away
                        + "\",\"start\":\"" + OffsetDateTime.now().plusHours(2) + "\"}")
                .when().post("/admin/partidas").then().statusCode(201).extract().path("id");
    }

    private int createMatchWithToken(String home, String away, String token) {
        return given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"teamHomeName\":\"" + home + "\",\"teamAwayName\":\"" + away
                        + "\",\"start\":\"" + OffsetDateTime.now().plusHours(2) + "\"}")
                .when().post("/admin/partidas").statusCode();
    }

    private String adminToken() {
        try {
            return Jwt.issuer("bolao-user-service").audience("bolao-api").subject(ADMIN_ID)
                    .groups(Set.of("USER", "ADMIN")).issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(300)).jws().algorithm(SignatureAlgorithm.RS256)
                    .sign(KeyUtils.readPrivateKey("privateKey.pem", SignatureAlgorithm.RS256));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
