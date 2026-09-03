package com.droidscope.android.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.droidscope.android.transfer.UploadManager;
import com.droidscope.android.transfer.UploadError;
import com.droidscope.android.transfer.UploadResult;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

    @Test
    public void rotatedActivityWaitsForOldTransferToReleaseGateBeforeStarting() throws Exception {
        TransferGate gate = new TransferGate();
        TransferUiState oldState = new TransferUiState();
        TransferUiState newState = new TransferUiState();
        CountDownLatch newAcquired = new CountDownLatch(1);
        Thread newActivity = new Thread(() -> {
            if (gate.acquire(newState::isCanceled)) newAcquired.countDown();
        });

        assertTrue(gate.acquire(oldState::isCanceled));
        newActivity.start();
        assertFalse(newAcquired.await(100, TimeUnit.MILLISECONDS));

        oldState.destroy();
        gate.release();

        assertTrue(newAcquired.await(1, TimeUnit.SECONDS));
        gate.release();
        newActivity.join(1000);
    }

    @Test
    public void destroyedActivityWaitingForGateNeverStartsTransfer() throws Exception {
        TransferGate gate = new TransferGate();
        TransferUiState oldState = new TransferUiState();
        TransferUiState waitingState = new TransferUiState();
        CountDownLatch waitingReturned = new CountDownLatch(1);

        assertTrue(gate.acquire(oldState::isCanceled));
        Thread waitingActivity = new Thread(() -> {
            assertFalse(gate.acquire(waitingState::isCanceled));
            waitingReturned.countDown();
        });
        waitingActivity.start();
        waitingState.destroy();
        waitingActivity.interrupt();

        assertTrue(waitingReturned.await(1, TimeUnit.SECONDS));
        gate.release();
        waitingActivity.join(1000);
    }
}
