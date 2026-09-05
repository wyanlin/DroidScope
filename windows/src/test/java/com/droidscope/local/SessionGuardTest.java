package com.droidscope.local;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class SessionGuardTest {
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        SessionGuard guard = new SessionGuard("expected-token");
        server.createContext("/api/v1/devices", exchange -> respondIfAllowed(exchange, guard, false));
        server.createContext("/api/v1/health", exchange -> respondIfAllowed(exchange, guard, true));
        server.start();
        try {
            URI base = new URI("http://127.0.0.1:" + server.getAddress().getPort());
            assertStatus(base, "/api/v1/devices", "expected-token", "http://127.0.0.1:9527", 204);
            assertStatus(base, "/api/v1/devices", null, "http://127.0.0.1:9527", 403);
            assertStatus(base, "/api/v1/devices", "wrong-token", "http://127.0.0.1:9527", 403);
            assertStatus(base, "/api/v1/devices", "expected-token", "https://example.test", 403);
            assertStatus(base, "/api/v1/devices", "expected-token", "null", 403);
            assertStatus(base, "/api/v1/devices", "expected-token", null, 403);
            assertStatus(base, "/api/v1/health", null, null, 204);
        } finally {
            server.stop(0);
        }
    }

    private static void respondIfAllowed(HttpExchange exchange, SessionGuard guard, boolean allowMissingOrigin) throws IOException {
        if (guard.allow(exchange, allowMissingOrigin)) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        }
    }

    private static void assertStatus(URI base, String path, String token, String origin, int expected) throws IOException {
        try (Socket socket = new Socket(base.getHost(), base.getPort())) {
            StringBuilder request = new StringBuilder("GET ").append(path).append(" HTTP/1.1\r\n")
                    .append("Host: ").append(base.getAuthority()).append("\r\n")
                    .append("Connection: close\r\n");
            if (token != null) request.append("X-DroidScope-Session: ").append(token).append("\r\n");
            if (origin != null) request.append("Origin: ").append(origin).append("\r\n");
            request.append("\r\n");
            socket.getOutputStream().write(request.toString().getBytes(StandardCharsets.US_ASCII));
            String statusLine = new String(socket.getInputStream().readNBytes(12), StandardCharsets.US_ASCII);
            int actual = Integer.parseInt(statusLine.substring(9, 12));
            if (actual != expected) {
                throw new AssertionError(path + ": expected " + expected + " but got " + actual);
            }
        }
    }
}
