package com.droidscope.local;

import com.droidscope.adb.AdbDevice;

import java.util.List;

public final class DeviceJsonTest {
    public static void main(String[] args) {
        assertEquals(
                "{\"devices\":[{\"serial\":\"ABC123\",\"state\":\"device\",\"ready\":true},{\"serial\":\"OFFLINE1\",\"state\":\"offline\",\"ready\":false}]}",
                DeviceJson.encode(List.of(
                        new AdbDevice("ABC123", AdbDevice.State.DEVICE),
                        new AdbDevice("OFFLINE1", AdbDevice.State.OFFLINE))));
        assertEquals(
                "{\"devices\":[{\"serial\":\"A\\\"B\\\\C\\u0001\",\"state\":\"unknown\",\"ready\":false}]}",
                DeviceJson.encode(List.of(new AdbDevice("A\"B\\C\u0001", AdbDevice.State.UNKNOWN))));
    }

    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected " + expected + " but got " + actual);
        }
    }
}
