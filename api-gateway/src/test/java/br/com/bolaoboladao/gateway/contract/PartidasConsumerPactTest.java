package br.com.bolaoboladao.gateway.contract;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.LambdaDsl;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "partidas-service")
public class PartidasConsumerPactTest {

    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String USER_ID = "22121193-3c26-4c26-812d-123456789012";
    private static final String MATCH_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    private static final Instant EXAMPLE_DATETIME = Instant.parse("2026-12-01T20:00:00Z");

    private static PactDslJsonBody matchResponseBody() {
        return new PactDslJsonBody()
                .uuid("id")
                .stringType("teamHome", "Aurora")
                .stringType("teamAway", "Estrela")
                .integerType("teamHomeScore", 0)
                .integerType("teamAwayScore", 0)
                .datetime("start", DATETIME_FORMAT, EXAMPLE_DATETIME)
                .stringMatcher("status", "SCHEDULED|IN_PROGRESS|FINISHED|CANCELED", "SCHEDULED");
    }

    @Pact(consumer = "api-gateway")
    public V4Pact listMatchesPact(PactDslWithProvider builder) {
        return builder
                .given("at least one match exists")
                .uponReceiving("a request to list all matches")
                .method("GET")
                .path("/partidas")
                .headers(Map.of("X-Authenticated-User-Id", USER_ID))
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", CONTENT_TYPE_JSON))
                .body(LambdaDsl.newJsonArray(array ->
                        array.object(match -> {
                            match.uuid("id");
                            match.stringType("teamHome", "Aurora");
                            match.stringType("teamAway", "Estrela");
                            match.integerType("teamHomeScore", 0);
                            match.integerType("teamAwayScore", 0);
                            match.datetime("start", DATETIME_FORMAT, EXAMPLE_DATETIME);
                            match.stringMatcher("status", "SCHEDULED|IN_PROGRESS|FINISHED|CANCELED", "SCHEDULED");
                        })).build())
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "listMatchesPact")
    public void testListMatches(MockServer mockServer) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .GET()
                        .uri(URI.create(mockServer.getUrl() + "/partidas"))
                        .header("X-Authenticated-User-Id", USER_ID)
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().startsWith("["));
    }

    @Pact(consumer = "api-gateway")
    public V4Pact getMatchByIdPact(PactDslWithProvider builder) {
        return builder
                .given("match with id " + MATCH_ID + " exists")
                .uponReceiving("a request to get a match by id")
                .method("GET")
                .path("/partidas/" + MATCH_ID)
                .headers(Map.of("X-Authenticated-User-Id", USER_ID))
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", CONTENT_TYPE_JSON))
                .body(matchResponseBody())
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "getMatchByIdPact")
    public void testGetMatchById(MockServer mockServer) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .GET()
                        .uri(URI.create(mockServer.getUrl() + "/partidas/" + MATCH_ID))
                        .header("X-Authenticated-User-Id", USER_ID)
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"teamHome\""));
        assertTrue(response.body().contains("\"status\""));
    }

    @Pact(consumer = "api-gateway")
    public V4Pact createMatchPact(PactDslWithProvider builder) {
        return builder
                .given("teams Aurora and Estrela exist")
                .uponReceiving("a request to create a new match")
                .method("POST")
                .path("/partidas")
                .headers(Map.of("Content-Type", CONTENT_TYPE_JSON, "X-Authenticated-User-Id", USER_ID))
                .body(new PactDslJsonBody()
                        .stringType("teamHomeName", "Aurora")
                        .stringType("teamAwayName", "Estrela")
                        .datetime("start", DATETIME_FORMAT, EXAMPLE_DATETIME))
                .willRespondWith()
                .status(201)
                .headers(Map.of("Content-Type", CONTENT_TYPE_JSON))
                .body(matchResponseBody())
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "createMatchPact")
    public void testCreateMatch(MockServer mockServer) throws Exception {
        String body = "{\"teamHomeName\":\"Aurora\",\"teamAwayName\":\"Estrela\",\"start\":\"2026-12-01T20:00:00\"}";

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .uri(URI.create(mockServer.getUrl() + "/partidas"))
                        .header("Content-Type", CONTENT_TYPE_JSON)
                        .header("X-Authenticated-User-Id", USER_ID)
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(201, response.statusCode());
        assertTrue(response.body().contains("\"teamHome\""));
        assertTrue(response.body().contains("SCHEDULED"));
    }
}
