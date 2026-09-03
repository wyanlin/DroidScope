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
        if (error == UploadError.SERVER_ERROR) return "电脑服务异常：" + result.getMessage();
        return "连接失败：" + result.getMessage();
    }
}
