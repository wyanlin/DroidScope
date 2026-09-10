package com.droidscope.activity;

import com.droidscope.window.WindowInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ActivityWindowResolver {
    public ActivityWindowSnapshot resolve(List<ActivityInfo> activities, List<WindowInfo> windows) {
        Map<String, ActivityInfo> byKey = new HashMap<>();
        for (ActivityInfo activity : activities) byKey.put(activity.id(), activity);

        Map<String, List<String>> windowIdsByActivity = new HashMap<>();
        List<WindowInfo> resolvedWindows = new ArrayList<>();
        for (WindowInfo window : windows) {
            String activityId = relatedActivityId(window, byKey);
            resolvedWindows.add(window.withRelatedActivityId(activityId));
            if (activityId != null) windowIdsByActivity.computeIfAbsent(activityId, ignored -> new ArrayList<>()).add(window.id());
        }

        List<ActivityInfo> resolvedActivities = new ArrayList<>();
        for (ActivityInfo activity : activities) {
            resolvedActivities.add(activity.withRelatedWindowIds(
                    windowIdsByActivity.getOrDefault(activity.id(), List.of())));
        }
        return new ActivityWindowSnapshot(System.currentTimeMillis(), resolvedWindows, resolvedActivities);
    }

    private static String relatedActivityId(WindowInfo window, Map<String, ActivityInfo> byKey) {
        if (window.userId() < 0 || window.packageName() == null || window.componentName() == null) return null;
        String id = "u" + window.userId() + ":" + window.componentName();
        ActivityInfo activity = byKey.get(id);
        return activity != null && window.packageName().equals(activity.packageName()) ? id : null;
    }
}
