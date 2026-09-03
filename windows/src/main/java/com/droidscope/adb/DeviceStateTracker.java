package com.droidscope.adb;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class DeviceStateTracker {
    private final Map<String, AdbDevice.State> states = new HashMap<>();

    boolean isReconnect(String serial, AdbDevice.State state) {
        AdbDevice.State previous = states.put(serial, state);
        return state == AdbDevice.State.DEVICE && previous != AdbDevice.State.DEVICE;
    }

    void retry(String serial) { states.remove(serial); }

    void removeMissing(Set<String> currentSerials) {
        states.keySet().removeIf(serial -> !currentSerials.contains(serial));
    }
}
