package com.usbfileshare.android.transfer;

public interface ProgressListener {
    void onProgress(long sentBytes, long totalBytes);
}
