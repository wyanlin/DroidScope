package com.usbfileshare.android.transfer;

public final class UploadResult {
    private final boolean success;
    private final int httpCode;
    private final String message;

    private UploadResult(boolean success, int httpCode, String message) {
        this.success = success;
        this.httpCode = httpCode;
        this.message = message;
    }

    public static UploadResult success(int httpCode) { return new UploadResult(true, httpCode, "ok"); }
    public static UploadResult failure(String message) { return new UploadResult(false, -1, message); }
    public static UploadResult failure(int httpCode) { return new UploadResult(false, httpCode, "HTTP " + httpCode); }
    public boolean isSuccess() { return success; }
    public int getHttpCode() { return httpCode; }
    public String getMessage() { return message; }
}
