package com.droidscope.android.ui;

final class ShareTransferEntry {
    private final TransferGate gate;
    private final TransferUiState state;

    ShareTransferEntry(TransferGate gate, TransferUiState state) {
        this.gate = gate;
        this.state = state;
    }

    boolean run(Runnable transfer) {
        if (!gate.acquire(state::isCanceled)) return false;
        try {
            if (state.isCanceled()) return false;
            transfer.run();
            return true;
        } finally {
            gate.release();
        }
    }
}
