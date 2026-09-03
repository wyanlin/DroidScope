package com.droidscope.android.transfer;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public final class PingClient {
    private final String endpoint;
    private final int timeoutMillis;

    public PingClient() {
        this("http://127.0.0.1:9527/api/v1/ping", 1500);
    }

    PingClient(String endpoint, int timeoutMillis) {
        this.endpoint = endpoint;
        this.timeoutMillis = timeoutMillis;
    }

    public UploadResult ping() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(timeoutMillis);
            connection.setReadTimeout(timeoutMillis);
            int code = connection.getResponseCode();
            return code >= 200 && code < 300 ? UploadResult.success(code)
                    : UploadResult.failure(code, connection.getResponseMessage());
        } catch (IOException e) {
            return UploadResult.failure(e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
