package com.droidscope;

import java.nio.file.Files;
import java.nio.file.Path;

public final class SingleInstanceLockTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("droidscope-lock-test");
        Path lockFile = directory.resolve("DroidScope.lock");
        try (SingleInstanceLock first = SingleInstanceLock.acquire(lockFile)) {
            assertTrue(first != null, "first process should acquire the lock");
            assertTrue(SingleInstanceLock.acquire(lockFile) == null,
                    "second process must not acquire the lock");
        }
        try (SingleInstanceLock next = SingleInstanceLock.acquire(lockFile)) {
            assertTrue(next != null, "lock should be available after first process exits");
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
