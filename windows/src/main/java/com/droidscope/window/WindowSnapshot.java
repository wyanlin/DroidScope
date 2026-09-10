package com.droidscope.window;

import java.util.List;

public final class WindowSnapshot {
    private final List<WindowInfo> windows;
    private final String focusedTarget;

    public WindowSnapshot(List<WindowInfo> windows, String focusedTarget) {
        this.windows = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(windows));
        this.focusedTarget = focusedTarget;
    }

    public List<WindowInfo> windows() { return windows; }
    public String focusedTarget() { return focusedTarget; }
}
