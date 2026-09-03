package com.droidscope.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.regex.Pattern;

public final class UploadHandler implements HttpHandler {
    private static final Pattern SAFE_NAME = Pattern.compile("[^A-Za-z0-9._ ()-]");
    private final Path directory;

    public UploadHandler(Path directory) {
        this.directory = directory;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "{\"error\":\"method_not_allowed\"}\n");
            return;
        }
        String rawName = exchange.getRequestHeaders().getFirst("X-File-Name");
        String sizeHeader = exchange.getRequestHeaders().getFirst("X-File-Size");
        if (rawName == null || rawName.isBlank() || sizeHeader == null) {
            respond(exchange, 400, "{\"error\":\"missing_headers\"}\n");
            return;
        }
        final long expected;
        try {
            expected = Long.parseLong(sizeHeader);
            if (expected < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            respond(exchange, 400, "{\"error\":\"invalid_file_size\"}\n");
            return;
        }
        String name = sanitize(rawName);
        Files.createDirectories(directory);
        Path target = resolveUnique(directory, name);
        Path part = target.resolveSibling(target.getFileName() + ".part");
        long actual = 0;
        boolean complete = false;
        try (InputStream input = exchange.getRequestBody();
             var output = Files.newOutputStream(part, StandardOpenOption.CREATE_NEW)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                actual += count;
                output.write(buffer, 0, count);
            }
            if (actual != expected) {
                respond(exchange, 400, "{\"error\":\"size_mismatch\",\"expected\":" + expected + ",\"actual\":" + actual + "}\n");
                return;
            }
            Files.move(part, target, StandardCopyOption.ATOMIC_MOVE);
            complete = true;
            respond(exchange, 201, "{\"status\":\"saved\",\"fileName\":\"" + json(target.getFileName().toString()) + "\",\"size\":" + actual + "}\n");
        } finally {
            if (!complete) Files.deleteIfExists(part);
        }
    }

    private static String sanitize(String value) {
        String base = Path.of(value.replace('\\', '/')).getFileName().toString();
        base = SAFE_NAME.matcher(base).replaceAll("_").trim();
        return base.isEmpty() ? "unnamed" : base;
    }

    private static Path resolveUnique(Path directory, String name) {
        Path candidate = directory.resolve(name);
        if (!Files.exists(candidate)) return candidate;
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; ; i++) {
            candidate = directory.resolve(stem + " (" + i + ")" + extension);
            if (!Files.exists(candidate)) return candidate;
        }
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
