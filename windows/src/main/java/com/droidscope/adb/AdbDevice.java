package com.droidscope.adb;

public final class AdbDevice {
    private final String serial;
    private final State state;

    public AdbDevice(String serial, State state) {
        this.serial = serial;
        this.state = state;
    }

    public String serial() { return serial; }
    public State state() { return state; }

    public enum State { DEVICE, UNAUTHORIZED, OFFLINE, UNKNOWN }

    public boolean isReady() {
        return state == State.DEVICE;
    }
}
