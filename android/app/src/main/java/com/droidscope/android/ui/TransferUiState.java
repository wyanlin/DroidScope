package com.droidscope.android.ui;

final class TransferUiState {
    private boolean canceled;
    private boolean terminal;
    private boolean destroyed;

    synchronized boolean cancelAndClaimTerminal() {
        canceled = true;
        if (destroyed || terminal) return false;
        terminal = true;
        return true;
    }

    synchronized void destroy() {
        canceled = true;
        destroyed = true;
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
}
