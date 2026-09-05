package com.droidscope.server;

import com.droidscope.adb.AdbManager;
import com.droidscope.local.DeviceEventBroker;
import com.droidscope.local.DeviceEventsHandler;
import com.droidscope.local.DeviceHandler;
import com.droidscope.local.EventTicketHandler;
import com.droidscope.local.EventTicketStore;
import com.droidscope.local.SessionGuard;
import com.droidscope.local.StaticAssetHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.Executors;

public final class ReceiverServer implements AutoCloseable {
    private final HttpServer server;
    private final DeviceEventBroker broker;

    public ReceiverServer(int port, Path storageDirectory) throws IOException {
        this(port, storageDirectory, new AdbManager(), "");
    }

    public ReceiverServer(int port, Path storageDirectory, AdbManager adbManager, String token) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/v1/ping", this::ping);
        server.createContext("/api/v1/files", new UploadHandler(storageDirectory));
        SessionGuard guard = new SessionGuard(token);
        broker = new DeviceEventBroker();
        server.createContext("/ui/", new StaticAssetHandler());
        server.createContext("/api/v1/health", exchange -> {
            if (guard.allow(exchange, true)) respond(exchange, 200, "{\"status\":\"ok\",\"platform\":\"" + System.getProperty("os.name") + "\",\"version\":\"0.1\"}\n");
        });
        server.createContext("/api/v1/devices", exchange -> {
            if (guard.allow(exchange, false)) new DeviceHandler(adbManager::listDevices).handle(exchange);
        });
        EventTicketStore tickets = new EventTicketStore(Clock.systemUTC());
        server.createContext("/api/v1/event-tickets", new EventTicketHandler(guard, tickets));
        server.createContext("/api/v1/events", new DeviceEventsHandler(tickets, broker));
        server.setExecutor(Executors.newCachedThreadPool());
    }

    public void start() {
        server.start();
    }

    public DeviceEventBroker deviceEvents() { return broker; }

    @Override
    public void close() {
        server.stop(0);
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
