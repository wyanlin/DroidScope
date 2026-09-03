package com.droidscope.android.transfer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class UploadResultTest {
    @Test
    public void successHasNoErrorAndKeepsHttpStatus() {
        UploadResult result = UploadResult.success(201);

        assertTrue(result.isSuccess());
        assertEquals(201, result.getHttpCode());
        assertNull(result.getError());
        assertEquals("ok", result.getMessage());
    }

    @Test
    public void pcNotConnectedFailureKeepsOriginalMessage() {
        UploadResult result = UploadResult.failure(UploadError.PC_NOT_CONNECTED, -1, "Connection refused");

        assertFalse(result.isSuccess());
        assertEquals(UploadError.PC_NOT_CONNECTED, result.getError());
        assertEquals(-1, result.getHttpCode());
        assertEquals("Connection refused", result.getMessage());
    }

    @Test
    public void timeoutFailureKeepsOriginalMessage() {
        UploadResult result = UploadResult.failure(UploadError.TIMEOUT, -1, "Read timed out");

        assertFalse(result.isSuccess());
        assertEquals(UploadError.TIMEOUT, result.getError());
        assertEquals(-1, result.getHttpCode());
        assertEquals("Read timed out", result.getMessage());
    }

    @Test
    public void httpNon2xxMapsToServerErrorAndKeepsHttpResponse() {
        UploadResult result = UploadResult.failure(503, "Service Unavailable");

        assertFalse(result.isSuccess());
        assertEquals(UploadError.SERVER_ERROR, result.getError());
        assertEquals(503, result.getHttpCode());
        assertEquals("Service Unavailable", result.getMessage());
    }

    @Test
    public void connectionExceptionMapsToPcNotConnected() {
        UploadResult result = UploadResult.failure(new ConnectException("Connection refused"));

        assertEquals(UploadError.PC_NOT_CONNECTED, result.getError());
        assertEquals("Connection refused", result.getMessage());
    }

    @Test
    public void socketTimeoutExceptionMapsToTimeout() {
        UploadResult result = UploadResult.failure(new SocketTimeoutException("Read timed out"));

        assertEquals(UploadError.TIMEOUT, result.getError());
        assertEquals("Read timed out", result.getMessage());
    }

    @Test
    public void socketExceptionMapsToPcNotConnected() {
        UploadResult result = UploadResult.failure(new SocketException("Connection reset"));

        assertEquals(UploadError.PC_NOT_CONNECTED, result.getError());
        assertEquals("Connection reset", result.getMessage());
    }

    @Test
    public void pingReturnsTypedFailureForNon2xxResponse() throws IOException {
        ServerSocket server = new ServerSocket(0);
        Thread responder = new Thread(() -> {
            try (Socket socket = server.accept()) {
                socket.getInputStream().read();
                socket.getOutputStream().write(("HTTP/1.1 503 Service Unavailable\r\n"
                        + "Content-Length: 0\r\n\r\n").getBytes());
                socket.getOutputStream().flush();
            } catch (IOException ignored) {
            }
        });
        responder.start();
        try {
            PingClient client = new PingClient("http://127.0.0.1:" + server.getLocalPort() + "/ping", 1000);

            UploadResult result = client.ping();

            assertFalse(result.isSuccess());
            assertEquals(UploadError.SERVER_ERROR, result.getError());
            assertEquals(503, result.getHttpCode());
            assertEquals("Service Unavailable", result.getMessage());
        } finally {
            server.close();
        }
    }

    @Test
    public void cancelDuringPingDisconnectsConnectionAndReturnsCanceled() throws Exception {
        ServerSocket server = new ServerSocket(0);
        CountDownLatch requestReceived = new CountDownLatch(1);
        CountDownLatch clientDisconnected = new CountDownLatch(1);
        Thread responder = new Thread(() -> {
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(3000);
                while (socket.getInputStream().read() != -1) {
                    requestReceived.countDown();
                }
            } catch (IOException ignored) {
                // HttpURLConnection.disconnect may surface as a socket exception.
            } finally {
                clientDisconnected.countDown();
            }
        });
        responder.start();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            PingClient client = new PingClient("http://127.0.0.1:" + server.getLocalPort() + "/ping", 5000);
            Future<UploadResult> ping = executor.submit(client::ping);
            assertTrue(requestReceived.await(3, TimeUnit.SECONDS));

            client.cancel();

            UploadResult result = ping.get(3, TimeUnit.SECONDS);
            assertFalse(result.isSuccess());
            assertEquals(UploadError.CANCELED, result.getError());
            assertTrue(clientDisconnected.await(3, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            server.close();
        }
    }

    @Test
    public void cancelAfterPingResponseIsReadyReturnsCanceled() throws Exception {
        ServerSocket server = new ServerSocket(0);
        CountDownLatch responseReady = new CountDownLatch(1);
        CountDownLatch allowResult = new CountDownLatch(1);
        Thread responder = new Thread(() -> {
            try (Socket socket = server.accept()) {
                socket.getInputStream().read();
                socket.getOutputStream().write(("HTTP/1.1 503 Service Unavailable\r\n"
                        + "Content-Length: 0\r\n\r\n").getBytes());
                socket.getOutputStream().flush();
            } catch (IOException ignored) {
            }
        });
        responder.start();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            String endpoint = "http://127.0.0.1:" + server.getLocalPort() + "/ping";
            PingClient client = new PingClient(endpoint, 5000, () ->
                    new BlockingResponseMessageConnection((HttpURLConnection) new URL(endpoint).openConnection(),
                            responseReady, allowResult));
            Future<UploadResult> ping = executor.submit(client::ping);
            assertTrue(responseReady.await(3, TimeUnit.SECONDS));

            client.cancel();
            allowResult.countDown();

            assertEquals(UploadError.CANCELED, ping.get(3, TimeUnit.SECONDS).getError());
        } finally {
            allowResult.countDown();
            executor.shutdownNow();
            server.close();
        }
    }

    private static final class BlockingResponseMessageConnection extends HttpURLConnection {
        private final HttpURLConnection delegate;
        private final CountDownLatch responseReady;
        private final CountDownLatch allowResult;

        BlockingResponseMessageConnection(HttpURLConnection delegate, CountDownLatch responseReady,
                CountDownLatch allowResult) {
            super(delegate.getURL());
            this.delegate = delegate;
            this.responseReady = responseReady;
            this.allowResult = allowResult;
        }

        @Override public void setRequestMethod(String method) throws java.net.ProtocolException {
            delegate.setRequestMethod(method);
        }
        @Override public void setConnectTimeout(int timeout) { delegate.setConnectTimeout(timeout); }
        @Override public void setReadTimeout(int timeout) { delegate.setReadTimeout(timeout); }
        @Override public int getResponseCode() throws IOException { return delegate.getResponseCode(); }
        @Override public String getResponseMessage() throws IOException {
            String message = delegate.getResponseMessage();
            responseReady.countDown();
            try {
                if (!allowResult.await(3, TimeUnit.SECONDS)) throw new IOException("result was not released");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
            return message;
        }
        @Override public void disconnect() { delegate.disconnect(); }
        @Override public boolean usingProxy() { return delegate.usingProxy(); }
        @Override public void connect() throws IOException { delegate.connect(); }
    }
}
