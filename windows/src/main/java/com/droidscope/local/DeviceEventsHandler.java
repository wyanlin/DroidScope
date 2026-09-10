package com.droidscope.local;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class DeviceEventsHandler implements HttpHandler {
    private final EventTicketStore tickets;
    private final DeviceEventBroker broker;

    public DeviceEventsHandler(EventTicketStore tickets, DeviceEventBroker broker) {
        this.tickets = tickets;
        this.broker = broker;
    }

    @Override public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()) || !tickets.consume(ticket(exchange))) {
            exchange.sendResponseHeaders(403, -1); exchange.close(); return;
        }
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);
        try (var subscription = broker.subscribe(); var output = exchange.getResponseBody()) {
            while (!Thread.currentThread().isInterrupted()) {
                String event = subscription.await(Duration.ofSeconds(15));
                String line = event == null ? ": keepalive\n\n" : "event: devices\ndata: " + event + "\n\n";
                output.write(line.getBytes(StandardCharsets.UTF_8)); output.flush();
            }
        } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    private static String ticket(HttpExchange exchange) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) return "";
        for (String part : query.split("&")) if (part.startsWith("ticket=")) return part.substring("ticket=".length());
        return "";
    }
}
