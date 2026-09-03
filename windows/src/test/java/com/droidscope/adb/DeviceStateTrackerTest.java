package com.droidscope.adb;

public final class DeviceStateTrackerTest {
    public static void main(String[] args) {
        DeviceStateTracker tracker = new DeviceStateTracker();

        assertTrue(tracker.isReconnect("serial-1", AdbDevice.State.DEVICE),
                "first ready device should establish reverse");
        assertFalse(tracker.isReconnect("serial-1", AdbDevice.State.DEVICE),
                "unchanged ready device should not establish reverse repeatedly");
        assertFalse(tracker.isReconnect("serial-1", AdbDevice.State.OFFLINE),
                "offline device should not establish reverse");
        assertTrue(tracker.isReconnect("serial-1", AdbDevice.State.DEVICE),
                "device returning online should establish reverse again");
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertFalse(boolean value, String message) {
        if (value) throw new AssertionError(message);
    }
}
