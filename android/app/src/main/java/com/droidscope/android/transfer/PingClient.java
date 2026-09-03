package com.droidscope.android.transfer;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public final class PingClient {
    private final String endpoint;
    private final int timeoutMillis;
    private final Object stateLock = new Object();
    private boolean canceled;
    private HttpURLConnection currentConnection;

    public PingClient() {
        this("http://127.0.0.1:9527/api/v1/ping", 1500);
    }

    PingClient(String endpoint, int timeoutMillis) {
        this.endpoint = endpoint;
        this.timeoutMillis = timeoutMillis;
    }

    public void cancel() {
        HttpURLConnection connection;
        synchronized (stateLock) {
            canceled = true;
            connection = currentConnection;
        }
        if (connection != null) connection.disconnect();
    }

    public UploadResult ping() {
        HttpURLConnection connection = null;
        try {
            if (isCanceled()) return canceledResult();
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            if (!setCurrentConnection(connection)) return canceledResult();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(timeoutMillis);
            connection.setReadTimeout(timeoutMillis);
            int code = connection.getResponseCode();
            if (isCanceled()) return canceledResult();
            return code >= 200 && code < 300 ? UploadResult.success(code)
                    : UploadResult.failure(code, connection.getResponseMessage());
        } catch (IOException e) {
            return isCanceled() ? canceledResult() : UploadResult.failure(e);
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
            currentConnection = connection;
            return true;
        }
    }

    private void clearCurrentConnection(HttpURLConnection connection) {
        synchronized (stateLock) {
            if (currentConnection == connection) currentConnection = null;
        }
    }

    private boolean isCanceled() {
        synchronized (stateLock) { return canceled; }
    }

    private static UploadResult canceledResult() {
        return UploadResult.failure(UploadError.CANCELED, -1, "ping canceled");
    }
}
