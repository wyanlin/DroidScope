package com.droidscope.android.ui;

final class TransferUiState {
    private boolean canceled;
    private boolean terminal;
    private boolean destroyed;
    private boolean retryableFailure;

    synchronized boolean cancelAndClaimTerminal() {
        if (canceled || destroyed) return false;
        canceled = true;
        retryableFailure = false;
        terminal = true;
        return true;
    }

    synchronized void destroy() {
        canceled = true;
        destroyed = true;
        retryableFailure = false;
    }

    synchronized boolean isCanceled() {
        return canceled;
    }

    synchronized boolean isDestroyed() {
        return destroyed;
    }

    synchronized boolean canShowStatus() {
        return !destroyed && !terminal;
    }

    synchronized boolean tryClaimTerminal() {
        if (destroyed || terminal) return false;
        terminal = true;
        return true;
    }

    synchronized boolean claimFailure(boolean retryable) {
        if (!tryClaimTerminal()) return false;
        retryableFailure = retryable;
        return true;
    }

    synchronized boolean canRetry() {
        return !canceled && !destroyed && terminal && retryableFailure;
    }

    synchronized boolean beginRetry() {
        if (!canRetry()) return false;
        terminal = false;
        retryableFailure = false;
        return true;
    }
}
