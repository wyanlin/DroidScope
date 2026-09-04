package com.droidscope.android.ui;

import static org.junit.Assert.assertEquals;

import com.droidscope.android.transfer.UploadError;
import com.droidscope.android.transfer.UploadResult;

import org.junit.Test;

public final class TransferStatusTextTest {
    @Test
    public void mapsAllUploadFailuresToUserFacingText() {
        assertEquals("电脑未连接", TransferStatusText.failure(UploadResult.failure(
                UploadError.PC_NOT_CONNECTED, -1, "Connection refused")));
        assertEquals("连接电脑超时", TransferStatusText.failure(UploadResult.failure(
                UploadError.TIMEOUT, -1, "Read timed out")));
        assertEquals("电脑服务异常：Service Unavailable", TransferStatusText.failure(UploadResult.failure(
                UploadError.SERVER_ERROR, 503, "Service Unavailable")));
        assertEquals("连接失败：Unexpected error", TransferStatusText.failure(UploadResult.failure(
                UploadError.UNKNOWN, -1, "Unexpected error")));
        assertEquals("电脑服务异常：HTTP 503", TransferStatusText.failure(UploadResult.failure(
                UploadError.SERVER_ERROR, 503, null)));
        assertEquals("连接失败：未知错误", TransferStatusText.failure(UploadResult.failure(
                UploadError.UNKNOWN, -1, null)));
    }
}
