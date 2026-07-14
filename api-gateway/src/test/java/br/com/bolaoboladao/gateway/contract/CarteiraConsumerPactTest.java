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
@PactTestFor(providerName = "carteira-service")
public class CarteiraConsumerPactTest {

    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String USER_ID = "22121193-3c26-4c26-812d-123456789012";
    private static final String DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    private static final Instant EXAMPLE_DATETIME = Instant.parse("2026-07-14T12:00:00Z");

    @Pact(consumer = "api-gateway")
    public V4Pact getWalletBalancePact(PactDslWithProvider builder) {
        return builder
                .given("wallet exists for user " + USER_ID)
                .uponReceiving("a request to get the wallet balance")
                .method("GET")
                .path("/wallet/" + USER_ID + "/balance")
                .headers(Map.of("X-Authenticated-User-Id", USER_ID))
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", CONTENT_TYPE_JSON))
                .body(new PactDslJsonBody()
                        .decimalType("balance", 150.00))
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "getWalletBalancePact")
    public void testGetWalletBalance(MockServer mockServer) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .GET()
                        .uri(URI.create(mockServer.getUrl() + "/wallet/" + USER_ID + "/balance"))
                        .header("X-Authenticated-User-Id", USER_ID)
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"balance\""));
    }

    @Pact(consumer = "api-gateway")
    public V4Pact getWalletStatementPact(PactDslWithProvider builder) {
        return builder
                .given("wallet with id " + USER_ID + " has ledger entries")
                .uponReceiving("a request to get the wallet statement")
                .method("GET")
                .path("/wallet/" + USER_ID + "/statement")
                .headers(Map.of("X-Authenticated-User-Id", USER_ID))
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", CONTENT_TYPE_JSON))
                .body(LambdaDsl.newJsonArray(array ->
                        array.object(entry -> {
                            entry.uuid("id");
                            entry.stringType("reason", "BET_PLACED");
                            entry.stringMatcher("operation", "CREDIT|DEBIT", "DEBIT");
                            entry.decimalType("amount", 50.00);
                            entry.datetime("occurredAt", DATETIME_FORMAT, EXAMPLE_DATETIME);
                        })).build())
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "getWalletStatementPact")
    public void testGetWalletStatement(MockServer mockServer) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .GET()
                        .uri(URI.create(mockServer.getUrl() + "/wallet/" + USER_ID + "/statement"))
                        .header("X-Authenticated-User-Id", USER_ID)
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().startsWith("["));
    }
}
