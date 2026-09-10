package com.droidscope.adb;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;

public final class AdbManagerBinaryTest {
    public static void main(String[] args) throws Exception {
        byte[] expected = new byte[] {0x00, 0x7f, (byte) 0x80, (byte) 0xff, 0x01, 0x02, (byte) 0xfe};
        Path fakeAdb = Files.createTempFile("droidscope-fake-adb", ".cmd");
        String encoded = Base64.getEncoder().encodeToString(expected);
        Files.writeString(fakeAdb, "@echo off\r\n"
                + "if \"%3\"==\"exec-out\" powershell -NoProfile -Command \"$b=[Convert]::FromBase64String('"
                + encoded + "'); [Console]::OpenStandardOutput().Write($b,0,$b.Length)\"\r\n"
                + "if not \"%3\"==\"exec-out\" echo text-window\r\n",
                StandardCharsets.US_ASCII);
        try {
            AdbManager manager = new AdbManager(fakeAdb.toString());
            assertArrayEquals(expected, manager.dumpSurfaceFlingerProto("serial"),
                    "binary ADB output must be preserved byte-for-byte");
            assertEquals("text-window", manager.dumpWindows("serial").trim(),
                    "text ADB output must remain decoded as text");
        } finally {
            Files.deleteIfExists(fakeAdb);
        }
    }

    private static void assertArrayEquals(byte[] expected, byte[] actual, String message) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(message + ": expected=" + Arrays.toString(expected)
                    + ", actual=" + Arrays.toString(actual));
        }
    }

    private static void assertEquals(String expected, String actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": " + actual);
    }
}
