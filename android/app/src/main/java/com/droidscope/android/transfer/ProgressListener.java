package com.droidscope.android.transfer;

public interface ProgressListener {
    void onProgress(long sentBytes, long totalBytes);
}
