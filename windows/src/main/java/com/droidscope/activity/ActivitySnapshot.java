package com.droidscope.activity;

import java.util.Collections;
import java.util.List;

public final class ActivitySnapshot {
    private final List<ActivityInfo> activities;

    public ActivitySnapshot(List<ActivityInfo> activities) {
        this.activities = Collections.unmodifiableList(List.copyOf(activities));
    }

    public List<ActivityInfo> activities() { return activities; }
}
