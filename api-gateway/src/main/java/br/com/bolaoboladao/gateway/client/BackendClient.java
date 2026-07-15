package br.com.bolaoboladao.gateway.client;

import io.smallrye.mutiny.Uni;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.annotation.PostConstruct;

@ApplicationScoped
public class BackendClient {

    @Inject
    Vertx vertx;

    private WebClient webClient;

    @PostConstruct
    void initializeClient() {
        webClient = WebClient.create(vertx);
    }

    public Uni<HttpResponse<Buffer>> get(String url, String authenticatedUserId) {
        return Uni.createFrom().completionStage(authenticatedGet(url, authenticatedUserId).send().toCompletionStage());
    }

    public Uni<HttpResponse<Buffer>> post(String url, String authenticatedUserId, String body) {
        return Uni.createFrom().completionStage(authenticatedPost(url, authenticatedUserId)
                .sendBuffer(Buffer.buffer(body)).toCompletionStage());
    }

    public Uni<HttpResponse<Buffer>> post(String url, String authenticatedUserId, String idempotencyKey, String body) {
        HttpRequest<Buffer> request = authenticatedPost(url, authenticatedUserId);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            request.putHeader("Idempotency-Key", idempotencyKey);
        }
        return Uni.createFrom().completionStage(request.sendBuffer(Buffer.buffer(body)).toCompletionStage());
    }

    public Uni<HttpResponse<Buffer>> publicPost(String url, String body) {
        return Uni.createFrom().completionStage(webClient.postAbs(url)
                .putHeader("Content-Type", "application/json")
                .sendBuffer(Buffer.buffer(body)).toCompletionStage());
    }

    public Uni<HttpResponse<Buffer>> publicGet(String url) {
        return Uni.createFrom().completionStage(webClient.getAbs(url)
                .send().toCompletionStage());
    }

    public Uni<HttpResponse<Buffer>> adminGet(String url, String authenticatedUserId, String authorization) {
        return Uni.createFrom().completionStage(authenticatedGet(url, authenticatedUserId)
                .putHeader("Authorization", authorization)
                .send().toCompletionStage());
    }

    public Uni<HttpResponse<Buffer>> adminPost(String url, String authenticatedUserId, String authorization,
                                                String idempotencyKey, String body) {
        HttpRequest<Buffer> request = authenticatedPost(url, authenticatedUserId)
                .putHeader("Authorization", authorization);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            request.putHeader("Idempotency-Key", idempotencyKey);
        }
        return Uni.createFrom().completionStage(request.sendBuffer(Buffer.buffer(body)).toCompletionStage());
    }

    private HttpRequest<Buffer> authenticatedGet(String url, String authenticatedUserId) {
        return webClient.getAbs(url)
                .putHeader("Content-Type", "application/json")
                .putHeader("X-Authenticated-User-Id", authenticatedUserId);
    }

    private HttpRequest<Buffer> authenticatedPost(String url, String authenticatedUserId) {
        return webClient.postAbs(url)
                .putHeader("Content-Type", "application/json")
                .putHeader("X-Authenticated-User-Id", authenticatedUserId);
    }
}
