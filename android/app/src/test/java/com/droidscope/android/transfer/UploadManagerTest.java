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
import java.net.InetSocketAddress;
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
}
