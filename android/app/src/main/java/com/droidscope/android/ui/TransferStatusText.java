package com.droidscope.android.ui;

import com.droidscope.android.transfer.UploadError;
import com.droidscope.android.transfer.UploadResult;

public final class TransferStatusText {
    private TransferStatusText() {
    }

    public static String failure(UploadResult result) {
        UploadError error = result.getError();
        if (error == UploadError.PC_NOT_CONNECTED) return "电脑未连接";
        if (error == UploadError.TIMEOUT) return "连接电脑超时";
        String message = result.getMessage();
        if (error == UploadError.SERVER_ERROR) {
            return "电脑服务异常：" + (message == null ? "HTTP " + result.getHttpCode() : message);
        }
        return "连接失败：" + (message == null ? "未知错误" : message);
    }
}
