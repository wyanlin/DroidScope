package com.droidscope.android.ui;

final class TransferGate {
    interface CancellationSignal {
        boolean isCanceled();
    }

    private boolean active;

    synchronized boolean acquire(CancellationSignal cancellation) {
        while (active) {
            if (cancellation.isCanceled()) return false;
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        if (cancellation.isCanceled()) return false;
        active = true;
        return true;
    }

    synchronized void release() {
        active = false;
        notifyAll();
    }
}
