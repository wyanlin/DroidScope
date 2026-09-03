package com.droidscope.android.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
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
    private TextView statusView;
    private Button cancelButton;
    private volatile UploadManager currentUpload;
    private volatile boolean canceled;
    private volatile boolean terminal;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = 48;
        layout.setPadding(padding, padding, padding, padding);
        statusView = new TextView(this);
        cancelButton = new Button(this);
        cancelButton.setText("取消发送");
        cancelButton.setTextAppearance(this, R.style.ShareCancelButton);
        cancelButton.setOnClickListener(ignored -> cancelTransfer());
        layout.addView(statusView);
        layout.addView(cancelButton);
        setContentView(layout);
        statusView.setText("正在准备发送…");
        Intent incoming = getIntent();
        new Thread(() -> transfer(incoming), "usb-file-share-transfer").start();
    }

    @Override
    protected void onDestroy() {
        canceled = true;
        cancelCurrentUploadAsync();
        super.onDestroy();
    }

    private void cancelTransfer() {
        if (terminal) return;
        canceled = true;
        showTerminal("已取消发送");
        cancelCurrentUploadAsync();
    }

    private void cancelCurrentUploadAsync() {
        UploadManager manager = currentUpload;
        if (manager == null) return;
        new Thread(manager::cancel, "usb-file-share-cancel").start();
    }

    private void transfer(Intent intent) {
        List<ShareFile> files = parseIntent(intent);
        if (canceled) return;
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
        if (canceled) return;
        UploadResult pingResult = new PingClient().ping();
        if (canceled) return;
        if (!pingResult.isSuccess()) {
            showTerminal(TransferStatusText.failure(pingResult) + "\n" + metadata);
            return;
        }
        if (files.isEmpty()) {
            showTerminal("电脑已连接\n" + metadata);
            return;
        }
        for (int i = 0; i < files.size(); i++) {
            if (canceled) return;
            final int fileNumber = i + 1;
            ShareFile file = files.get(i);
            UploadManager manager = new UploadManager(getContentResolver());
            currentUpload = manager;
            if (canceled) {
                manager.cancel();
                return;
            }
            ProgressListener listener = (sent, total) -> {
                long percent = total <= 0 ? 0 : sent * 100 / total;
                showStatus("上传第 " + fileNumber + "/" + files.size() + " 个：" + percent + "%\n" + metadata);
            };
            UploadResult result = manager.upload(new UploadTask(file), listener);
            if (currentUpload == manager) currentUpload = null;
            if (canceled || result.getError() == com.droidscope.android.transfer.UploadError.CANCELED) {
                if (!terminal) showTerminal("已取消发送\n" + metadata);
                return;
            }
            if (!result.isSuccess()) {
                String failure = TransferStatusText.failure(result);
                showTerminal("第 " + fileNumber + " 个文件发送失败：" + failure + "\n" + metadata);
                return;
            }
        }
        showTerminal("全部发送成功（" + files.size() + " 个）\n" + metadata);
    }

    private void showStatus(String text) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (!terminal) statusView.setText(text);
        });
    }

    private void showTerminal(String text) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (terminal) return;
            terminal = true;
            statusView.setText(text);
            cancelButton.setEnabled(false);
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
