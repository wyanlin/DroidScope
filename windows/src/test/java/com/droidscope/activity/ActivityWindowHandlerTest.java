package com.droidscope.activity;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public final class ActivityWindowHandlerTest {
    public static void main(String[] args) throws Exception {
        String windowDump = "  Window #0 Window{abc u0 com.demo/.MainActivity}:\n"
                + "    mDisplayId=0 mSession=Session{abc 1234:1000}\n"
                + "    mHasSurface=true isReadyForDisplay()=true\n";
        String activityDump = "topResumedActivity=ActivityRecord{abc u0 com.demo/.MainActivity t1}\n"
                + "  * Hist  #0: ActivityRecord{abc u0 com.demo/.MainActivity t1}\n"
                + "    app=ProcessRecord{def 1234:com.demo/u0a1}\n"
                + "    mActivityComponent=com.demo/.MainActivity\n";
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            server.createContext("/api/v1/activity-window-snapshot",
                    new ActivityWindowHandler(serial -> windowDump, serial -> activityDump));
            server.start();
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                    + "/api/v1/activity-window-snapshot");
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> success = client.send(HttpRequest.newBuilder(
                    URI.create(base + "?serial=ABC")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, success.statusCode());
            assertContains(success.body(), "\"activities\":[");
            assertContains(success.body(), "\"relatedActivityId\":\"u0:com.demo/.MainActivity\"");

            HttpResponse<String> missing = client.send(HttpRequest.newBuilder(base).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(400, missing.statusCode());
            HttpResponse<String> method = client.send(HttpRequest.newBuilder(base)
                    .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(405, method.statusCode());
        } finally {
            server.stop(0);
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("expected " + expected + " but got " + actual);
    }

    private static void assertContains(String value, String expected) {
        if (!value.contains(expected)) throw new AssertionError("missing " + expected + " in " + value);
    }
}
