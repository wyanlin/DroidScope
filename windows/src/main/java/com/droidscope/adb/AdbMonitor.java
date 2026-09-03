package com.droidscope.adb;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class AdbMonitor implements AutoCloseable {
    private final AdbManager adbManager;
    private final DeviceStateTracker stateTracker = new DeviceStateTracker();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "droidscope-adb-monitor");
        thread.setDaemon(true);
        return thread;
    });

    public AdbMonitor(AdbManager adbManager) { this.adbManager = adbManager; }

    public void start() {
        executor.scheduleWithFixedDelay(this::poll, 0, 2, TimeUnit.SECONDS);
    }

    private void poll() {
        try {
            Set<String> currentSerials = new HashSet<>();
            for (AdbDevice device : adbManager.listDevices()) {
                currentSerials.add(device.serial());
                if (device.isReady() && stateTracker.isReconnect(device.serial(), device.state())) {
                    try {
                        if (!adbManager.establishReverse(device.serial())) {
                            throw new IOException("adb reverse returned a non-zero exit code");
                        }
                        System.out.println("ADB reverse restored for: " + device.serial());
                    } catch (IOException | InterruptedException e) {
                        stateTracker.retry(device.serial());
                        System.err.println("ADB reverse failed for " + device.serial() + ": " + e.getMessage());
                    }
                }
            }
            stateTracker.removeMissing(currentSerials);
        } catch (IOException | InterruptedException e) {
            System.err.println("ADB monitor unavailable: " + e.getMessage());
        }
    }

    @Override
    public void close() { executor.shutdownNow(); }
}
