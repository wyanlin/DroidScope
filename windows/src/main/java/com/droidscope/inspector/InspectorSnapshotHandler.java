package com.droidscope.inspector;

import com.droidscope.activity.ActivityDumpParser;
import com.droidscope.activity.ActivityWindowResolver;
import com.droidscope.surface.SurfaceProtoParser;
import com.droidscope.surface.SurfaceWindowResolver;
import com.droidscope.window.WindowDumpParser;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class InspectorSnapshotHandler implements com.sun.net.httpserver.HttpHandler {
    private final Function<String, String> windowDumpProvider;
    private final Function<String, String> activityDumpProvider;
    private final Function<String, byte[]> surfaceProtoProvider;

    public InspectorSnapshotHandler(Function<String, String> windowDumpProvider,
                                    Function<String, String> activityDumpProvider,
                                    Function<String, byte[]> surfaceProtoProvider) {
        this.windowDumpProvider = windowDumpProvider;
        this.activityDumpProvider = activityDumpProvider;
        this.surfaceProtoProvider = surfaceProtoProvider;
    }

    @Override public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) { respond(exchange, 405, "{\"error\":\"METHOD_NOT_ALLOWED\"}\n"); return; }
        String serial = query(exchange.getRequestURI().getRawQuery(), "serial");
        if (serial == null || serial.isEmpty()) { respond(exchange, 400, "{\"error\":\"MISSING_SERIAL\"}\n"); return; }
        try {
            CompletableFuture<String> windows = CompletableFuture.supplyAsync(() -> windowDumpProvider.apply(serial));
            CompletableFuture<String> activities = CompletableFuture.supplyAsync(() -> activityDumpProvider.apply(serial));
            CompletableFuture<byte[]> surfaces = CompletableFuture.supplyAsync(() -> surfaceProtoProvider.apply(serial));
            var windowSnapshot = new WindowDumpParser().parse(windows.join());
            var activitySnapshot = new ActivityDumpParser().parse(activities.join());
            var surfaceSnapshot = new SurfaceProtoParser().parse(surfaces.join());
            var activityWindow = new ActivityWindowResolver().resolve(activitySnapshot.activities(), windowSnapshot.windows());
            var relations = new SurfaceWindowResolver().resolve(activityWindow.windows(), surfaceSnapshot.layers());
            respond(exchange, 200, InspectorSnapshotJson.encode(new InspectorSnapshot(System.currentTimeMillis(), serial,
                    activityWindow.windows(), activityWindow.activities(), surfaceSnapshot.layers(), relations)));
        } catch (Exception error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            respond(exchange, 502, "{\"error\":\"INSPECTOR_CAPTURE_FAILED\",\"detail\":" + quote(cause.getMessage()) + "}\n");
        }
    }

    private static String query(String raw, String name) { if (raw == null) return null; for (String part : raw.split("&")) { String[] pair = part.split("=", 2); if (pair.length == 2 && name.equals(pair[0])) return URLDecoder.decode(pair[1], StandardCharsets.UTF_8); } return null; }
    private static void respond(HttpExchange exchange, int status, String body) throws IOException { byte[] bytes = body.getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8"); exchange.sendResponseHeaders(status, bytes.length); try (var output = exchange.getResponseBody()) { output.write(bytes); } }
    private static String quote(String value) { return value == null ? "null" : "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""; }
}
