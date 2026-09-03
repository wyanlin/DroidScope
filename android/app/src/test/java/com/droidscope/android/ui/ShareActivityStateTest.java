package com.droidscope.android.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.droidscope.android.transfer.UploadManager;
import com.droidscope.android.transfer.UploadError;
import com.droidscope.android.transfer.UploadResult;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    @Test
    public void singleFileSuccessFinishesAndEmptyListDoesNotCreateUpload() {
        Scenario single = new Scenario(UploadResult.success(200), UploadResult.success(201));
        single.run(1);
        assertTrue(single.success);
        assertTrue(single.uploads == 1);

        Scenario empty = new Scenario(UploadResult.success(200));
        empty.run(0);
        assertTrue(empty.empty);
        assertTrue(empty.uploads == 0);
    }

    @Test
    public void multipleFilesRunInOrderAndStopOnFailureWithOriginalReason() {
        Scenario scenario = new Scenario(UploadResult.success(200), UploadResult.success(201),
                UploadResult.failure(UploadError.SERVER_ERROR, 422, "invalid"));
        scenario.run(3);
        assertTrue(scenario.uploads == 2);
        assertTrue(scenario.failure.getError() == UploadError.SERVER_ERROR);
        assertTrue(scenario.failureFile == 2);
        assertFalse(scenario.success);
    }

    @Test
    public void retryRunsCreateFreshPingAndUploadOperations() {
        Scenario scenario = new Scenario(UploadResult.success(200), UploadResult.success(201),
                UploadResult.success(200), UploadResult.success(201));

        scenario.run(1);
        scenario.run(1);

        assertTrue(scenario.pings == 2);
        assertTrue(scenario.uploads == 2);
    }

    private static final class Scenario implements ShareTransferCoordinator.Factory,
            ShareTransferCoordinator.Callback {
        private final List<UploadResult> results;
        int pings;
        int uploads;
        boolean success;
        boolean empty;
        UploadResult failure;
        int failureFile;

        Scenario(UploadResult... results) { this.results = new ArrayList<>(Arrays.asList(results)); }
        void run(int count) {
            List<com.droidscope.android.model.ShareFile> files = new ArrayList<>();
            for (int i = 0; i < count; i++) files.add(null);
            new ShareTransferCoordinator(this, this).transfer(files);
        }
        @Override public ShareTransferCoordinator.Ping createPing() {
            pings++;
            return () -> results.remove(0);
        }
        @Override public ShareTransferCoordinator.Upload createUpload(
                com.droidscope.android.model.ShareFile file, int number, int count) {
            uploads++;
            return () -> results.remove(0);
        }
        @Override public boolean isCanceled() { return false; }
        @Override public void onFailure(UploadResult result, int number) {
            failure = result; failureFile = number;
        }
        @Override public void onEmpty() { empty = true; }
        @Override public void onSuccess(int count) { success = true; }
        @Override public void onCanceled() { }
    }
}
