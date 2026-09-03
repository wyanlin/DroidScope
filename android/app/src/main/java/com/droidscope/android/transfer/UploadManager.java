package com.droidscope.android.transfer;

import android.content.ContentResolver;

import com.droidscope.android.model.ShareFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class UploadManager {
    private static final int BUFFER_SIZE = 64 * 1024;
    interface InputStreamFactory {
        InputStream open(ShareFile file) throws IOException;
    }

    private final InputStreamFactory inputFactory;
    private final String endpoint;
    private volatile boolean canceled;
    private volatile HttpURLConnection currentConnection;

    public UploadManager(ContentResolver resolver) {
        this(resolver, "http://127.0.0.1:9527/api/v1/files");
    }

    UploadManager(ContentResolver resolver, String endpoint) {
        this(file -> resolver.openInputStream(file.getUri()), endpoint);
    }

    UploadManager(InputStreamFactory inputFactory, String endpoint) {
        this.inputFactory = inputFactory;
        this.endpoint = endpoint;
    }

    public void cancel() {
        canceled = true;
        HttpURLConnection connection = currentConnection;
        if (connection != null) connection.disconnect();
    }

    public UploadResult upload(UploadTask task, ProgressListener listener) {
        ShareFile file = task.getFile();
        if (file.getSize() < 0) return UploadResult.failure("file size unavailable");
        if (canceled) return canceledResult();
        HttpURLConnection connection = null;
        try (InputStream input = inputFactory.open(file)) {
            if (input == null) return UploadResult.failure("cannot open URI");
            if (canceled) return canceledResult();
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            currentConnection = connection;
            if (canceled) return canceledResult();
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
                while (true) {
                    if (canceled) return canceledResult();
                    count = input.read(buffer);
                    if (count == -1) break;
                    if (canceled) return canceledResult();
                    output.write(buffer, 0, count);
                    output.flush();
                    sent += count;
                    if (listener != null) listener.onProgress(sent, file.getSize());
                }
            }
            if (canceled) return canceledResult();
            int code = connection.getResponseCode();
            return code >= 200 && code < 300 ? UploadResult.success(code)
                    : UploadResult.failure(code, connection.getResponseMessage());
        } catch (IOException e) {
            if (canceled) return canceledResult();
            return UploadResult.failure(e);
        } catch (SecurityException | IllegalArgumentException e) {
            return UploadResult.failure(UploadError.UNKNOWN, -1, e.getMessage());
        } finally {
            if (currentConnection == connection) currentConnection = null;
            if (connection != null) connection.disconnect();
        }
    }

    private static UploadResult canceledResult() {
        return UploadResult.failure(UploadError.CANCELED, -1, "upload canceled");
    }
}
