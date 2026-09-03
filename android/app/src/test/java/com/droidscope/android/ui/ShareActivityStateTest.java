package com.droidscope.android.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.droidscope.android.transfer.UploadManager;
import com.droidscope.android.transfer.UploadError;
import com.droidscope.android.transfer.UploadResult;

import org.junit.Test;

public final class ShareActivityStateTest {
    @Test
    public void retryableNetworkFailureAllowsRetryAndReopensUiState() {
        TransferUiState state = new TransferUiState();

        assertTrue(state.claimFailure(UploadManager.isRetryable(
                UploadResult.failure(UploadError.PC_NOT_CONNECTED, -1, "offline"))));
        assertTrue(state.canRetry());
        assertFalse(state.canShowStatus());

        assertTrue(state.beginRetry());
        assertTrue(state.canShowStatus());
        assertFalse(state.canRetry());
    }

    @Test
    public void timeoutAllowsRetryButServerValidationFailureDoesNot() {
        TransferUiState timeout = new TransferUiState();
        TransferUiState serverFailure = new TransferUiState();

        assertTrue(timeout.claimFailure(UploadManager.isRetryable(
                UploadResult.failure(UploadError.TIMEOUT, -1, "timeout"))));
        assertTrue(timeout.canRetry());
        assertTrue(serverFailure.claimFailure(UploadManager.isRetryable(
                UploadResult.failure(UploadError.SERVER_ERROR, 400, "invalid file"))));
        assertFalse(serverFailure.canRetry());
        assertFalse(serverFailure.beginRetry());
    }

    @Test
    public void destroyedActivityCannotRetryOrResumeUiUpdates() {
        TransferUiState state = new TransferUiState();
        assertTrue(state.claimFailure(UploadManager.isRetryable(
                UploadResult.failure(UploadError.PC_NOT_CONNECTED, -1, "offline"))));

        state.destroy();

        assertFalse(state.beginRetry());
        assertFalse(state.canShowStatus());
    }

    @Test
    public void cancelAfterRetryableFailureStopsFurtherRetries() {
        TransferUiState state = new TransferUiState();
        assertTrue(state.claimFailure(true));

        assertTrue(state.cancelAndClaimTerminal());

        assertTrue(state.isCanceled());
        assertFalse(state.canRetry());
    }
}
