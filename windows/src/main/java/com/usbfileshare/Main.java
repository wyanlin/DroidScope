package com.usbfileshare;

import com.usbfileshare.server.ReceiverServer;
import com.usbfileshare.adb.AdbManager;

import java.io.IOException;
import java.nio.file.Path;

public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args.length > 0 ? args[0] : "D:\\ihblu\\wyrepo\\USB_File_Share\\PhoneReceive");
        ReceiverServer server = new ReceiverServer(9527, directory);
        server.start();
        System.out.println("USB File Share Receiver listening on http://127.0.0.1:9527");
        try {
            System.out.println("ADB reverse ready for: " + new AdbManager().establishReverse());
        } catch (IOException | InterruptedException e) {
            System.err.println("ADB unavailable: " + e.getMessage());
        }
        Thread.currentThread().join();
    }
}
