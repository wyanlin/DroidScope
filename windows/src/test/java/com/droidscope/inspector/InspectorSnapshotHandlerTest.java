package com.droidscope.inspector;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import com.sun.net.httpserver.HttpServer;

public final class InspectorSnapshotHandlerTest {
    public static void main(String[] args) throws Exception {
        String window = "  Window #0 Window{abc u0 com.demo/com.demo.MainActivity}:\n"
                + "    mDisplayId=0 mSession=Session{abc 1234:1000}\n";
        String activity = "  * Hist  #0: ActivityRecord{abc u0 com.demo/.MainActivity t1}\n"
                + "    app=ProcessRecord{def 1234:com.demo/u0a1}\n"
                + "    mActivityComponent=com.demo/.MainActivity\n";
        byte[] proto = Files.readAllBytes(Path.of("windows/src/test/resources/surface/oneplus9r-api34-20260910/surfaceflinger.pb"));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/inspector-snapshot", new InspectorSnapshotHandler(s -> window, s -> activity, s -> proto));
        server.start();
        try {
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1/inspector-snapshot");
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> success = client.send(HttpRequest.newBuilder(URI.create(base + "?serial=ABC")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, success.statusCode());
            assertContains(success.body(), "\"serial\":\"ABC\"");
            assertContains(success.body(), "\"surfaces\":[");
            assertEquals(400, client.send(HttpRequest.newBuilder(base).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(405, client.send(HttpRequest.newBuilder(base).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            HttpServer failing = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            failing.createContext("/api/v1/inspector-snapshot", new InspectorSnapshotHandler(s -> window, s -> activity, s -> { throw new RuntimeException("proto failed"); }));
            failing.start();
            try {
                URI failedUri = URI.create("http://127.0.0.1:" + failing.getAddress().getPort() + "/api/v1/inspector-snapshot?serial=ABC");
                assertEquals(502, client.send(HttpRequest.newBuilder(failedUri).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            } finally { failing.stop(0); }
        } finally { server.stop(0); }
    }
    private static void assertEquals(Object expected, Object actual) { if (!expected.equals(actual)) throw new AssertionError("expected " + expected + " but got " + actual); }
    private static void assertContains(String value, String expected) { if (!value.contains(expected)) throw new AssertionError("missing " + expected); }
}
