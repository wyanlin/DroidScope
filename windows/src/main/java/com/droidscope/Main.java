package com.droidscope;

import com.droidscope.server.ReceiverServer;
import com.droidscope.adb.AdbManager;
import com.droidscope.adb.AdbMonitor;
import com.droidscope.tray.TrayManager;
import com.droidscope.local.BrowserLauncher;
import com.droidscope.local.SessionToken;

import java.io.IOException;
import java.net.BindException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import javax.swing.JOptionPane;

public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args.length > 0 ? args[0]
                : Path.of(System.getProperty("user.home"), "DroidScope", "PhoneReceive").toString());
        ReceiverServer server;
        String sessionToken = SessionToken.create();
        AdbManager adbManager = new AdbManager();
        try {
            server = new ReceiverServer(9527, directory, adbManager, sessionToken);
        } catch (BindException e) {
            String message = "Port 9527 is already in use. Close the running DroidScope first.";
            System.err.println(message);
            JOptionPane.showMessageDialog(null, message, "DroidScope Startup Failed", JOptionPane.ERROR_MESSAGE);
            return;
        }
        server.start();
        System.out.println("DroidScope Receiver listening on http://127.0.0.1:9527");
        try {
            System.out.println("ADB reverse ready for: " + adbManager.establishReverse());
        } catch (IOException | InterruptedException e) {
            System.err.println("ADB unavailable: " + e.getMessage());
        }
        AdbMonitor adbMonitor = new AdbMonitor(adbManager, server.deviceEvents()::publish);
        adbMonitor.start();
        BrowserLauncher browserLauncher = new BrowserLauncher();
        browserLauncher.open(sessionToken);
        CountDownLatch exitLatch = new CountDownLatch(1);
        TrayManager trayManager = TrayManager.start(directory, adbManager, exitLatch::countDown,
                () -> browserLauncher.open(sessionToken));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (trayManager != null) trayManager.close();
            adbMonitor.close();
            server.close();
        }, "usb-file-share-shutdown"));
        try {
            exitLatch.await();
        } finally {
            if (trayManager != null) trayManager.close();
            adbMonitor.close();
            server.close();
        }
    }
}
