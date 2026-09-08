package com.droidscope.window;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

public final class WindowHandler {
    private final Function<String, String> dumpProvider;
    private final WindowDumpParser parser;

    public WindowHandler(Function<String, String> dumpProvider) {
        this.dumpProvider = dumpProvider;
        this.parser = new WindowDumpParser();
    }

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
            respond(exchange, 200, WindowJson.encode(parser.parse(dumpProvider.apply(serial))));
        } catch (Exception error) {
            String detail = error.getCause() == null ? error.getMessage() : error.getCause().getMessage();
            respond(exchange, 502, "{\"error\":\"WINDOW_CAPTURE_FAILED\",\"detail\":\"" + escape(detail) + "\"}\n");
        }
    }

    private static String query(String raw, String name) {
        if (raw == null) return null;
        for (String part : raw.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && name.equals(pair[0])) return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
        }
        return null;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
