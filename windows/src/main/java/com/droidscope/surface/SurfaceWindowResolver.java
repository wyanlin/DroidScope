package com.droidscope.surface;

import com.droidscope.window.WindowInfo;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SurfaceWindowResolver {
    public SurfaceWindowRelations resolve(List<WindowInfo> windows, List<SurfaceLayerInfo> layers) {
        Map<String, List<SurfaceLayerInfo>> candidatesByWindow = new LinkedHashMap<>();
        Map<String, SurfaceWindowRelation> byWindow = new LinkedHashMap<>();
        for (WindowInfo window : windows) {
            List<SurfaceLayerInfo> candidates = new ArrayList<>();
            for (SurfaceLayerInfo layer : layers) {
                if (matches(window, layer)) candidates.add(layer);
            }
            candidatesByWindow.put(window.id(), candidates);
            byWindow.put(window.id(), relationForWindow(window.id(), candidates));
        }

        Map<Integer, SurfaceWindowRelation> bySurface = new LinkedHashMap<>();
        for (SurfaceLayerInfo layer : layers) {
            List<WindowInfo> matches = new ArrayList<>();
            for (WindowInfo window : windows) {
                if (candidatesByWindow.get(window.id()).contains(layer)) matches.add(window);
            }
            if (matches.size() == 1 && candidatesByWindow.get(matches.get(0).id()).size() == 1) {
                bySurface.put(layer.id(), new SurfaceWindowRelation(matches.get(0).id(), layer.id(),
                        SurfaceWindowRelationKind.EXACT_METADATA, 1));
            } else if (!matches.isEmpty()) {
                int count = matches.size() == 1 ? candidatesByWindow.get(matches.get(0).id()).size() : matches.size();
                bySurface.put(layer.id(), new SurfaceWindowRelation(null, layer.id(),
                        SurfaceWindowRelationKind.AMBIGUOUS, count));
            } else {
                bySurface.put(layer.id(), new SurfaceWindowRelation(null, layer.id(),
                        SurfaceWindowRelationKind.UNLINKED, 0));
            }
        }
        return new SurfaceWindowRelations(byWindow, bySurface);
    }

    private static boolean matches(WindowInfo window, SurfaceLayerInfo layer) {
        Integer ownerUid = layer.metadataInt32(1);
        Integer windowType = layer.metadataInt32(2);
        Integer ownerPid = layer.metadataInt32(6);
        return window.title() != null && window.windowType() >= 0 && layer.canonicalName() != null
                && window.title().equals(layer.canonicalName())
                && ownerUid != null && ownerUid == window.uid()
                && windowType != null && windowType == window.windowType()
                && ownerPid != null && ownerPid == window.pid();
    }

    private static SurfaceWindowRelation relationForWindow(String windowId, List<SurfaceLayerInfo> candidates) {
        if (candidates.isEmpty()) return new SurfaceWindowRelation(null, null,
                SurfaceWindowRelationKind.UNLINKED, 0);
        if (candidates.size() > 1) return new SurfaceWindowRelation(null, null,
                SurfaceWindowRelationKind.AMBIGUOUS, candidates.size());
        return new SurfaceWindowRelation(windowId, candidates.get(0).id(),
                SurfaceWindowRelationKind.EXACT_METADATA, 1);
    }
}
