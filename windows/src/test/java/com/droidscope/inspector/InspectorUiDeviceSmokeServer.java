package com.droidscope.inspector;

import com.droidscope.adb.AdbManager;
import com.droidscope.server.ReceiverServer;

import java.nio.file.Path;

/** Starts the latest Inspector server for manual browser acceptance on an isolated port. */
public final class InspectorUiDeviceSmokeServer {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("port and token are required");
        int port = Integer.parseInt(args[0]);
        try (ReceiverServer server = new ReceiverServer(port, Path.of("build-out", "smoke-ui-receive"), new AdbManager(), args[1])) {
            server.start();
            Thread.sleep(300_000L);
        }
    }
}
