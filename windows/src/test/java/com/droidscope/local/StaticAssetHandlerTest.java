package com.droidscope.local;

import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class StaticAssetHandlerTest {
    public static void main(String[] args) throws Exception {
        assertAsset("/ui/", "web/index.html", "text/html; charset=utf-8");
        assertAsset("/ui/assets/app.js", "web/assets/app.js", "text/javascript; charset=utf-8");
        assertAsset("/ui/assets/app.css", "web/assets/app.css", "text/css; charset=utf-8");
        assertInvalid("/ui/../secret");
        assertAsset("/ui/missing.js", "web/missing.js", "text/javascript; charset=utf-8");
        assertResponses();
    }

    private static void assertAsset(String path, String expectedResource, String expectedMime) {
        String resource = StaticAssetHandler.resourceName(path);
        if (!expectedResource.equals(resource)) {
            throw new AssertionError("expected resource " + expectedResource + " but got " + resource);
        }
        String mime = StaticAssetHandler.contentType(resource);
        if (!expectedMime.equals(mime)) {
            throw new AssertionError("expected MIME " + expectedMime + " but got " + mime);
        }
    }

    private static void assertInvalid(String path) {
        try {
            StaticAssetHandler.resourceName(path);
            throw new AssertionError("expected invalid path " + path);
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void assertResponses() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ui/", new StaticAssetHandler(new TestResources()));
        server.start();
        try {
            URI base = new URI("http://127.0.0.1:" + server.getAddress().getPort());
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> index = send(client, base.resolve("/ui/"));
            assertEquals(200, index.statusCode(), "index status");
            assertEquals("<h1>DroidScope</h1>", index.body(), "index content");
            assertEquals("text/html; charset=utf-8", index.headers().firstValue("Content-Type").orElse(""), "index MIME");
            assertEquals("no-store", index.headers().firstValue("Cache-Control").orElse(""), "index cache policy");

            HttpResponse<String> asset = send(client, base.resolve("/ui/assets/app.js"));
            assertEquals(200, asset.statusCode(), "asset status");
            assertEquals("text/javascript; charset=utf-8", asset.headers().firstValue("Content-Type").orElse(""), "asset MIME");

            HttpResponse<String> missing = send(client, base.resolve("/ui/missing.js"));
            assertEquals(404, missing.statusCode(), "missing asset status");
        } finally {
            server.stop(0);
        }
    }

    private static HttpResponse<String> send(HttpClient client, URI uri) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }

    private static final class TestResources extends ClassLoader {
        private final Map<String, byte[]> resources = Map.of(
                "web/index.html", "<h1>DroidScope</h1>".getBytes(StandardCharsets.UTF_8),
                "web/assets/app.js", "export {};".getBytes(StandardCharsets.UTF_8));

        @Override
        public ByteArrayInputStream getResourceAsStream(String name) {
            byte[] content = resources.get(name);
            return content == null ? null : new ByteArrayInputStream(content);
        }
    }
}
