package com.droidscope.android.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.droidscope.android.model.ShareFile;
import com.droidscope.android.transfer.PingClient;
import com.droidscope.android.transfer.ProgressListener;
import com.droidscope.android.transfer.UploadError;
import com.droidscope.android.transfer.UploadManager;
import com.droidscope.android.transfer.UploadResult;
import com.droidscope.android.transfer.UploadTask;
import com.droidscope.android.util.UriUtils;

import java.util.ArrayList;
import java.util.List;

public final class ShareActivity extends Activity {
    private static final String TAG = "DroidScope";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setPadding(48, 48, 48, 48);
        setContentView(view);
        List<ShareFile> files = parseIntent(getIntent());
        StringBuilder summary = new StringBuilder();
        for (ShareFile file : files) {
            Log.i(TAG, "URI=" + file.getUri());
            Log.i(TAG, "DISPLAY_NAME=" + file.getDisplayName());
            Log.i(TAG, "SIZE=" + file.getSize());
            Log.i(TAG, "MIME=" + file.getMimeType());
            summary.append(file.getDisplayName()).append("\n");
        }
        final String metadata = files.isEmpty() ? "未找到可分享文件" : summary.toString();
        view.setText("正在连接电脑…\n" + metadata);
        new Thread(() -> transfer(files, metadata, view), "usb-file-share-transfer").start();
    }

    private void transfer(List<ShareFile> files, String metadata, TextView view) {
        UploadResult pingResult = new PingClient().ping();
        if (!pingResult.isSuccess()) {
            String failure = pingResult.getError() == UploadError.TIMEOUT ? "连接电脑超时"
                    : pingResult.getError() == UploadError.SERVER_ERROR
                    ? "电脑服务异常：" + pingResult.getMessage()
                    : pingResult.getError() == UploadError.PC_NOT_CONNECTED ? "电脑未连接"
                    : "连接失败：" + pingResult.getMessage();
            runOnUiThread(() -> view.setText(failure + "\n" + metadata));
            return;
        }
        if (files.isEmpty()) {
            runOnUiThread(() -> view.setText("电脑已连接\n" + metadata));
            return;
        }
        UploadManager manager = new UploadManager(getContentResolver());
        for (int i = 0; i < files.size(); i++) {
            final int fileNumber = i + 1;
            ShareFile file = files.get(i);
            ProgressListener listener = (sent, total) -> runOnUiThread(() -> {
                long percent = total <= 0 ? 0 : sent * 100 / total;
                view.setText("上传第 " + fileNumber + "/" + files.size() + " 个：" + percent + "%\n" + metadata);
            });
            UploadResult result = manager.upload(new UploadTask(file), listener);
            if (!result.isSuccess()) {
                runOnUiThread(() -> view.setText("第 " + fileNumber + " 个文件发送失败：" + result.getMessage() + "\n" + metadata));
                return;
            }
        }
        runOnUiThread(() -> view.setText("全部发送成功（" + files.size() + " 个）\n" + metadata));
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
