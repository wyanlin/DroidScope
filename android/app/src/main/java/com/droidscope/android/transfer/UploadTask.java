package com.droidscope.android.transfer;

import com.droidscope.android.model.ShareFile;

public final class UploadTask {
    private final ShareFile file;
    private final String uploadId;

    public UploadTask(ShareFile file) {
        this.file = file;
        this.uploadId = java.util.UUID.randomUUID().toString();
    }

    public ShareFile getFile() { return file; }
    public String getUploadId() { return uploadId; }
}
