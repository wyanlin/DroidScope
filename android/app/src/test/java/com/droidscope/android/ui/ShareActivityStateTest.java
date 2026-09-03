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
        assertFalse(state.canShowFailure());
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

    @Test
    public void presenterPortReceivesFinishFailureRetryAndCancelStates() {
        Port port = new Port();
        TransferUiState state = new TransferUiState();
        ShareTransferPresenter presenter = new ShareTransferPresenter(state, port);

        presenter.onFailure(UploadResult.failure(UploadError.TIMEOUT, -1, "timeout"), 2);
        assertTrue(port.failure.contains("第 2 个文件发送失败"));
        assertTrue(port.failure.contains("连接电脑超时"));
        assertTrue(port.retryable);
        assertTrue(state.beginRetry());
        presenter.onCanceled();
        assertTrue(port.canceled);
        assertFalse(state.canRetry());

        new ShareTransferPresenter(new TransferUiState(), port).onSuccess(1);
        assertTrue(port.finished == 1);
    }

    @Test
    public void entryWaitsForOldRunAndDestroyedNewEntryDoesNotTransfer() throws Exception {
        TransferGate gate = new TransferGate();
        TransferUiState old = new TransferUiState();
        TransferUiState next = new TransferUiState();
        CountDownLatch oldStarted = new CountDownLatch(1);
        CountDownLatch releaseOld = new CountDownLatch(1);
        CountDownLatch nextRan = new CountDownLatch(1);
        ShareTransferEntry.Listener listener = new ShareTransferEntry.Listener() {
            @Override public void onAcquired() { }
            @Override public void onReleased() { }
        };
        Thread first = new Thread(() -> new ShareTransferEntry(gate, old, listener).run(() -> {
            oldStarted.countDown(); try { releaseOld.await(); } catch (InterruptedException ignored) { }
        }));
        first.start(); assertTrue(oldStarted.await(1, TimeUnit.SECONDS));
        Thread second = new Thread(() -> new ShareTransferEntry(gate, next, listener)
                .run(nextRan::countDown));
        second.start(); assertFalse(nextRan.await(100, TimeUnit.MILLISECONDS));
        releaseOld.countDown(); assertTrue(nextRan.await(1, TimeUnit.SECONDS));
        TransferUiState canceled = new TransferUiState(); canceled.destroy();
        assertFalse(new ShareTransferEntry(gate, canceled, listener).run(nextRan::countDown));
        first.join(1000); second.join(1000);
    }

    @Test
    public void presenterRetryRerunsCoordinatorWithFreshResources() {
        TransferUiState state = new TransferUiState();
        Port port = new Port();
        ShareTransferPresenter presenter = new ShareTransferPresenter(state, port);
        presenter.onFailure(UploadResult.failure(UploadError.PC_NOT_CONNECTED, -1, "offline"), 0);
        assertTrue(state.beginRetry());
        Scenario retry = new Scenario(UploadResult.success(200), UploadResult.success(201));
        retry.run(1);
        assertTrue(retry.pings == 1 && retry.uploads == 1 && retry.success);
    }

    private static final class Port implements ShareTransferPresenter.Port {
        String failure;
        boolean retryable;
        boolean canceled;
        int finished;
        @Override public void showFailure(String text, boolean canRetry) {
            failure = text; retryable = canRetry;
        }
        @Override public void showEmpty() { }
        @Override public void finishSuccess(int count) { finished = count; }
        @Override public void showCanceled() { canceled = true; }
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
