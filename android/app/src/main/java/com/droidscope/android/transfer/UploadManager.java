package com.droidscope.android.transfer;

import android.content.ContentResolver;

import com.droidscope.android.model.ShareFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class UploadManager {
    private static final int BUFFER_SIZE = 64 * 1024;
    static final String CALL_INPUT_OPEN = "inputOpen";
    static final String CALL_CONNECTION_OPEN = "connectionOpen";
    static final String CALL_OUTPUT_STREAM = "outputStream";
    static final String CALL_READ = "read";
    static final String CALL_WRITE = "write";
    static final String CALL_FLUSH = "flush";
    static final String CALL_RESPONSE = "response";
    static final String CALL_RESPONSE_MESSAGE = "responseMessage";

    interface InputStreamFactory { InputStream open(ShareFile file) throws IOException; }
    interface ConnectionFactory { HttpURLConnection open(ShareFile file) throws IOException; }
    interface NetworkCallObserver {
        void beforeCall(String call) throws IOException;
        void onCancelRequested();
    }

    private static final NetworkCallObserver NO_OP_OBSERVER = new NetworkCallObserver() {
        @Override public void beforeCall(String call) { }
        @Override public void onCancelRequested() { }
    };

    private final InputStreamFactory inputFactory;
    private final ConnectionFactory connectionFactory;
    private final NetworkCallObserver observer;
    private final Object stateLock = new Object();
    private boolean canceled;
    private int activeUploads;
    private int inFlightCalls;
    private int inputOpenCalls;
    private final Map<Thread, Integer> uploadThreadCounts = new HashMap<>();
    private final Set<HttpURLConnection> currentConnections = new HashSet<>();
    private final Set<InputStream> currentInputs = new HashSet<>();
    private final Set<OutputStream> currentOutputs = new HashSet<>();

    public UploadManager(ContentResolver resolver) {
        this(resolver, "http://127.0.0.1:9527/api/v1/files");
    }

    UploadManager(ContentResolver resolver, String endpoint) {
        this(file -> resolver.openInputStream(file.getUri()),
                file -> (HttpURLConnection) new URL(endpoint).openConnection());
    }

    UploadManager(InputStreamFactory inputFactory, String endpoint) {
        this(inputFactory, file -> (HttpURLConnection) new URL(endpoint).openConnection());
    }

    UploadManager(InputStreamFactory inputFactory, ConnectionFactory connectionFactory) {
        this(inputFactory, connectionFactory, NO_OP_OBSERVER);
    }

    UploadManager(InputStreamFactory inputFactory, ConnectionFactory connectionFactory,
            NetworkCallObserver observer) {
        this.inputFactory = inputFactory;
        this.connectionFactory = connectionFactory;
        this.observer = observer;
    }

    public void cancel() {
        HttpURLConnection[] connections;
        InputStream[] inputs;
        OutputStream[] outputs;
        boolean calledFromUploadThread;
        synchronized (stateLock) {
            canceled = true;
            connections = currentConnections.toArray(new HttpURLConnection[0]);
            inputs = currentInputs.toArray(new InputStream[0]);
            outputs = currentOutputs.toArray(new OutputStream[0]);
            currentInputs.clear();
            currentOutputs.clear();
            calledFromUploadThread = uploadThreadCounts.containsKey(Thread.currentThread());
        }
        observer.onCancelRequested();
        for (HttpURLConnection connection : connections) connection.disconnect();
        for (InputStream input : inputs) closeQuietly(input);
        for (OutputStream output : outputs) closeQuietly(output);
        if (calledFromUploadThread) return;
        boolean interrupted = false;
        synchronized (stateLock) {
            while (activeUploads > inputOpenCalls || inFlightCalls > 0) {
                try { stateLock.wait(); } catch (InterruptedException e) { interrupted = true; }
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    public UploadResult upload(UploadTask task, ProgressListener listener) {
        if (!beginUpload()) return canceledResult();
        UploadResult result;
        try {
            result = uploadInternal(task, listener);
        } catch (UploadCanceledException e) {
            result = canceledResult();
        } catch (IOException e) {
            result = isCanceled() ? canceledResult() : UploadResult.failure(e);
        } catch (SecurityException | IllegalArgumentException e) {
            result = isCanceled() ? canceledResult()
                    : UploadResult.failure(UploadError.UNKNOWN, -1, e.getMessage());
        } catch (RuntimeException e) {
            result = isCanceled() ? canceledResult()
                    : UploadResult.failure(UploadError.UNKNOWN, -1, e.getMessage());
        }
        return finishUpload(result);
    }

    private UploadResult uploadInternal(UploadTask task, ProgressListener listener) throws IOException {
        ShareFile file = task.getFile();
        if (file.getSize() < 0) return UploadResult.failure("file size unavailable");
        InputStream input = openInput(file);
        if (input == null) return UploadResult.failure("cannot open URI");
        if (!registerInput(input)) {
            input.close();
            throw new UploadCanceledException();
        }
        try {
            HttpURLConnection connection = openConnection(file);
            try {
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
                OutputStream output = call(CALL_OUTPUT_STREAM, connection::getOutputStream);
                if (!registerOutput(output)) {
                    output.close();
                    throw new UploadCanceledException();
                }
                try {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    long sent = 0;
                    int count;
                    while ((count = call(CALL_READ, () -> input.read(buffer))) != -1) {
                        final int bytes = count;
                        call(CALL_WRITE, () -> { output.write(buffer, 0, bytes); return null; });
                        call(CALL_FLUSH, () -> { output.flush(); return null; });
                        sent += count;
                        if (listener != null) listener.onProgress(sent, file.getSize());
                    }
                } finally {
                    closeOutput(output);
                }
                int code = call(CALL_RESPONSE, connection::getResponseCode);
                if (code >= 200 && code < 300) return UploadResult.success(code);
                return UploadResult.failure(code,
                        call(CALL_RESPONSE_MESSAGE, connection::getResponseMessage));
            } finally {
                clearConnection(connection);
                connection.disconnect();
            }
        } finally {
            closeInput(input);
        }
    }

    private boolean beginUpload() {
        synchronized (stateLock) {
            if (canceled) return false;
            activeUploads++;
            Thread thread = Thread.currentThread();
            Integer count = uploadThreadCounts.get(thread);
            uploadThreadCounts.put(thread, count == null ? 1 : count + 1);
            return true;
        }
    }

    private UploadResult finishUpload(UploadResult result) {
        synchronized (stateLock) {
            boolean wasCanceled = canceled;
            activeUploads--;
            Thread thread = Thread.currentThread();
            Integer count = uploadThreadCounts.get(thread);
            if (count == 1) uploadThreadCounts.remove(thread);
            else if (count != null) uploadThreadCounts.put(thread, count - 1);
            stateLock.notifyAll();
            return wasCanceled ? canceledResult() : result;
        }
    }

    private HttpURLConnection openConnection(ShareFile file) throws IOException {
        HttpURLConnection connection = null;
        boolean disconnect = false;
        synchronized (stateLock) {
            if (canceled) throw new UploadCanceledException();
            inFlightCalls++;
        }
        try {
            observer.beforeCall(CALL_CONNECTION_OPEN);
            synchronized (stateLock) {
                if (canceled) throw new UploadCanceledException();
            }
            connection = connectionFactory.open(file);
            synchronized (stateLock) {
                disconnect = canceled;
                if (!disconnect) currentConnections.add(connection);
            }
            if (disconnect) {
                connection.disconnect();
                throw new UploadCanceledException();
            }
            return connection;
        } finally {
            synchronized (stateLock) {
                inFlightCalls--;
                stateLock.notifyAll();
            }
        }
    }

    private InputStream openInput(ShareFile file) throws IOException {
        synchronized (stateLock) {
            if (canceled) throw new UploadCanceledException();
            inputOpenCalls++;
        }
        try {
            observer.beforeCall(CALL_INPUT_OPEN);
            synchronized (stateLock) {
                if (canceled) throw new UploadCanceledException();
            }
            InputStream input = inputFactory.open(file);
            boolean closeInput;
            synchronized (stateLock) {
                closeInput = canceled;
            }
            if (closeInput) {
                if (input != null) closeQuietly(input);
                throw new UploadCanceledException();
            }
            return input;
        } finally {
            synchronized (stateLock) {
                inputOpenCalls--;
                stateLock.notifyAll();
            }
        }
    }

    private void clearConnection(HttpURLConnection connection) {
        synchronized (stateLock) {
            currentConnections.remove(connection);
        }
    }

    private boolean isCanceled() {
        synchronized (stateLock) { return canceled; }
    }

    private void closeOutput(OutputStream output) throws IOException {
        if (claimOutput(output)) output.close();
    }

    private boolean registerInput(InputStream input) {
        synchronized (stateLock) {
            if (canceled) return false;
            currentInputs.add(input);
            return true;
        }
    }

    private void closeInput(InputStream input) throws IOException {
        if (claimInput(input)) input.close();
    }

    private boolean registerOutput(OutputStream output) {
        synchronized (stateLock) {
            if (canceled) return false;
            currentOutputs.add(output);
            return true;
        }
    }

    private boolean claimInput(InputStream input) {
        synchronized (stateLock) { return currentInputs.remove(input); }
    }

    private boolean claimOutput(OutputStream output) {
        synchronized (stateLock) { return currentOutputs.remove(output); }
    }

    private static void closeQuietly(InputStream input) {
        try { input.close(); } catch (IOException ignored) { }
    }

    private static void closeQuietly(OutputStream output) {
        try { output.close(); } catch (IOException ignored) { }
    }

    private static void closeResourceQuietly(Object resource) {
        if (resource instanceof InputStream) closeQuietly((InputStream) resource);
        else if (resource instanceof OutputStream) closeQuietly((OutputStream) resource);
    }

    private <T> T call(String name, IoCall<T> operation) throws IOException {
        synchronized (stateLock) {
            if (canceled) throw new UploadCanceledException();
            inFlightCalls++;
        }
        try {
            observer.beforeCall(name);
            synchronized (stateLock) {
                if (canceled) throw new UploadCanceledException();
            }
            T result = operation.run();
            boolean canceledAfterCall;
            synchronized (stateLock) {
                canceledAfterCall = canceled;
            }
            if (canceledAfterCall) {
                closeResourceQuietly(result);
                throw new UploadCanceledException();
            }
            return result;
        } finally {
            synchronized (stateLock) {
                inFlightCalls--;
                stateLock.notifyAll();
            }
        }
    }

    private interface IoCall<T> { T run() throws IOException; }
    private static final class UploadCanceledException extends IOException { }

    private static UploadResult canceledResult() {
        return UploadResult.failure(UploadError.CANCELED, -1, "upload canceled");
    }
}
