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
@PactTestFor(providerName = "apostas-service")
public class ApostasConsumerPactTest {

    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String USER_ID = "22121193-3c26-4c26-812d-123456789012";
    private static final String MATCH_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    private static final Instant EXAMPLE_DATETIME = Instant.parse("2026-07-14T12:00:00Z");

    private static PactDslJsonBody betResponseBody() {
        return new PactDslJsonBody()
                .uuid("bet_id")
                .uuid("user_id")
                .uuid("match_id")
                .integerType("home_team_goals", 2)
                .integerType("away_team_goals", 1)
                .decimalType("stake_amount", 50.00)
                .stringValue("status", "CREATED")
                .datetime("created_at", DATETIME_FORMAT, EXAMPLE_DATETIME);
    }

    @Pact(consumer = "api-gateway")
    public V4Pact listBetsPact(PactDslWithProvider builder) {
        return builder
                .given("user " + USER_ID + " has placed bets")
                .uponReceiving("a request to list bets for the authenticated user")
                .method("GET")
                .path("/bets")
                .headers(Map.of("X-Authenticated-User-Id", USER_ID))
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", CONTENT_TYPE_JSON))
                .body(LambdaDsl.newJsonArray(array ->
                        array.object(bet -> {
                            bet.uuid("bet_id");
                            bet.uuid("user_id");
                            bet.uuid("match_id");
                            bet.integerType("home_team_goals", 2);
                            bet.integerType("away_team_goals", 1);
                            bet.decimalType("stake_amount", 50.00);
                            bet.stringValue("status", "CREATED");
                            bet.datetime("created_at", DATETIME_FORMAT, EXAMPLE_DATETIME);
                        })).build())
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "listBetsPact")
    public void testListBets(MockServer mockServer) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .GET()
                        .uri(URI.create(mockServer.getUrl() + "/bets"))
                        .header("X-Authenticated-User-Id", USER_ID)
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().startsWith("["));
    }

    @Pact(consumer = "api-gateway")
    public V4Pact createBetPact(PactDslWithProvider builder) {
        return builder
                .given("match " + MATCH_ID + " is in progress and user " + USER_ID + " has sufficient balance")
                .uponReceiving("a request to place a bet on a match")
                .method("POST")
                .path("/bets")
                .headers(Map.of("Content-Type", CONTENT_TYPE_JSON, "X-Authenticated-User-Id", USER_ID))
                .body(new PactDslJsonBody()
                        .uuid("match_id", MATCH_ID)
                        .integerType("home_team_goals", 2)
                        .integerType("away_team_goals", 1)
                        .decimalType("stake_amount", 50.00))
                .willRespondWith()
                .status(201)
                .headers(Map.of("Content-Type", CONTENT_TYPE_JSON))
                .body(betResponseBody())
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "createBetPact")
    public void testCreateBet(MockServer mockServer) throws Exception {
        String body = String.format(
                "{\"match_id\":\"%s\",\"home_team_goals\":2,\"away_team_goals\":1,\"stake_amount\":50.00}",
                MATCH_ID);

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .uri(URI.create(mockServer.getUrl() + "/bets"))
                        .header("Content-Type", CONTENT_TYPE_JSON)
                        .header("X-Authenticated-User-Id", USER_ID)
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(201, response.statusCode());
        assertTrue(response.body().contains("\"bet_id\""));
        assertTrue(response.body().contains("\"CREATED\""));
    }
}
