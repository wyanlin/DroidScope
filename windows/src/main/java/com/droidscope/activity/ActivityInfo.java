package com.droidscope.activity;

import java.util.Collections;
import java.util.List;

public final class ActivityInfo {
    public enum State { RESUMED, PAUSED, UNKNOWN }

    private final int userId;
    private final String packageName;
    private final String componentName;
    private final int pid;
    private final State state;
    private final String rawBlock;
    private final List<String> relatedWindowIds;

    public ActivityInfo(int userId, String packageName, String componentName, int pid,
                        State state, String rawBlock, List<String> relatedWindowIds) {
        this.userId = userId;
        this.packageName = packageName;
        this.componentName = componentName;
        this.pid = pid;
        this.state = state;
        this.rawBlock = rawBlock;
        this.relatedWindowIds = Collections.unmodifiableList(List.copyOf(relatedWindowIds));
    }

    public int userId() { return userId; }
    public String packageName() { return packageName; }
    public String componentName() { return componentName; }
    public int pid() { return pid; }
    public State state() { return state; }
    public String rawBlock() { return rawBlock; }
    public List<String> relatedWindowIds() { return relatedWindowIds; }
    public String id() { return "u" + userId + ":" + componentName; }

    public ActivityInfo withRelatedWindowIds(List<String> windowIds) {
        return new ActivityInfo(userId, packageName, componentName, pid, state, rawBlock, windowIds);
    }
}
