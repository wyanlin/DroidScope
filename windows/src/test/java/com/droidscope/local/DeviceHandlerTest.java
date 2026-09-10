package com.droidscope.local;

import com.droidscope.adb.AdbDevice;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Callable;

public final class DeviceHandlerTest {
    public static void main(String[] args) throws Exception {
        assertResponse(() -> List.of(new AdbDevice("ABC123", AdbDevice.State.DEVICE)), "GET", 200,
                "{\"devices\":[{\"serial\":\"ABC123\",\"state\":\"device\",\"ready\":true}]}");
        assertResponse(() -> List.of(), "POST", 405, "method not allowed\n");
        assertResponse(() -> { throw new java.io.IOException("adb unavailable"); }, "GET", 503,
                "{\"error\":{\"code\":\"ADB_UNAVAILABLE\",\"message\":\"ADB is unavailable\"}}");
    }

    private static void assertResponse(Callable<List<AdbDevice>> devices, String method, int expectedStatus, String expectedBody) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/devices", new DeviceHandler(devices));
        server.start();
        try {
            URI uri = new URI("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1/devices");
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(uri).method(method, HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != expectedStatus) throw new AssertionError("unexpected status " + response.statusCode());
            if (!expectedBody.equals(response.body())) throw new AssertionError("unexpected body " + response.body());
        } finally {
            server.stop(0);
        }
    }
}
