package br.com.bolaoboladao.carteira.infrastructure.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class PaymentProviderClient {
    @Inject Vertx vertx;
    @Inject ObjectMapper objectMapper;

    @ConfigProperty(name = "payment.provider.url") String providerUrl;
    @ConfigProperty(name = "payment.merchant.api-key", defaultValue = "") String configuredApiKey;
    @ConfigProperty(name = "payment.merchant.api-key-file", defaultValue = "") String apiKeyFile;

    private WebClient client;
    private String apiKey;

    @PostConstruct
    void initialize() {
        client = WebClient.create(vertx);
        apiKey = readSecret(configuredApiKey, apiKeyFile);
        if (apiKey.isBlank()) {
            throw new IllegalStateException("Payment merchant API key is not configured");
        }
    }

    public Uni<ProviderCharge> create(UUID depositId, long amountCents, String returnUrl) {
        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of(
                    "merchantReference", depositId,
                    "amountCents", amountCents,
                    "description", "Crédito Bolão Boladão",
                    "returnUrl", returnUrl));
        } catch (JsonProcessingException exception) {
            return Uni.createFrom().failure(exception);
        }

        HttpRequest<Buffer> request = client.postAbs(baseUrl() + "/merchant/charges")
                .putHeader("Authorization", "Bearer " + apiKey)
                .putHeader("Idempotency-Key", "deposit:" + depositId)
                .putHeader("Content-Type", "application/json");
        return Uni.createFrom().completionStage(request.sendBuffer(Buffer.buffer(body)).toCompletionStage())
                .map(this::requireSuccess)
                .map(this::parse);
    }

    public Uni<ProviderCharge> get(UUID chargeId) {
        return Uni.createFrom().completionStage(client.getAbs(baseUrl() + "/merchant/charges/" + chargeId)
                        .putHeader("Authorization", "Bearer " + apiKey)
                        .send().toCompletionStage())
                .map(this::requireSuccess)
                .map(this::parse);
    }

    private HttpResponse<Buffer> requireSuccess(HttpResponse<Buffer> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ProviderException(response.statusCode(), "Payment provider returned " + response.statusCode());
        }
        return response;
    }

    private ProviderCharge parse(HttpResponse<Buffer> response) {
        try {
            JsonNode body = objectMapper.readTree(response.bodyAsString());
            return new ProviderCharge(
                    UUID.fromString(body.required("chargeId").asText()),
                    UUID.fromString(body.required("merchantReference").asText()),
                    body.required("amountCents").asLong(),
                    ProviderCharge.Status.from(body.required("status").asText()),
                    body.path("checkoutUrl").isMissingNode() ? null : body.path("checkoutUrl").asText(null),
                    instant(body, "expiresAt"),
                    instant(body, "createdAt"));
        } catch (Exception exception) {
            throw new ProviderException(502, "Invalid payment provider response", exception);
        }
    }

    private Instant instant(JsonNode node, String name) {
        String value = node.path(name).asText(null);
        return value == null ? null : Instant.parse(value);
    }

    private String baseUrl() {
        return providerUrl.endsWith("/") ? providerUrl.substring(0, providerUrl.length() - 1) : providerUrl;
    }

    private String readSecret(String inline, String file) {
        if (file != null && !file.isBlank()) {
            try {
                return Files.readString(Path.of(file)).trim();
            } catch (IOException exception) {
                throw new IllegalStateException("Could not read payment merchant API key", exception);
            }
        }
        return inline == null ? "" : inline.trim();
    }

    public static class ProviderException extends RuntimeException {
        private final int status;

        public ProviderException(int status, String message) {
            super(message);
            this.status = status;
        }

        public ProviderException(int status, String message, Throwable cause) {
            super(message, cause);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }
}
