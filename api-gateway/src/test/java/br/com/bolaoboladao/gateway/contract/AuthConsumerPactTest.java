package br.com.bolaoboladao.gateway.contract;

import au.com.dius.pact.consumer.MockServer;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "user-service")
public class AuthConsumerPactTest {

    private static final String CONTENT_TYPE_JSON = "application/json";

    @Pact(consumer = "api-gateway")
    public V4Pact registerPact(PactDslWithProvider builder) {
        return builder
                .given("username joaosilva is available")
                .uponReceiving("a register request with valid data")
                .method("POST")
                .path("/auth/register")
                .headers(Map.of("Content-Type", CONTENT_TYPE_JSON))
                .body(new PactDslJsonBody()
                        .stringType("name", "João Silva")
                        .stringType("username", "joaosilva")
                        .stringType("password", "senhasegura123"))
                .willRespondWith()
                .status(201)
                .headers(Map.of("Content-Type", CONTENT_TYPE_JSON))
                .body(new PactDslJsonBody()
                        .uuid("id")
                        .stringType("name", "João Silva")
                        .stringType("username", "joaosilva"))
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "registerPact")
    public void testRegister(MockServer mockServer) throws Exception {
        String body = "{\"name\":\"João Silva\",\"username\":\"joaosilva\",\"password\":\"senhasegura123\"}";

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .uri(URI.create(mockServer.getUrl() + "/auth/register"))
                        .header("Content-Type", CONTENT_TYPE_JSON)
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(201, response.statusCode());
        assertTrue(response.body().contains("\"username\""));
    }

    @Pact(consumer = "api-gateway")
    public V4Pact loginPact(PactDslWithProvider builder) {
        return builder
                .given("user joaosilva exists with correct password")
                .uponReceiving("a login request with valid credentials")
                .method("POST")
                .path("/auth/login")
                .headers(Map.of("Content-Type", CONTENT_TYPE_JSON))
                .body(new PactDslJsonBody()
                        .stringType("username", "joaosilva")
                        .stringType("password", "senhasegura123"))
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", CONTENT_TYPE_JSON))
                .body(new PactDslJsonBody()
                        .stringType("accessToken")
                        .stringValue("tokenType", "Bearer")
                        .integerType("expiresIn"))
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "loginPact")
    public void testLogin(MockServer mockServer) throws Exception {
        String body = "{\"username\":\"joaosilva\",\"password\":\"senhasegura123\"}";

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .uri(URI.create(mockServer.getUrl() + "/auth/login"))
                        .header("Content-Type", CONTENT_TYPE_JSON)
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"accessToken\""));
        assertTrue(response.body().contains("\"Bearer\""));
    }
}
