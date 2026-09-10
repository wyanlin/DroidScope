package com.droidscope.local;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class EventTicketHandler implements HttpHandler {
    private final SessionGuard guard;
    private final EventTicketStore tickets;

    public EventTicketHandler(SessionGuard guard, EventTicketStore tickets) {
        this.guard = guard;
        this.tickets = tickets;
    }

    @Override public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "method not allowed\n", "text/plain; charset=utf-8"); return;
        }
        if (!guard.allow(exchange, false)) return;
        if (exchange.getRequestBody().read() != -1) {
            respond(exchange, 400, "request body must be empty\n", "text/plain; charset=utf-8"); return;
        }
        respond(exchange, 200, "{\"ticket\":\"" + tickets.issue() + "\",\"expiresInSeconds\":30}", "application/json; charset=utf-8");
    }

    private static void respond(HttpExchange exchange, int status, String body, String type) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }
}
