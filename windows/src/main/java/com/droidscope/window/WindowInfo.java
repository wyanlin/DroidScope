package com.droidscope.window;

public final class WindowInfo {
    private final int order;
    private final String title;
    private final String packageName;
    private final int userId;
    private final String componentName;
    private final int displayId;
    private final int pid;
    private final int uid;
    private final boolean focused;
    private final boolean visible;
    private final boolean hasSurface;
    private final String rawBlock;
    private final String relatedActivityId;

    public WindowInfo(int order, String title, String packageName, int displayId, int pid, int uid,
                      boolean focused, boolean visible, boolean hasSurface, String rawBlock) {
        this(order, title, packageName, -1, null, displayId, pid, uid, focused, visible, hasSurface, rawBlock, null);
    }

    public WindowInfo(int order, String title, String packageName, int userId, String componentName,
                      int displayId, int pid, int uid, boolean focused, boolean visible,
                      boolean hasSurface, String rawBlock, String relatedActivityId) {
        this.order = order;
        this.title = title;
        this.packageName = packageName;
        this.userId = userId;
        this.componentName = componentName;
        this.displayId = displayId;
        this.pid = pid;
        this.uid = uid;
        this.focused = focused;
        this.visible = visible;
        this.hasSurface = hasSurface;
        this.rawBlock = rawBlock;
        this.relatedActivityId = relatedActivityId;
    }

    public int order() { return order; }
    public String title() { return title; }
    public String packageName() { return packageName; }
    public int userId() { return userId; }
    public String componentName() { return componentName; }
    public int displayId() { return displayId; }
    public int pid() { return pid; }
    public int uid() { return uid; }
    public boolean focused() { return focused; }
    public boolean visible() { return visible; }
    public boolean hasSurface() { return hasSurface; }
    public String rawBlock() { return rawBlock; }
    public String relatedActivityId() { return relatedActivityId; }
    public String id() { return "window:" + order; }

    public WindowInfo withRelatedActivityId(String activityId) {
        return new WindowInfo(order, title, packageName, userId, componentName, displayId, pid, uid,
                focused, visible, hasSurface, rawBlock, activityId);
    }
}
