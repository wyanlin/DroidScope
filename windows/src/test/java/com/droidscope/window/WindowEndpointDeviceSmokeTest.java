package com.droidscope.window;

import com.droidscope.adb.AdbManager;
import com.droidscope.server.ReceiverServer;

import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Manual smoke test. Run with one connected-device serial as its only argument. */
public final class WindowEndpointDeviceSmokeTest {
    private static final int PORT = 19527;
    private static final String TOKEN = "window-endpoint-smoke-token";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("device serial is required");
        try (ReceiverServer server = new ReceiverServer(PORT, Path.of("build-out", "smoke-receive"), new AdbManager(), TOKEN)) {
            server.start();
            String request = "GET /api/v1/activity-window-snapshot?serial=" + args[0] + " HTTP/1.1\r\n"
                    + "Host: 127.0.0.1:" + PORT + "\r\n"
                    + "Origin: http://localhost:9527\r\n"
                    + "X-DroidScope-Session: " + TOKEN + "\r\nConnection: close\r\n\r\n";
            try (Socket socket = new Socket("127.0.0.1", PORT)) {
                socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
                String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (!response.startsWith("HTTP/1.1 200")) throw new AssertionError(response.substring(0, Math.min(200, response.length())));
                if (!response.contains("\"windows\":[")) throw new AssertionError("window payload missing");
                if (!response.contains("\"activities\":[")) throw new AssertionError("activity payload missing");
            }
        }
    }
}
