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
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class UploadManagerTest {
    @Test
    public void cancelDuringReadStopsFurtherWritesAndClosesRealConnection() throws Exception {
        CountDownLatch firstChunk = new CountDownLatch(1);
        CountDownLatch connectionClosed = new CountDownLatch(1);
        AtomicInteger received = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/upload", exchange -> receive(exchange, firstChunk, connectionClosed, received));
        server.start();
        ExecutorService uploads = Executors.newSingleThreadExecutor();
        ExecutorService cancellations = Executors.newSingleThreadExecutor();
        try {
            GateInputStream input = new GateInputStream();
            UploadManager manager = new UploadManager(ignored -> input,
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/upload");
            Future<UploadResult> upload = uploads.submit(() -> manager.upload(task(2), null));
            assertTrue(firstChunk.await(3, TimeUnit.SECONDS));
            assertTrue(input.secondReadStarted.await(3, TimeUnit.SECONDS));

            Future<?> cancel = cancellations.submit(manager::cancel);
            assertTrue("server did not observe disconnect", connectionClosed.await(3, TimeUnit.SECONDS));
            input.releaseSecondRead.countDown();

            cancel.get(3, TimeUnit.SECONDS);
            assertCanceled(upload);
            assertEquals(1, received.get());
        } finally {
            uploads.shutdownNow();
            cancellations.shutdownNow();
            server.stop(0);
        }
    }

    @Test
    public void cancelBeforeConnectionOpenPreventsOpenCall() throws Exception {
        AtomicInteger opens = new AtomicInteger();
        ControlledConnection connection = new ControlledConnection();
        Fixture fixture = newManager(connection, () -> opens.incrementAndGet(),
                UploadManager.CALL_CONNECTION_OPEN);

        UploadResult result = cancelAtCall(fixture);

        assertEquals(0, opens.get());
        assertEquals(UploadError.CANCELED, result.getError());
    }

    @Test
    public void cancelBeforeOutputStreamPreventsOutputStreamCall() throws Exception {
        ControlledConnection connection = new ControlledConnection();
        Fixture fixture = newManager(connection, null, UploadManager.CALL_OUTPUT_STREAM);

        UploadResult result = cancelAtCall(fixture);

        assertEquals(0, connection.outputStreamCalls.get());
        assertEquals(UploadError.CANCELED, result.getError());
        assertTrue(connection.disconnected);
    }

    @Test
    public void cancelBeforeWritePreventsWriteCall() throws Exception {
        ControlledConnection connection = new ControlledConnection();
        Fixture fixture = newManager(connection, null, UploadManager.CALL_WRITE);

        UploadResult result = cancelAtCall(fixture);

        assertEquals(0, connection.bytesWritten.get());
        assertEquals(UploadError.CANCELED, result.getError());
        assertTrue(connection.disconnected);
    }

    @Test
    public void cancelBeforeResponsePreventsResponseCall() throws Exception {
        ControlledConnection connection = new ControlledConnection();
        Fixture fixture = newManager(connection, null, UploadManager.CALL_RESPONSE);

        UploadResult result = cancelAtCall(fixture);

        assertEquals(0, connection.responseCalls.get());
        assertEquals(UploadError.CANCELED, result.getError());
        assertTrue(connection.disconnected);
    }

    private static UploadResult cancelAtCall(Fixture fixture) throws Exception {
        CallGate gate = fixture.gate;
        ExecutorService uploads = Executors.newSingleThreadExecutor();
        ExecutorService cancellations = Executors.newSingleThreadExecutor();
        try {
            Future<UploadResult> upload = uploads.submit(() -> fixture.manager.upload(task(1), null));
            assertTrue(gate.beforeCall.await(3, TimeUnit.SECONDS));
            Future<?> cancel = cancellations.submit(fixture.manager::cancel);
            assertTrue(gate.cancelRequested.await(3, TimeUnit.SECONDS));
            gate.release.countDown();
            cancel.get(3, TimeUnit.SECONDS);
            UploadResult result = upload.get(3, TimeUnit.SECONDS);
            assertFalse(result.isSuccess());
            return result;
        } finally {
            uploads.shutdownNow();
            cancellations.shutdownNow();
        }
    }

    private static Fixture newManager(ControlledConnection connection, OpenCounter openCounter,
            String targetCall) {
        CallGate gate = new CallGate(targetCall);
        return new Fixture(new UploadManager(ignored -> oneByteInput(), ignored -> {
            if (openCounter != null) openCounter.open();
            return connection;
        }, gate), gate);
    }

    private static final class Fixture {
        final UploadManager manager;
        final CallGate gate;
        Fixture(UploadManager manager, CallGate gate) {
            this.manager = manager;
            this.gate = gate;
        }
    }

    private static InputStream oneByteInput() {
        return new InputStream() {
            private boolean unread = true;
            @Override public int read(byte[] buffer, int offset, int length) {
                if (!unread) return -1;
                unread = false;
                buffer[offset] = 1;
                return 1;
            }
            @Override public int read() { return -1; }
        };
    }

    private static UploadTask task(long size) {
        return new UploadTask(new ShareFile(null, "cancel.bin", "application/octet-stream", size));
    }

    private static void assertCanceled(Future<UploadResult> upload) throws Exception {
        UploadResult result = upload.get(3, TimeUnit.SECONDS);
        assertFalse(result.isSuccess());
        assertEquals(UploadError.CANCELED, result.getError());
    }

    private static void receive(HttpExchange exchange, CountDownLatch firstChunk,
            CountDownLatch connectionClosed, AtomicInteger received) throws IOException {
        try (InputStream request = exchange.getRequestBody()) {
            int value;
            while ((value = request.read()) != -1) {
                received.incrementAndGet();
                firstChunk.countDown();
            }
        } catch (IOException ignored) {
        } finally {
            connectionClosed.countDown();
            exchange.close();
        }
    }

    private interface OpenCounter { void open(); }

    private static final class CallGate implements UploadManager.NetworkCallObserver {
        final String target;
        final CountDownLatch beforeCall = new CountDownLatch(1);
        final CountDownLatch cancelRequested = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        CallGate(String target) { this.target = target; }
        @Override public void beforeCall(String call) throws IOException {
            if (!target.equals(call)) return;
            beforeCall.countDown();
            try {
                if (!release.await(3, TimeUnit.SECONDS)) throw new IOException("gate was not released");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
        }
        @Override public void onCancelRequested() { cancelRequested.countDown(); }
    }

    private static final class GateInputStream extends InputStream {
        final CountDownLatch secondReadStarted = new CountDownLatch(1);
        final CountDownLatch releaseSecondRead = new CountDownLatch(1);
        private int reads;
        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            if (reads++ == 0) { buffer[offset] = 1; return 1; }
            if (reads > 2) return -1;
            secondReadStarted.countDown();
            try { releaseSecondRead.await(); } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); throw new IOException(e);
            }
            buffer[offset] = 2;
            return 1;
        }
        @Override public int read() throws IOException { throw new IOException("unexpected byte read"); }
    }

    private static final class ControlledConnection extends HttpURLConnection {
        final AtomicInteger outputStreamCalls = new AtomicInteger();
        final AtomicInteger bytesWritten = new AtomicInteger();
        final AtomicInteger responseCalls = new AtomicInteger();
        volatile boolean disconnected;
        ControlledConnection() throws IOException { super(new URL("http://127.0.0.1/upload")); }
        @Override public OutputStream getOutputStream() {
            outputStreamCalls.incrementAndGet();
            return new OutputStream() {
                @Override public void write(int value) { bytesWritten.incrementAndGet(); }
                @Override public void write(byte[] value, int offset, int length) {
                    bytesWritten.addAndGet(length);
                }
            };
        }
        @Override public int getResponseCode() { responseCalls.incrementAndGet(); return 200; }
        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }
    }
}
