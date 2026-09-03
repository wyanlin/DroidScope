package com.droidscope.android.transfer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.droidscope.android.model.ShareFile;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class UploadManagerTest {
    @Test
    public void cancelDuringReadStopsFurtherWritesReturnsCanceledAndClosesConnection() throws Exception {
        CountDownLatch firstChunkReceived = new CountDownLatch(1);
        CountDownLatch connectionClosed = new CountDownLatch(1);
        AtomicInteger receivedBytes = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/upload", exchange -> receiveUntilDisconnected(exchange, firstChunkReceived,
                connectionClosed, receivedBytes));
        server.start();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            GateInputStream input = new GateInputStream();
            UploadManager manager = new UploadManager(ignored -> input,
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/upload");
            ShareFile file = new ShareFile(null, "cancel.bin", "application/octet-stream", 2);
            Future<UploadResult> upload = executor.submit(() -> manager.upload(new UploadTask(file), null));

            assertTrue("first chunk was not sent", firstChunkReceived.await(3, TimeUnit.SECONDS));
            assertTrue("second read did not start", input.secondReadStarted.await(3, TimeUnit.SECONDS));
            manager.cancel();
            input.releaseSecondRead.countDown();

            UploadResult result = upload.get(3, TimeUnit.SECONDS);
            assertFalse(result.isSuccess());
            assertEquals(UploadError.CANCELED, result.getError());
            assertTrue("server did not observe connection close", connectionClosed.await(3, TimeUnit.SECONDS));
            assertEquals("bytes read after cancellation must not be written", 1, receivedBytes.get());
        } finally {
            executor.shutdownNow();
            server.stop(0);
        }
    }

    @Test
    public void cancelWhileOpeningOutputStreamPreventsAnyWrite() throws Exception {
        ControlledConnection connection = new ControlledConnection();
        connection.outputStreamGate = new Gate();
        UploadManager manager = newManager(connection);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<UploadResult> upload = executor.submit(() -> manager.upload(task(), null));

            assertTrue(connection.outputStreamGate.started.await(3, TimeUnit.SECONDS));
            manager.cancel();
            connection.outputStreamGate.release.countDown();

            assertCanceled(upload);
            assertEquals(0, connection.bytesWritten.get());
            assertTrue(connection.disconnected);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void cancelAtWriteBoundaryPreventsThePendingWrite() throws Exception {
        ControlledConnection connection = new ControlledConnection();
        connection.writeGate = new Gate();
        UploadManager manager = newManager(connection);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<UploadResult> upload = executor.submit(() -> manager.upload(task(), null));

            assertTrue(connection.writeGate.started.await(3, TimeUnit.SECONDS));
            manager.cancel();
            connection.writeGate.release.countDown();

            assertCanceled(upload);
            assertEquals(0, connection.bytesWritten.get());
            assertTrue(connection.disconnected);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void cancelBeforeResponseCompletesCannotReturnSuccess() throws Exception {
        ControlledConnection connection = new ControlledConnection();
        connection.responseGate = new Gate();
        UploadManager manager = newManager(connection);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<UploadResult> upload = executor.submit(() -> manager.upload(task(), null));

            assertTrue(connection.responseGate.started.await(3, TimeUnit.SECONDS));
            manager.cancel();
            connection.responseGate.release.countDown();

            assertCanceled(upload);
            assertEquals(1, connection.bytesWritten.get());
            assertTrue(connection.disconnected);
        } finally {
            executor.shutdownNow();
        }
    }

    private static UploadManager newManager(ControlledConnection connection) {
        return new UploadManager(ignored -> new InputStream() {
            private boolean unread = true;

            @Override
            public int read(byte[] buffer, int offset, int length) {
                if (!unread) return -1;
                unread = false;
                buffer[offset] = 1;
                return 1;
            }

            @Override
            public int read() {
                return -1;
            }
        }, ignored -> connection);
    }

    private static UploadTask task() {
        return new UploadTask(new ShareFile(null, "cancel.bin", "application/octet-stream", 1));
    }

    private static void assertCanceled(Future<UploadResult> upload) throws Exception {
        UploadResult result = upload.get(3, TimeUnit.SECONDS);
        assertFalse(result.isSuccess());
        assertEquals(UploadError.CANCELED, result.getError());
    }

    private static void receiveUntilDisconnected(HttpExchange exchange, CountDownLatch firstChunkReceived,
            CountDownLatch connectionClosed, AtomicInteger receivedBytes) throws IOException {
        try (InputStream request = exchange.getRequestBody()) {
            int value;
            if ((value = request.read()) != -1) {
                receivedBytes.incrementAndGet();
                firstChunkReceived.countDown();
            }
            while ((value = request.read()) != -1) {
                receivedBytes.incrementAndGet();
                // Wait for HttpURLConnection.disconnect() to close the request body.
            }
        } catch (IOException ignored) {
            // A client disconnect can surface as an IOException on some JDKs.
        } finally {
            connectionClosed.countDown();
            exchange.close();
        }
    }

    private static final class GateInputStream extends InputStream {
        final CountDownLatch secondReadStarted = new CountDownLatch(1);
        final CountDownLatch releaseSecondRead = new CountDownLatch(1);
        private int reads;

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (reads++ == 0) {
                buffer[offset] = 1;
                return 1;
            }
            if (reads > 2) return -1;
            secondReadStarted.countDown();
            try {
                if (!releaseSecondRead.await(3, TimeUnit.SECONDS)) {
                    throw new IOException("second read was not released");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
            buffer[offset] = 2;
            return 1;
        }

        @Override
        public int read() throws IOException {
            throw new IOException("byte reads are not expected");
        }
    }

    private static final class Gate {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        void await() throws IOException {
            started.countDown();
            try {
                if (!release.await(3, TimeUnit.SECONDS)) throw new IOException("gate was not released");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
        }
    }

    private static final class ControlledConnection extends HttpURLConnection {
        final AtomicInteger bytesWritten = new AtomicInteger();
        volatile boolean disconnected;
        Gate outputStreamGate;
        Gate writeGate;
        Gate responseGate;

        ControlledConnection() throws IOException {
            super(new URL("http://127.0.0.1/upload"));
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            if (outputStreamGate != null) outputStreamGate.await();
            if (disconnected) throw new IOException("disconnected");
            return new OutputStream() {
                @Override
                public void write(int value) throws IOException {
                    if (writeGate != null) writeGate.await();
                    if (disconnected) throw new IOException("disconnected");
                    bytesWritten.incrementAndGet();
                }

                @Override
                public void write(byte[] buffer, int offset, int length) throws IOException {
                    if (writeGate != null) writeGate.await();
                    if (disconnected) throw new IOException("disconnected");
                    bytesWritten.addAndGet(length);
                }
            };
        }

        @Override
        public int getResponseCode() throws IOException {
            if (responseGate != null) responseGate.await();
            return 200;
        }

        @Override
        public void disconnect() {
            disconnected = true;
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
        }
    }
}
