package com.droidscope;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

final class SingleInstanceLock implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;

    private SingleInstanceLock(FileChannel channel, FileLock lock) { this.channel = channel; this.lock = lock; }

    static SingleInstanceLock acquire(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) { channel.close(); return null; }
            return new SingleInstanceLock(channel, lock);
        } catch (OverlappingFileLockException exception) { channel.close(); return null; }
    }

    @Override public void close() throws IOException { lock.release(); channel.close(); }
}
