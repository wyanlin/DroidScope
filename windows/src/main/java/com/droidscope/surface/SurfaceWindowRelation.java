package com.droidscope.surface;

public final class SurfaceWindowRelation {
    private final String windowId;
    private final Integer surfaceId;
    private final SurfaceWindowRelationKind kind;
    private final int candidateCount;

    public SurfaceWindowRelation(String windowId, Integer surfaceId,
                                 SurfaceWindowRelationKind kind, int candidateCount) {
        this.windowId = windowId;
        this.surfaceId = surfaceId;
        this.kind = kind;
        this.candidateCount = candidateCount;
    }

    public String windowId() { return windowId; }
    public Integer surfaceId() { return surfaceId; }
    public SurfaceWindowRelationKind kind() { return kind; }
    public int candidateCount() { return candidateCount; }
}
