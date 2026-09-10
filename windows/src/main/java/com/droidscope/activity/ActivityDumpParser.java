package com.droidscope.activity;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ActivityDumpParser {
    private static final Pattern BLOCK = Pattern.compile(
            "(?ms)^\\s*\\*?\\s*Hist\\s+#\\d+:.*?(?=^\\s*\\*?\\s*Hist\\s+#\\d+:|^\\s*(?:ResumedActivity|mFocusedApp)=|\\z)");
    private static final Pattern RECORD = Pattern.compile(
            "ActivityRecord\\{[^ ]+\\s+u(\\d+)\\s+([^\\s}]+)");
    private static final Pattern COMPONENT = Pattern.compile("(?m)^\\s*mActivityComponent=([^\\s]+)");
    private static final Pattern PID = Pattern.compile("app=ProcessRecord\\{[^ ]+\\s+(\\d+):");
    private static final Pattern RESUMED = Pattern.compile(
            "(?:topResumedActivity|ResumedActivity)=ActivityRecord\\{[^ ]+\\s+u(\\d+)\\s+([^\\s}]+)");
    private static final Pattern PAUSED = Pattern.compile(
            "mLastPausedActivity:\\s*ActivityRecord\\{[^ ]+\\s+u(\\d+)\\s+([^\\s}]+)");

    public ActivitySnapshot parse(String dump) {
        String value = dump == null ? "" : dump;
        String[] resumed = marker(value, RESUMED);
        String[] paused = marker(value, PAUSED);
        List<ActivityInfo> activities = new ArrayList<>();
        Matcher blocks = BLOCK.matcher(value);
        while (blocks.find()) {
            String raw = blocks.group();
            Matcher record = RECORD.matcher(raw);
            if (!record.find()) continue;
            int userId = Integer.parseInt(record.group(1));
            String recordComponent = record.group(2);
            Matcher component = COMPONENT.matcher(raw);
            String componentName = component.find() ? component.group(1) : recordComponent;
            String packageName = packageName(componentName);
            Matcher pidMatcher = PID.matcher(raw);
            int pid = pidMatcher.find() ? Integer.parseInt(pidMatcher.group(1)) : -1;
            ActivityInfo.State state = matches(resumed, userId, componentName)
                    ? ActivityInfo.State.RESUMED
                    : matches(paused, userId, componentName) ? ActivityInfo.State.PAUSED : ActivityInfo.State.UNKNOWN;
            activities.add(new ActivityInfo(userId, packageName, componentName, pid, state, raw, List.of()));
        }
        return new ActivitySnapshot(activities);
    }

    private static String[] marker(String dump, Pattern pattern) {
        Matcher matcher = pattern.matcher(dump);
        return matcher.find() ? new String[] { matcher.group(1), matcher.group(2) } : null;
    }

    private static boolean matches(String[] marker, int userId, String component) {
        return marker != null && Integer.parseInt(marker[0]) == userId && marker[1].equals(component);
    }

    private static String packageName(String component) {
        if (component == null) return null;
        int slash = component.indexOf('/');
        return slash > 0 ? component.substring(0, slash) : component;
    }
}
