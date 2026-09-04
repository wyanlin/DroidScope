package com.droidscope.android.transfer;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public final class UploadResult {
    private final boolean success;
    private final int httpCode;
    private final UploadError error;
    private final String message;

    private UploadResult(boolean success, int httpCode, UploadError error, String message) {
        this.success = success;
        this.httpCode = httpCode;
        this.error = error;
        this.message = message;
    }

    public static UploadResult success(int httpCode) {
        return new UploadResult(true, httpCode, null, "ok");
    }

    public static UploadResult failure(UploadError error, int httpCode, String message) {
        return new UploadResult(false, httpCode, error, message);
    }

    public static UploadResult failure(int httpCode, String message) {
        return failure(UploadError.SERVER_ERROR, httpCode, message);
    }

    static UploadResult failure(IOException error) {
        UploadError type = error instanceof SocketTimeoutException ? UploadError.TIMEOUT
                : error instanceof ConnectException || error instanceof NoRouteToHostException
                || error instanceof SocketException
                || error instanceof UnknownHostException ? UploadError.PC_NOT_CONNECTED
                : UploadError.UNKNOWN;
        return failure(type, -1, error.getMessage());
    }

    public static UploadResult failure(String message) {
        return failure(UploadError.UNKNOWN, -1, message);
    }

    public static UploadResult failure(int httpCode) {
        return failure(UploadError.SERVER_ERROR, httpCode, "HTTP " + httpCode);
    }

    public boolean isSuccess() { return success; }
    public int getHttpCode() { return httpCode; }
    public UploadError getError() { return error; }
    public String getMessage() { return message; }
}
