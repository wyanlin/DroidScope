package com.droidscope.activity;

import com.droidscope.window.WindowInfo;
import java.util.List;

public final class ActivityWindowJsonTest {
    public static void main(String[] args) {
        ActivityInfo activity = new ActivityInfo(0, "com.demo", "com.demo", 1234,
                ActivityInfo.State.RESUMED, "activity\nraw", List.of("window:0"));
        WindowInfo window = new WindowInfo(0, "com.demo/.MainActivity", "com.demo", 0,
                "com.demo/.MainActivity", 0, 1234, 1000, true, true, true, "window\nraw", activity.id());
        String json = ActivityWindowJson.encode(new ActivityWindowSnapshot(123, List.of(window), List.of(activity)));
        assertContains(json, "\"capturedAtEpochMs\":123");
        assertContains(json, "\"relatedActivityId\":\"u0:com.demo\"");
        assertContains(json, "\"relatedWindowIds\":[\"window:0\"]");
        assertContains(json, "activity\\nraw");
    }

    private static void assertContains(String value, String expected) {
        if (!value.contains(expected)) throw new AssertionError("missing " + expected + " in " + value);
    }
}
