package com.droidscope.activity;

import com.droidscope.window.WindowInfo;
import java.util.Collections;
import java.util.List;

public final class ActivityWindowSnapshot {
    private final long capturedAtEpochMs;
    private final List<WindowInfo> windows;
    private final List<ActivityInfo> activities;

    public ActivityWindowSnapshot(long capturedAtEpochMs, List<WindowInfo> windows, List<ActivityInfo> activities) {
        this.capturedAtEpochMs = capturedAtEpochMs;
        this.windows = Collections.unmodifiableList(List.copyOf(windows));
        this.activities = Collections.unmodifiableList(List.copyOf(activities));
    }

    public long capturedAtEpochMs() { return capturedAtEpochMs; }
    public List<WindowInfo> windows() { return windows; }
    public List<ActivityInfo> activities() { return activities; }
}
