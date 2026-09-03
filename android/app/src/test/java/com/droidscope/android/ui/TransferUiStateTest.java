package com.droidscope.android.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TransferUiStateTest {
    @Test
    public void cancelClaimsTerminalAndPreventsRepeatedTerminalUpdates() {
        TransferUiState state = new TransferUiState();

        assertTrue(state.cancelAndClaimTerminal());
        assertTrue(state.isCanceled());
        assertFalse(state.canShowStatus());
        assertFalse(state.tryClaimTerminal());
        assertFalse(state.cancelAndClaimTerminal());
    }

    @Test
    public void destroyCancelsWorkAndPreventsAllUiUpdates() {
        TransferUiState state = new TransferUiState();

        state.destroy();

        assertTrue(state.isCanceled());
        assertFalse(state.canShowStatus());
        assertFalse(state.tryClaimTerminal());
        assertFalse(state.cancelAndClaimTerminal());
    }

    @Test
    public void firstTerminalClaimDisablesLaterTerminalUpdates() {
        TransferUiState state = new TransferUiState();

        assertTrue(state.tryClaimTerminal());
        assertFalse(state.canShowStatus());
        assertFalse(state.tryClaimTerminal());
    }
}
