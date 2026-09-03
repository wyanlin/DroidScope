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
import java.util.concurrent.atomic.AtomicReference;

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
    public void cancelDuringConnectionOpenDisconnectsTheReturnedConnection() throws Exception {
        ControlledConnection connection = new ControlledConnection();
        CountDownLatch openStarted = new CountDownLatch(1);
        CountDownLatch releaseOpen = new CountDownLatch(1);
        CallGate gate = new CallGate("unused");
        UploadManager manager = new UploadManager(ignored -> oneByteInput(), ignored -> {
            openStarted.countDown();
            try {
                if (!releaseOpen.await(3, TimeUnit.SECONDS)) throw new IOException("open was not released");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
            return connection;
        }, gate);
        ExecutorService uploads = Executors.newSingleThreadExecutor();
        ExecutorService cancellations = Executors.newSingleThreadExecutor();
        try {
            Future<UploadResult> upload = uploads.submit(() -> manager.upload(task(1), null));
            assertTrue(openStarted.await(3, TimeUnit.SECONDS));

            Future<?> cancel = cancellations.submit(manager::cancel);
            assertTrue(gate.cancelRequested.await(3, TimeUnit.SECONDS));
            assertFalse("cancel returned while connection open was in flight", cancel.isDone());
            releaseOpen.countDown();

            cancel.get(3, TimeUnit.SECONDS);
            assertCanceled(upload);
            assertTrue(connection.disconnected);
        } finally {
            uploads.shutdownNow();
            cancellations.shutdownNow();
        }
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

    @Test
    public void cancelBeforeWriteClosesOutputStream() throws Exception {
        ControlledConnection connection = new ControlledConnection();
        Fixture fixture = newManager(connection, null, UploadManager.CALL_WRITE);

        UploadResult result = cancelAtCall(fixture);

        assertEquals(1, connection.outputCloseCalls.get());
        assertEquals(UploadError.CANCELED, result.getError());
        assertTrue(connection.disconnected);
    }

    @Test
    public void cancelAfterOutputStreamCreationClosesUnregisteredOutputStream() throws Exception {
        AtomicReference<UploadManager> managerReference = new AtomicReference<>();
        CancelOnOutputConnection connection = new CancelOnOutputConnection(
                () -> managerReference.get().cancel());
        UploadManager manager = new UploadManager(ignored -> oneByteInput(), ignored -> connection);
        managerReference.set(manager);
        ExecutorService uploads = Executors.newSingleThreadExecutor();
        try {
            assertCanceled(uploads.submit(() -> manager.upload(task(1), null)));
            assertEquals(1, connection.outputCloseCalls.get());
        } finally {
            uploads.shutdownNow();
        }
    }

    @Test
    public void cancelFromProgressListenerReturnsCanceledWithoutDeadlock() throws Exception {
        ControlledConnection connection = new ControlledConnection();
        UploadManager manager = new UploadManager(ignored -> oneByteInput(), ignored -> connection);
        ExecutorService uploads = Executors.newSingleThreadExecutor();
        try {
            Future<UploadResult> upload = uploads.submit(() ->
                    manager.upload(task(1), (sent, total) -> manager.cancel()));

            assertCanceled(upload);
            assertTrue(connection.disconnected);
        } finally {
            uploads.shutdownNow();
        }
    }

    @Test
    public void cancelFromNestedUploadProgressListenerDoesNotForgetOuterUploadThread() throws Exception {
        ControlledConnection outerConnection = new ControlledConnection();
        ControlledConnection nestedConnection = new ControlledConnection();
        AtomicInteger nextConnection = new AtomicInteger();
        AtomicReference<UploadResult> nestedResult = new AtomicReference<>();
        UploadManager manager = new UploadManager(ignored -> oneByteInput(), ignored ->
                nextConnection.getAndIncrement() == 0 ? outerConnection : nestedConnection);
        ExecutorService uploads = Executors.newSingleThreadExecutor();
        try {
            Future<UploadResult> outerUpload = uploads.submit(() -> manager.upload(task(1), (sent, total) -> {
                nestedResult.set(manager.upload(task(1),
                        (nestedSent, nestedTotal) -> manager.cancel()));
                manager.cancel();
            }));

            assertCanceled(outerUpload);
            assertEquals(UploadError.CANCELED, nestedResult.get().getError());
            assertTrue(outerConnection.disconnected);
            assertTrue(nestedConnection.disconnected);
        } finally {
            uploads.shutdownNow();
        }
    }

    @Test
    public void cancelDisconnectsAllConnectionsFromConcurrentUploads() throws Exception {
        BlockingConnection firstConnection = new BlockingConnection();
        BlockingConnection secondConnection = new BlockingConnection();
        AtomicInteger nextConnection = new AtomicInteger();
        UploadManager manager = new UploadManager(ignored -> oneByteInput(), ignored ->
                nextConnection.getAndIncrement() == 0 ? firstConnection : secondConnection);
        ExecutorService uploads = Executors.newFixedThreadPool(2);
        ExecutorService cancellations = Executors.newSingleThreadExecutor();
        try {
            Future<UploadResult> firstUpload = uploads.submit(() -> manager.upload(task(1), null));
            Future<UploadResult> secondUpload = uploads.submit(() -> manager.upload(task(1), null));
            assertTrue(firstConnection.outputStreamStarted.await(3, TimeUnit.SECONDS));
            assertTrue(secondConnection.outputStreamStarted.await(3, TimeUnit.SECONDS));

            cancellations.submit(manager::cancel).get(3, TimeUnit.SECONDS);

            assertCanceled(firstUpload);
            assertCanceled(secondUpload);
            assertTrue(firstConnection.disconnected);
            assertTrue(secondConnection.disconnected);
        } finally {
            uploads.shutdownNow();
            cancellations.shutdownNow();
        }
    }

    @Test
    public void cancelClosesBlockingInputAndReturnsCanceledWithoutManualRelease() throws Exception {
        BlockingInputStream input = new BlockingInputStream();
        ControlledConnection connection = new ControlledConnection();
        UploadManager manager = new UploadManager(ignored -> input, ignored -> connection);
        ExecutorService uploads = Executors.newSingleThreadExecutor();
        ExecutorService cancellations = Executors.newSingleThreadExecutor();
        try {
            Future<UploadResult> upload = uploads.submit(() -> manager.upload(task(1), null));
            assertTrue(input.readStarted.await(3, TimeUnit.SECONDS));

            cancellations.submit(manager::cancel).get(3, TimeUnit.SECONDS);

            assertCanceled(upload);
            assertTrue(input.closed);
        } finally {
            uploads.shutdownNow();
            cancellations.shutdownNow();
        }
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
            assertFalse("cancel returned while the call was in flight", cancel.isDone());
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

    private static final class BlockingInputStream extends InputStream {
        final CountDownLatch readStarted = new CountDownLatch(1);
        final CountDownLatch releaseRead = new CountDownLatch(1);
        volatile boolean closed;
        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            readStarted.countDown();
            try {
                if (!releaseRead.await(3, TimeUnit.SECONDS)) throw new IOException("input was not closed");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
            throw new IOException("input closed");
        }
        @Override public int read() throws IOException { return read(new byte[1], 0, 1); }
        @Override public void close() { closed = true; releaseRead.countDown(); }
    }

    private static final class ControlledConnection extends HttpURLConnection {
        final AtomicInteger outputStreamCalls = new AtomicInteger();
        final AtomicInteger outputCloseCalls = new AtomicInteger();
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
                @Override public void close() { outputCloseCalls.incrementAndGet(); }
            };
        }
        @Override public int getResponseCode() { responseCalls.incrementAndGet(); return 200; }
        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }
    }

    private static final class BlockingConnection extends HttpURLConnection {
        final CountDownLatch outputStreamStarted = new CountDownLatch(1);
        final CountDownLatch releaseOutputStream = new CountDownLatch(1);
        volatile boolean disconnected;
        BlockingConnection() throws IOException { super(new URL("http://127.0.0.1/upload")); }
        @Override public OutputStream getOutputStream() throws IOException {
            outputStreamStarted.countDown();
            try {
                if (!releaseOutputStream.await(3, TimeUnit.SECONDS)) {
                    throw new IOException("output stream was not released");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
            return new OutputStream() { @Override public void write(int value) { } };
        }
        @Override public int getResponseCode() { return 200; }
        @Override public void disconnect() {
            disconnected = true;
            releaseOutputStream.countDown();
        }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }
    }

    private static final class CancelOnOutputConnection extends HttpURLConnection {
        final AtomicInteger outputCloseCalls = new AtomicInteger();
        private final Runnable onOutputCreated;
        CancelOnOutputConnection(Runnable onOutputCreated) throws IOException {
            super(new URL("http://127.0.0.1/upload"));
            this.onOutputCreated = onOutputCreated;
        }
        @Override public OutputStream getOutputStream() {
            OutputStream output = new OutputStream() {
                @Override public void write(int value) { }
                @Override public void close() { outputCloseCalls.incrementAndGet(); }
            };
            onOutputCreated.run();
            return output;
        }
        @Override public int getResponseCode() { return 200; }
        @Override public void disconnect() { }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }
    }
}
