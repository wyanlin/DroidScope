package com.droidscope.adb;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.FutureTask;

public final class AdbManager {
    private final String adbPath;
    private final Duration timeout;

    public AdbManager() {
        this("adb", Duration.ofSeconds(10));
    }

    public AdbManager(String adbPath) {
        this(adbPath, Duration.ofSeconds(10));
    }

    AdbManager(String adbPath, Duration timeout) {
        this.adbPath = adbPath;
        this.timeout = timeout;
    }

    public List<AdbDevice> listDevices() throws IOException, InterruptedException {
        CommandResult result = run("devices");
        if (result.exitCode() != 0) {
            throw new IOException("adb devices failed: " + result.output());
        }
        List<AdbDevice> devices = new ArrayList<>();
        for (String line : result.output().split("\\R")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("List of devices attached")) continue;
            String[] fields = line.split("\\s+");
            if (fields.length < 2) continue;
            devices.add(new AdbDevice(fields[0], parseState(fields[1])));
        }
        return devices;
    }

    public List<String> establishReverse() throws IOException, InterruptedException {
        List<String> ready = new ArrayList<>();
        for (AdbDevice device : listDevices()) {
            if (!device.isReady()) continue;
            if (establishReverse(device.serial())) ready.add(device.serial());
        }
        return ready;
    }

    public boolean establishReverse(String serial) throws IOException, InterruptedException {
        return run("-s", serial, "reverse", "tcp:9527", "tcp:9527").exitCode() == 0;
    }

    public String dumpWindows(String serial) throws IOException, InterruptedException {
        CommandResult result = run("-s", serial, "shell", "dumpsys", "window");
        if (result.exitCode() != 0) {
            throw new IOException("dumpsys window failed: " + result.output());
        }
        return result.output();
    }

    public String dumpActivities(String serial) throws IOException, InterruptedException {
        CommandResult result = run("-s", serial, "shell", "dumpsys", "activity", "activities");
        if (result.exitCode() != 0) {
            throw new IOException("dumpsys activity activities failed: " + result.output());
        }
        return result.output();
    }

    private CommandResult run(String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(adbPath);
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        FutureTask<byte[]> outputReader = new FutureTask<>(() -> process.getInputStream().readAllBytes());
        Thread outputThread = new Thread(outputReader, "droidscope-adb-output");
        outputThread.setDaemon(true);
        outputThread.start();
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new IOException("adb command timed out: " + String.join(" ", command));
        }
        try {
            return new CommandResult(process.exitValue(), new String(outputReader.get(1, TimeUnit.SECONDS), StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new IOException("cannot read adb command output", error);
        }
    }

    private static AdbDevice.State parseState(String state) {
        switch (state.toLowerCase()) {
            case "device": return AdbDevice.State.DEVICE;
            case "unauthorized": return AdbDevice.State.UNAUTHORIZED;
            case "offline": return AdbDevice.State.OFFLINE;
            default: return AdbDevice.State.UNKNOWN;
        }
    }

    private static final class CommandResult {
        private final int exitCode;
        private final String output;
        CommandResult(int exitCode, String output) { this.exitCode = exitCode; this.output = output; }
        int exitCode() { return exitCode; }
        String output() { return output; }
    }
}
