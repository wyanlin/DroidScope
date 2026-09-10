package com.droidscope.inspector;

import com.droidscope.activity.ActivityInfo;
import com.droidscope.surface.SurfaceLayerInfo;
import com.droidscope.surface.SurfaceWindowRelations;
import com.droidscope.window.WindowInfo;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

public final class InspectorSnapshot {
    private final long capturedAtEpochMs;
    private final String serial;
    private final List<WindowInfo> windows;
    private final List<ActivityInfo> activities;
    private final List<SurfaceLayerInfo> surfaces;
    private final SurfaceWindowRelations relations;

    public InspectorSnapshot(long capturedAtEpochMs, String serial, List<WindowInfo> windows,
                             List<ActivityInfo> activities, List<SurfaceLayerInfo> surfaces,
                             SurfaceWindowRelations relations) {
        this.capturedAtEpochMs = capturedAtEpochMs;
        this.serial = serial;
        this.windows = immutable(windows);
        this.activities = immutable(activities);
        this.surfaces = immutable(surfaces);
        this.relations = relations;
    }

    public long capturedAtEpochMs() { return capturedAtEpochMs; }
    public String serial() { return serial; }
    public List<WindowInfo> windows() { return windows; }
    public List<ActivityInfo> activities() { return activities; }
    public List<SurfaceLayerInfo> surfaces() { return surfaces; }
    public SurfaceWindowRelations relations() { return relations; }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
