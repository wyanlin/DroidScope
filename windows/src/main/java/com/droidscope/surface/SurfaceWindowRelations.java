package com.droidscope.surface;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SurfaceWindowRelations {
    private final Map<String, SurfaceWindowRelation> byWindowId;
    private final Map<Integer, SurfaceWindowRelation> bySurfaceId;

    SurfaceWindowRelations(Map<String, SurfaceWindowRelation> byWindowId,
                           Map<Integer, SurfaceWindowRelation> bySurfaceId) {
        this.byWindowId = Collections.unmodifiableMap(new LinkedHashMap<>(byWindowId));
        this.bySurfaceId = Collections.unmodifiableMap(new LinkedHashMap<>(bySurfaceId));
    }

    public SurfaceWindowRelation forWindow(String windowId) { return byWindowId.get(windowId); }
    public SurfaceWindowRelation forSurface(int surfaceId) { return bySurfaceId.get(surfaceId); }
    public Map<String, SurfaceWindowRelation> byWindowId() { return byWindowId; }
    public Map<Integer, SurfaceWindowRelation> bySurfaceId() { return bySurfaceId; }
}
