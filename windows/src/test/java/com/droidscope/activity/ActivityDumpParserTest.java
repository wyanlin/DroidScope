package com.droidscope.activity;

public final class ActivityDumpParserTest {
    public static void main(String[] args) {
        String dump = "topResumedActivity=ActivityRecord{a2bcf31 u0 com.android.launcher3/.uioverrides.QuickstepLauncher t1285}\n"
                + "  * Hist  #0: ActivityRecord{a2bcf31 u0 com.android.launcher3/.uioverrides.QuickstepLauncher t1285}\n"
                + "    app=ProcessRecord{207d6bb 2795:com.android.launcher3/u0a171}\n"
                + "    mActivityComponent=com.android.launcher3/.uioverrides.QuickstepLauncher\n";
        ActivityInfo activity = new ActivityDumpParser().parse(dump).activities().get(0);
        assertEquals(0, activity.userId());
        assertEquals("com.android.launcher3", activity.packageName());
        assertEquals("com.android.launcher3/.uioverrides.QuickstepLauncher", activity.componentName());
        assertEquals(2795, activity.pid());
        assertEquals(ActivityInfo.State.RESUMED, activity.state());
        assertTrue(activity.rawBlock().contains("ActivityRecord"), "raw block should be preserved");
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("expected " + expected + " but got " + actual);
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
