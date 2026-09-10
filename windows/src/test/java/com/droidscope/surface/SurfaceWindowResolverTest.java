package com.droidscope.surface;

import com.droidscope.window.WindowInfo;
import java.util.List;
import java.util.Map;

public final class SurfaceWindowResolverTest {
    public static void main(String[] args) {
        WindowInfo window = window(2795, 10171, 1);
        SurfaceLayerInfo exact = layer(5635, window.title(), 10171, 1, 2795);
        SurfaceWindowRelations exactResult = resolve(window, exact);
        assertEquals(SurfaceWindowRelationKind.EXACT_METADATA,
                exactResult.forWindow(window.id()).kind());
        assertEquals(5635, exactResult.forWindow(window.id()).surfaceId());
        assertEquals(window.id(), exactResult.forSurface(5635).windowId());

        SurfaceLayerInfo wrongPid = layer(5635, window.title(), 10171, 1, 9999);
        assertEquals(SurfaceWindowRelationKind.UNLINKED,
                resolve(window, wrongPid).forWindow(window.id()).kind());

        SurfaceLayerInfo missingMetadata = layer(5635, window.title(), null, null, null);
        assertEquals(SurfaceWindowRelationKind.UNLINKED,
                resolve(window, missingMetadata).forWindow(window.id()).kind());

        SurfaceLayerInfo duplicate = layer(5636, window.title(), 10171, 1, 2795);
        SurfaceWindowRelations ambiguous = resolve(window, exact, duplicate);
        assertEquals(SurfaceWindowRelationKind.AMBIGUOUS,
                ambiguous.forWindow(window.id()).kind());
        assertEquals(2, ambiguous.forWindow(window.id()).candidateCount());
        assertEquals(SurfaceWindowRelationKind.AMBIGUOUS, ambiguous.forSurface(5635).kind());

        SurfaceLayerInfo childLike = layer(5637, window.title(), null, null, null);
        assertEquals(SurfaceWindowRelationKind.UNLINKED,
                resolve(window, childLike).forWindow(window.id()).kind());
    }

    private static SurfaceWindowRelations resolve(WindowInfo window, SurfaceLayerInfo... layers) {
        return new SurfaceWindowResolver().resolve(List.of(window), List.of(layers));
    }

    private static WindowInfo window(int pid, int uid, int type) {
        return new WindowInfo(0, "com.demo/com.demo.MainActivity", "com.demo", 0,
                "com.demo/com.demo.MainActivity", 0, pid, uid, type,
                false, true, true, "raw", null);
    }

    private static SurfaceLayerInfo layer(int id, String name, Integer uid, Integer type, Integer pid) {
        Map<Integer, byte[]> metadata = new java.util.LinkedHashMap<>();
        if (uid != null) metadata.put(1, int32(uid));
        if (type != null) metadata.put(2, int32(type));
        if (pid != null) metadata.put(6, int32(pid));
        return new SurfaceLayerInfo(id, name + "#" + id, "Layer", null, List.of(),
                null, null, null, null, null, null, metadata, null);
    }

    private static byte[] int32(int value) {
        return new byte[] {(byte) value, (byte) (value >>> 8), (byte) (value >>> 16), (byte) (value >>> 24)};
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("expected " + expected + " but got " + actual);
        }
    }
}
