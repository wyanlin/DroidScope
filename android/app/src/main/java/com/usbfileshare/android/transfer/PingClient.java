package com.usbfileshare.android.transfer;

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

    public boolean ping() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(timeoutMillis);
        connection.setReadTimeout(timeoutMillis);
        try {
            return connection.getResponseCode() == HttpURLConnection.HTTP_OK;
        } finally {
            connection.disconnect();
        }
    }
}
