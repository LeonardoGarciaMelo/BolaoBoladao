package br.com.bolaoboladao.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class ApostasBackendStub implements BeforeAllCallback, AfterAllCallback {
    static final String USER_ID = "22121193-3c26-4c26-812d-123456789012";
    private HttpServer server;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        server = HttpServer.create(new InetSocketAddress(18083), 0);
        server.createContext("/bets", exchange -> {
            String identity = exchange.getRequestHeaders().getFirst("X-Authenticated-User-Id");
            String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
            byte[] body = ("{\"identity\":\"" + identity + "\",\"idempotencyKey\":\"" + key + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (server != null) server.stop(0);
    }
}
