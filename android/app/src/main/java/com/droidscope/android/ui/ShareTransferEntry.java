package com.droidscope.android.ui;

final class ShareTransferEntry {
    interface Listener {
        void onAcquired();
        void onReleased();
    }
    private final TransferGate gate;
    private final TransferUiState state;
    private final Listener listener;

    ShareTransferEntry(TransferGate gate, TransferUiState state, Listener listener) {
        this.gate = gate;
        this.state = state;
        this.listener = listener;
    }

    boolean run(Runnable transfer) {
        if (!gate.acquire(state::isCanceled)) return false;
        listener.onAcquired();
        try {
            if (state.isCanceled()) return false;
            transfer.run();
            return true;
        } finally {
            listener.onReleased();
            gate.release();
        }
    }
}
