package com.usbfileshare.android.transfer;

import android.content.ContentResolver;

import com.usbfileshare.android.model.ShareFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class UploadManager {
    private static final int BUFFER_SIZE = 64 * 1024;
    private final ContentResolver resolver;
    private final String endpoint;

    public UploadManager(ContentResolver resolver) {
        this(resolver, "http://127.0.0.1:9527/api/v1/files");
    }

    UploadManager(ContentResolver resolver, String endpoint) {
        this.resolver = resolver;
        this.endpoint = endpoint;
    }

    public UploadResult upload(UploadTask task, ProgressListener listener) {
        ShareFile file = task.getFile();
        if (file.getSize() < 0) return UploadResult.failure("file size unavailable");
        HttpURLConnection connection = null;
        try (InputStream input = resolver.openInputStream(file.getUri())) {
            if (input == null) return UploadResult.failure("cannot open URI");
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(10000);
            connection.setFixedLengthStreamingMode(file.getSize());
            connection.setRequestProperty("Content-Type", "application/octet-stream");
            connection.setRequestProperty("X-File-Name", file.getDisplayName());
            connection.setRequestProperty("X-File-Size", Long.toString(file.getSize()));
            connection.setRequestProperty("X-Mime-Type", file.getMimeType());
            connection.setRequestProperty("X-Upload-Id", task.getUploadId());
            try (OutputStream output = connection.getOutputStream()) {
                byte[] buffer = new byte[BUFFER_SIZE];
                long sent = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    sent += count;
                    if (listener != null) listener.onProgress(sent, file.getSize());
                }
            }
            int code = connection.getResponseCode();
            return code >= 200 && code < 300 ? UploadResult.success(code) : UploadResult.failure(code);
        } catch (IOException | SecurityException | IllegalArgumentException e) {
            return UploadResult.failure(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
