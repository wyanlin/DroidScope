package com.droidscope.android.transfer;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

public final class PingClient {
    interface ConnectionFactory { HttpURLConnection open() throws IOException; }

    private final int timeoutMillis;
    private final ConnectionFactory connectionFactory;
    private final Object stateLock = new Object();
    private boolean canceled;
    private final Set<HttpURLConnection> currentConnections = new HashSet<>();

    public PingClient() {
        this("http://127.0.0.1:9527/api/v1/ping", 1500);
    }

    PingClient(String endpoint, int timeoutMillis) {
        this(endpoint, timeoutMillis,
                () -> (HttpURLConnection) new URL(endpoint).openConnection());
    }

    PingClient(String endpoint, int timeoutMillis, ConnectionFactory connectionFactory) {
        this.timeoutMillis = timeoutMillis;
        this.connectionFactory = connectionFactory;
    }

    public void cancel() {
        HttpURLConnection[] connections;
        synchronized (stateLock) {
            canceled = true;
            connections = currentConnections.toArray(new HttpURLConnection[0]);
        }
        for (HttpURLConnection connection : connections) connection.disconnect();
    }

    public UploadResult ping() {
        HttpURLConnection connection = null;
        try {
            if (isCanceled()) return canceledResult();
            connection = connectionFactory.open();
            if (!setCurrentConnection(connection)) return canceledResult();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(timeoutMillis);
            connection.setReadTimeout(timeoutMillis);
            int code = connection.getResponseCode();
            String message = code >= 200 && code < 300 ? null : connection.getResponseMessage();
            return responseResult(code, message);
        } catch (IOException e) {
            return exceptionResult(e);
        } finally {
            if (connection != null) {
                clearCurrentConnection(connection);
                connection.disconnect();
            }
        }
    }

    private boolean setCurrentConnection(HttpURLConnection connection) {
        synchronized (stateLock) {
            if (canceled) {
                connection.disconnect();
                return false;
            }
            currentConnections.add(connection);
            return true;
        }
    }

    private void clearCurrentConnection(HttpURLConnection connection) {
        synchronized (stateLock) {
            currentConnections.remove(connection);
        }
    }

    private boolean isCanceled() {
        synchronized (stateLock) { return canceled; }
    }

    private UploadResult responseResult(int code, String message) {
        synchronized (stateLock) {
            if (canceled) return canceledResult();
            return code >= 200 && code < 300 ? UploadResult.success(code)
                    : UploadResult.failure(code, message);
        }
    }

    private UploadResult exceptionResult(IOException error) {
        synchronized (stateLock) {
            return canceled ? canceledResult() : UploadResult.failure(error);
        }
    }

    private static UploadResult canceledResult() {
        return UploadResult.failure(UploadError.CANCELED, -1, "ping canceled");
    }
}
