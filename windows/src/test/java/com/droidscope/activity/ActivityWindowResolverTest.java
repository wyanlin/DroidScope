package com.droidscope.activity;

import com.droidscope.window.WindowInfo;
import java.util.List;

public final class ActivityWindowResolverTest {
    public static void main(String[] args) {
        ActivityInfo activity = activity(0, "com.demo", "com.demo/.MainActivity");
        WindowInfo matched = window(0, 0, "com.demo", "com.demo/.MainActivity");
        WindowInfo wrongUser = window(1, 10, "com.demo", "com.demo/.MainActivity");
        WindowInfo wrongPackage = window(2, 0, "com.other", "com.demo/.MainActivity");
        WindowInfo wrongComponent = window(3, 0, "com.demo", "com.demo/.OtherActivity");

        ActivityWindowSnapshot snapshot = new ActivityWindowResolver()
                .resolve(List.of(activity), List.of(matched, wrongUser, wrongPackage, wrongComponent));

        assertEquals(activity.id(), snapshot.windows().get(0).relatedActivityId());
        assertEquals(null, snapshot.windows().get(1).relatedActivityId());
        assertEquals(null, snapshot.windows().get(2).relatedActivityId());
        assertEquals(null, snapshot.windows().get(3).relatedActivityId());
        assertEquals(List.of(matched.id()), snapshot.activities().get(0).relatedWindowIds());
    }

    private static ActivityInfo activity(int userId, String packageName, String componentName) {
        return new ActivityInfo(userId, packageName, componentName, 1234,
                ActivityInfo.State.RESUMED, "activity", List.of());
    }

    private static WindowInfo window(int order, int userId, String packageName, String componentName) {
        return new WindowInfo(order, componentName, packageName, userId, componentName,
                0, 1234, 1000, false, true, true, "window", null);
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("expected " + expected + " but got " + actual);
        }
    }
}
