package com.droidscope.android.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.droidscope.android.model.ShareFile;
import com.droidscope.android.transfer.PingClient;
import com.droidscope.android.transfer.ProgressListener;
import com.droidscope.android.transfer.UploadManager;
import com.droidscope.android.transfer.UploadResult;
import com.droidscope.android.transfer.UploadTask;
import com.droidscope.android.util.UriUtils;

import java.util.ArrayList;
import java.util.List;

public final class ShareActivity extends Activity {
    private static final String TAG = "DroidScope";
    private static final TransferGate TRANSFER_GATE = new TransferGate();
    private TextView statusView;
    private Button retryButton;
    private Button cancelButton;
    private volatile UploadManager currentUpload;
    private volatile PingClient currentPing;
    private volatile Thread transferThread;
    private volatile boolean ownsTransferGate;
    private final TransferUiState transferUiState = new TransferUiState();
    private Intent transferIntent;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = 48;
        layout.setPadding(padding, padding, padding, padding);
        statusView = new TextView(this);
        retryButton = new Button(this);
        retryButton.setText("重试");
        retryButton.setVisibility(View.GONE);
        retryButton.setOnClickListener(ignored -> retryTransfer());
        cancelButton = new Button(this);
        cancelButton.setText("取消");
        cancelButton.setTextAppearance(this, R.style.ShareCancelButton);
        cancelButton.setOnClickListener(ignored -> cancelTransfer());
        layout.addView(statusView);
        layout.addView(retryButton);
        layout.addView(cancelButton);
        setContentView(layout);
        statusView.setText("正在准备发送…");
        transferIntent = getIntent();
        startTransfer(transferIntent);
    }

    @Override
    protected void onDestroy() {
        transferUiState.destroy();
        interruptWaitingTransfer();
        cancelCurrentTransferAsync();
        super.onDestroy();
    }

    private void cancelTransfer() {
        if (!transferUiState.cancelAndClaimTerminal()) return;
        showClaimedTerminal("已取消发送");
        interruptWaitingTransfer();
        cancelCurrentTransferAsync();
    }

    private void retryTransfer() {
        if (!transferUiState.beginRetry()) return;
        retryButton.setVisibility(View.GONE);
        cancelButton.setEnabled(true);
        showStatus("正在重新连接电脑…");
        startTransfer(transferIntent);
    }

    private void startTransfer(Intent intent) {
        Thread worker = new Thread(() -> {
            boolean acquired = false;
            try {
                acquired = new ShareTransferEntry(TRANSFER_GATE, transferUiState)
                        .run(() -> transfer(intent));
            } finally {
                ownsTransferGate = false;
                if (transferThread == Thread.currentThread()) transferThread = null;
            }
        }, "usb-file-share-transfer");
        transferThread = worker;
        worker.start();
    }

    private void interruptWaitingTransfer() {
        Thread worker = transferThread;
        if (worker != null && !ownsTransferGate) worker.interrupt();
    }

    private void cancelCurrentTransferAsync() {
        PingClient ping = currentPing;
        UploadManager manager = currentUpload;
        if (ping == null && manager == null) return;
        new Thread(() -> {
            if (ping != null) ping.cancel();
            if (manager != null) manager.cancel();
        }, "usb-file-share-cancel").start();
    }

    private void transfer(Intent intent) {
        List<ShareFile> files = parseIntent(intent);
        if (transferUiState.isCanceled()) return;
        StringBuilder summary = new StringBuilder();
        for (ShareFile file : files) {
            Log.i(TAG, "URI=" + file.getUri());
            Log.i(TAG, "DISPLAY_NAME=" + file.getDisplayName());
            Log.i(TAG, "SIZE=" + file.getSize());
            Log.i(TAG, "MIME=" + file.getMimeType());
            summary.append(file.getDisplayName()).append("\n");
        }
        final String metadata = files.isEmpty() ? "未找到可分享文件" : summary.toString();
        showStatus("正在连接电脑…\n" + metadata);
        new ShareTransferCoordinator(new ShareTransferCoordinator.Factory() {
            @Override public ShareTransferCoordinator.Ping createPing() {
                PingClient ping = new PingClient();
                currentPing = ping;
                return () -> {
                    UploadResult result = ping.ping();
                    if (currentPing == ping) currentPing = null;
                    return result;
                };
            }

            @Override public ShareTransferCoordinator.Upload createUpload(ShareFile file,
                    int fileNumber, int fileCount) {
                UploadManager manager = new UploadManager(getContentResolver());
                currentUpload = manager;
                return () -> {
                    ProgressListener listener = (sent, total) -> {
                        long percent = total <= 0 ? 0 : sent * 100 / total;
                        showStatus("上传第 " + fileNumber + "/" + fileCount + " 个："
                                + percent + "%\n" + metadata);
                    };
                    UploadResult result = manager.upload(new UploadTask(file), listener);
                    if (currentUpload == manager) currentUpload = null;
                    return result;
                };
            }
        }, new ShareTransferPresenter(transferUiState, new ShareTransferPresenter.Port() {
            @Override public void showFailure(String text, boolean retryable) {
                showPresentedFailure(text + "\n" + metadata, retryable);
            }
            @Override public void showEmpty() { showTerminal("电脑已连接\n" + metadata); }
            @Override public void finishSuccess(int fileCount) {
                showSuccess("全部发送成功（" + fileCount + " 个）\n" + metadata);
            }
            @Override public void showCanceled() { showClaimedTerminal("已取消发送\n" + metadata); }
        })).transfer(files);
    }

    private void showStatus(String text) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (transferUiState.canShowStatus()) statusView.setText(text);
        });
    }

    private void showTerminal(String text) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (!transferUiState.tryClaimTerminal()) return;
            statusView.setText(text);
            cancelButton.setEnabled(false);
            retryButton.setVisibility(View.GONE);
        });
    }

    private void showFailure(UploadResult result, String text) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (!transferUiState.claimFailure(UploadManager.isRetryable(result))) return;
            statusView.setText(text);
            boolean retryable = transferUiState.canRetry();
            retryButton.setVisibility(retryable ? View.VISIBLE : View.GONE);
            cancelButton.setEnabled(true);
        });
    }

    private void showPresentedFailure(String text, boolean retryable) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            statusView.setText(text);
            retryButton.setVisibility(retryable ? View.VISIBLE : View.GONE);
            cancelButton.setEnabled(true);
        });
    }

    private void showSuccess(String text) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (!transferUiState.tryClaimTerminal()) return;
            statusView.setText(text);
            cancelButton.setEnabled(false);
            retryButton.setVisibility(View.GONE);
            finish();
        });
    }

    private void showClaimedTerminal(String text) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed() || transferUiState.isDestroyed()) return;
            statusView.setText(text);
            cancelButton.setEnabled(false);
            retryButton.setVisibility(View.GONE);
        });
    }

    private List<ShareFile> parseIntent(Intent intent) {
        ArrayList<Uri> uris = new ArrayList<>();
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) uris.add(uri);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            ArrayList<Uri> values = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (values != null) uris.addAll(values);
        }
        List<ShareFile> files = new ArrayList<>();
        for (Uri uri : uris) {
            try {
                String mime = getContentResolver().getType(uri);
                files.add(new ShareFile(uri, UriUtils.displayName(getContentResolver(), uri),
                        mime == null ? "application/octet-stream" : mime,
                        UriUtils.size(getContentResolver(), uri)));
            } catch (RuntimeException e) {
                Log.w(TAG, "URI_METADATA_FAILED=" + uri + ":" + e.getMessage());
            }
        }
        return files;
    }
}
