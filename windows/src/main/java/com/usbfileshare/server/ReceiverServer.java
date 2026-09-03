package com.usbfileshare.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.Executors;

public final class ReceiverServer {
    private final HttpServer server;

    public ReceiverServer(int port, Path storageDirectory) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/v1/ping", this::ping);
        server.createContext("/api/v1/files", new UploadHandler(storageDirectory));
        server.setExecutor(Executors.newCachedThreadPool());
    }

    public void start() {
        server.start();
    }

    private void ping(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "method not allowed\n");
            return;
        }
        respond(exchange, 200, "{\"status\":\"ok\",\"device\":\"Windows-PC\"}\n");
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
