package com.droidscope.surface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SurfaceSnapshot {
    private final List<SurfaceLayerInfo> layers;

    public SurfaceSnapshot(List<SurfaceLayerInfo> layers) {
        this.layers = Collections.unmodifiableList(new ArrayList<>(layers));
    }

    public List<SurfaceLayerInfo> layers() { return layers; }
}
