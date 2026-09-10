package com.droidscope.inspector;

import com.droidscope.adb.AdbManager;
import com.droidscope.server.ReceiverServer;

import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Manual smoke test. Run with one connected-device serial as its only argument. */
public final class InspectorEndpointDeviceSmokeTest {
    private static final int PORT = 19528;
    private static final String TOKEN = "inspector-endpoint-smoke-token";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("device serial is required");
        try (ReceiverServer server = new ReceiverServer(PORT, Path.of("build-out", "smoke-inspector"), new AdbManager(), TOKEN)) {
            server.start();
            String request = "GET /api/v1/inspector-snapshot?serial=" + args[0] + " HTTP/1.1\r\n"
                    + "Host: 127.0.0.1:" + PORT + "\r\n"
                    + "Origin: http://localhost:9527\r\n"
                    + "X-DroidScope-Session: " + TOKEN + "\r\nConnection: close\r\n\r\n";
            try (Socket socket = new Socket("127.0.0.1", PORT)) {
                socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
                String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (!response.startsWith("HTTP/1.1 200")) throw new AssertionError(response.substring(0, Math.min(300, response.length())));
                if (!response.contains("\"serial\":\"" + args[0] + "\"")) throw new AssertionError("serial missing");
                if (!response.contains("\"surfaces\":[")) throw new AssertionError("surface payload missing");
                if (!response.contains("\"relationKind\":\"")) throw new AssertionError("surface relation state missing");
                System.out.println("Inspector snapshot smoke passed; EXACT_METADATA=" + occurrences(response, "EXACT_METADATA")
                        + ", UNLINKED=" + occurrences(response, "UNLINKED") + ", AMBIGUOUS=" + occurrences(response, "AMBIGUOUS"));
            }
        }
        System.exit(0);
    }

    private static int occurrences(String text, String value) {
        int count = 0;
        for (int offset = 0; (offset = text.indexOf(value, offset)) >= 0; offset += value.length()) count++;
        return count;
    }

}
