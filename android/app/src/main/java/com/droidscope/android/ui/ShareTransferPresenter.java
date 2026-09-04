package com.droidscope.android.ui;

import com.droidscope.android.transfer.UploadManager;
import com.droidscope.android.transfer.UploadResult;

final class ShareTransferPresenter implements ShareTransferCoordinator.Callback {
    interface Port {
        void showFailure(String text, boolean retryable);
        void showEmpty();
        void finishSuccess(int fileCount);
        void showCanceled();
    }

    private final TransferUiState state;
    private final Port port;

    ShareTransferPresenter(TransferUiState state, Port port) {
        this.state = state;
        this.port = port;
    }

    @Override public boolean isCanceled() { return state.isCanceled(); }
    @Override public void onFailure(UploadResult result, int fileNumber) {
        if (!state.claimFailure(UploadManager.isRetryable(result))) return;
        String failure = TransferStatusText.failure(result);
        port.showFailure(fileNumber == 0 ? failure
                : "第 " + fileNumber + " 个文件发送失败：" + failure, state.canRetry());
    }
    @Override public void onEmpty() { port.showEmpty(); }
    @Override public void onSuccess(int fileCount) { port.finishSuccess(fileCount); }
    @Override public void onCanceled() {
        if (state.cancelAndClaimTerminal()) port.showCanceled();
    }
}
