package com.droidscope;

import java.nio.file.Files;
import java.nio.file.Path;

public final class SingleInstanceLockTest {
    public static void main(String[] args) throws Exception {
        Path lockFile = Files.createTempDirectory("droidscope-lock-test").resolve("DroidScope.lock");
        try (SingleInstanceLock first = SingleInstanceLock.acquire(lockFile)) {
            if (first == null || SingleInstanceLock.acquire(lockFile) != null) throw new AssertionError("lock must be exclusive");
        }
        try (SingleInstanceLock next = SingleInstanceLock.acquire(lockFile)) {
            if (next == null) throw new AssertionError("lock must be released");
        }
    }
}
