package com.droidscope.local;

import com.droidscope.adb.AdbDevice;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;

public final class DeviceHandler implements HttpHandler {
    private final Callable<List<AdbDevice>> devices;

    public DeviceHandler(Callable<List<AdbDevice>> devices) {
        this.devices = devices;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "method not allowed\n", "text/plain; charset=utf-8");
            return;
        }
        try {
            respond(exchange, 200, DeviceJson.encode(devices.call()), "application/json; charset=utf-8");
        } catch (Exception exception) {
            respond(exchange, 503,
                    "{\"error\":{\"code\":\"ADB_UNAVAILABLE\",\"message\":\"ADB is unavailable\"}}",
                    "application/json; charset=utf-8");
        }
    }

    private static void respond(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
