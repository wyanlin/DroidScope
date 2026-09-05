package com.droidscope.local;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class StaticAssetHandler implements HttpHandler {
    private static final Map<String, String> MIME_TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "svg", "image/svg+xml",
            "png", "image/png");

    private final ClassLoader classLoader;

    public StaticAssetHandler() {
        this(StaticAssetHandler.class.getClassLoader());
    }

    StaticAssetHandler(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "method not allowed\n", "text/plain; charset=utf-8");
            return;
        }

        final String resource;
        try {
            resource = resourceName(exchange.getRequestURI().getPath());
        } catch (IllegalArgumentException exception) {
            respond(exchange, 400, "invalid asset path\n", "text/plain; charset=utf-8");
            return;
        }

        try (InputStream input = classLoader.getResourceAsStream(resource)) {
            if (input == null) {
                respond(exchange, 404, "not found\n", "text/plain; charset=utf-8");
                return;
            }
            byte[] content = input.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", contentType(resource));
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().set("Cache-Control", cacheControl(resource));
            exchange.sendResponseHeaders(200, content.length);
            try (var output = exchange.getResponseBody()) {
                output.write(content);
            }
        }
    }

    static String resourceName(String requestPath) {
        if (!requestPath.startsWith("/ui/")) {
            throw new IllegalArgumentException("invalid asset path");
        }
        String relative = requestPath.substring("/ui/".length());
        if (relative.isEmpty()) relative = "index.html";
        if (relative.contains("..") || relative.startsWith("/")) {
            throw new IllegalArgumentException("invalid asset path");
        }
        return "web/" + relative;
    }

    static String contentType(String resourceName) {
        int extensionIndex = resourceName.lastIndexOf('.');
        if (extensionIndex < 0) return "application/octet-stream";
        return MIME_TYPES.getOrDefault(resourceName.substring(extensionIndex + 1), "application/octet-stream");
    }

    private static String cacheControl(String resourceName) {
        return resourceName.matches("web/assets/.+-[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9]+")
                ? "public, max-age=31536000, immutable"
                : "no-store";
    }

    private static void respond(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
