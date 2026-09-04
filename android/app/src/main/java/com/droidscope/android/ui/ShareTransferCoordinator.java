package com.droidscope.android.ui;

import com.droidscope.android.model.ShareFile;
import com.droidscope.android.transfer.UploadError;
import com.droidscope.android.transfer.UploadResult;

import java.util.List;

final class ShareTransferCoordinator {
    interface Ping { UploadResult ping(); }
    interface Upload { UploadResult upload(); }
    interface Factory {
        Ping createPing();
        Upload createUpload(ShareFile file, int fileNumber, int fileCount);
    }
    interface Callback {
        boolean isCanceled();
        void onFailure(UploadResult result, int fileNumber);
        void onEmpty();
        void onSuccess(int fileCount);
        void onCanceled();
    }

    private final Factory factory;
    private final Callback callback;

    ShareTransferCoordinator(Factory factory, Callback callback) {
        this.factory = factory;
        this.callback = callback;
    }

    void transfer(List<ShareFile> files) {
        if (callback.isCanceled()) { callback.onCanceled(); return; }
        UploadResult ping = factory.createPing().ping();
        if (callback.isCanceled()) { callback.onCanceled(); return; }
        if (!ping.isSuccess()) { callback.onFailure(ping, 0); return; }
        if (files.isEmpty()) { callback.onEmpty(); return; }
        for (int i = 0; i < files.size(); i++) {
            if (callback.isCanceled()) { callback.onCanceled(); return; }
            UploadResult upload = factory.createUpload(files.get(i), i + 1, files.size()).upload();
            if (callback.isCanceled() || upload.getError() == UploadError.CANCELED) {
                callback.onCanceled(); return;
            }
            if (!upload.isSuccess()) { callback.onFailure(upload, i + 1); return; }
        }
        callback.onSuccess(files.size());
    }
}
