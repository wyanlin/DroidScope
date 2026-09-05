package com.droidscope.local;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class SessionGuard {
    private static final String ALLOWED_ORIGIN = "http://127.0.0.1:9527";
    private final String token;

    public SessionGuard(String token) {
        this.token = token;
    }

    public boolean allow(HttpExchange exchange, boolean allowMissingOrigin) throws IOException {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        boolean originAllowed = ALLOWED_ORIGIN.equals(origin) || (allowMissingOrigin && origin == null);
        String providedToken = exchange.getRequestHeaders().getFirst("X-DroidScope-Session");
        boolean tokenRequired = origin != null;
        if (!originAllowed || (tokenRequired && !tokenMatches(token, providedToken))) {
            reject(exchange);
            return false;
        }
        addSecurityHeaders(exchange);
        return true;
    }

    private static boolean tokenMatches(String expected, String actual) {
        if (actual == null) return false;
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static void addSecurityHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Content-Security-Policy",
                "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
    }

    private static void reject(HttpExchange exchange) throws IOException {
        byte[] body = "forbidden\n".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(403, body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }
}
