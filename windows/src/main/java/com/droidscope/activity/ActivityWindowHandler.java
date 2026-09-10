package com.droidscope.activity;

import com.droidscope.window.WindowDumpParser;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class ActivityWindowHandler implements com.sun.net.httpserver.HttpHandler {
    private final Function<String, String> windowDumpProvider;
    private final Function<String, String> activityDumpProvider;
    private final WindowDumpParser windowParser = new WindowDumpParser();
    private final ActivityDumpParser activityParser = new ActivityDumpParser();
    private final ActivityWindowResolver resolver = new ActivityWindowResolver();

    public ActivityWindowHandler(Function<String, String> windowDumpProvider,
                                 Function<String, String> activityDumpProvider) {
        this.windowDumpProvider = windowDumpProvider;
        this.activityDumpProvider = activityDumpProvider;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "{\"error\":\"METHOD_NOT_ALLOWED\"}\n");
            return;
        }
        String serial = query(exchange.getRequestURI().getRawQuery(), "serial");
        if (serial == null || serial.isEmpty()) {
            respond(exchange, 400, "{\"error\":\"MISSING_SERIAL\"}\n");
            return;
        }
        try {
            CompletableFuture<String> windowDump = CompletableFuture.supplyAsync(() -> windowDumpProvider.apply(serial));
            CompletableFuture<String> activityDump = CompletableFuture.supplyAsync(() -> activityDumpProvider.apply(serial));
            ActivityWindowSnapshot snapshot = resolver.resolve(
                    activityParser.parse(activityDump.join()).activities(),
                    windowParser.parse(windowDump.join()).windows());
            respond(exchange, 200, ActivityWindowJson.encode(snapshot));
        } catch (Exception error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            respond(exchange, 502, "{\"error\":\"ACTIVITY_WINDOW_CAPTURE_FAILED\",\"detail\":"
                    + quote(cause.getMessage()) + "}\n");
        }
    }

    private static String query(String raw, String name) {
        if (raw == null) return null;
        for (String part : raw.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && name.equals(pair[0])) {
                return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }

    private static String quote(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}
