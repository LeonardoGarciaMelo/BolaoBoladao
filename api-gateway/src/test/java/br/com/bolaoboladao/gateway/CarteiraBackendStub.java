package br.com.bolaoboladao.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class CarteiraBackendStub implements BeforeAllCallback, AfterAllCallback {
    static final String AUTHENTICATED_USER_ID = "22121193-3c26-4c26-812d-123456789012";

    private HttpServer server;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        server = HttpServer.create(new InetSocketAddress(18082), 0);
        server.createContext("/wallet", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())
                    && exchange.getRequestURI().getPath().equals("/wallet/me/deposits")) {
                String identity = exchange.getRequestHeaders().getFirst("X-Authenticated-User-Id");
                String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
                String bodyText = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                boolean expected = AUTHENTICATED_USER_ID.equals(identity)
                        && "deposit-key".equals(key)
                        && bodyText.contains("5000");
                byte[] depositBody = (expected
                        ? "{\"depositId\":\"7cd186a4-9e8b-4cb3-848a-9fdb7f518cb9\",\"status\":\"PENDING\"}"
                        : "{\"forwarded\":false}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(expected ? 201 : 400, depositBody.length);
                exchange.getResponseBody().write(depositBody);
                exchange.close();
                return;
            }

            if (exchange.getRequestURI().getPath().endsWith("/forbidden/balance")) {
                exchange.sendResponseHeaders(403, -1);
                exchange.close();
                return;
            }

            String identity = exchange.getRequestHeaders().getFirst("X-Authenticated-User-Id");
            String requestTarget = exchange.getRequestURI().toString();
            boolean requestIsExpected = AUTHENTICATED_USER_ID.equals(identity)
                    && requestTarget.equals("/wallet/22121193-3c26-4c26-812d-123456789012/balance?includePending=true");
            byte[] body = (requestIsExpected ? "{\"forwarded\":true}" : "{\"forwarded\":false}")
                    .getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(requestIsExpected ? 200 : 400, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (server != null) {
            server.stop(0);
        }
    }
}
