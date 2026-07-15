package br.com.bolaoboladao.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

public class PartidasBackendStub implements BeforeAllCallback, AfterAllCallback {
    private HttpServer server;
    final AtomicReference<String> lastPath = new AtomicReference<>();
    final AtomicReference<String> lastIdempotencyKey = new AtomicReference<>();
    final AtomicReference<String> lastAuthenticatedUser = new AtomicReference<>();

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        server = HttpServer.create(new InetSocketAddress(18081), 0);
        server.createContext("/partidas", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 2);
            exchange.getResponseBody().write("[]".getBytes());
            exchange.close();
        });
        server.createContext("/admin/partidas", exchange -> {
            lastPath.set(exchange.getRequestURI().getPath());
            lastIdempotencyKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            lastAuthenticatedUser.set(exchange.getRequestHeaders().getFirst("X-Authenticated-User-Id"));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] body = ("GET".equals(exchange.getRequestMethod())
                    ? "{\"items\":[],\"page\":0,\"size\":20,\"total\":0}"
                    : "{\"status\":\"IN_PROGRESS\",\"observedPath\":\"" + exchange.getRequestURI().getPath()
                      + "\",\"observedKey\":\"" + exchange.getRequestHeaders().getFirst("Idempotency-Key")
                      + "\",\"observedUser\":\"" + exchange.getRequestHeaders().getFirst("X-Authenticated-User-Id") + "\"}").getBytes();
            exchange.sendResponseHeaders(200, body.length);
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
