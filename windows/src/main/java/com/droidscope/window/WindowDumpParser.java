package com.droidscope.window;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the detailed Window Manager blocks shared by Android 13 and 14 dumps. */
public final class WindowDumpParser {
    private static final Pattern FOCUS = Pattern.compile("mCurrentFocus=Window\\{[^ ]+\\s+u\\d+\\s+(.+?)(?:}-|$)");
    private static final Pattern BLOCK = Pattern.compile("(?m)^  Window #(\\d+) Window\\{.*?(?=^  Window #|\\z)", Pattern.DOTALL);
    private static final Pattern HEADER = Pattern.compile("(?s)^\\s*Window #(\\d+)\\s+Window\\{[^ ]+\\s+u\\d+\\s+(.+?)}(?:-|\\*|:|$)");
    private static final Pattern DISPLAY = Pattern.compile("mDisplayId=(\\d+)");
    private static final Pattern SESSION = Pattern.compile("mSession=Session\\{[^ ]+\\s+(\\d+):(\\d+)");
    private static final Pattern SESSION_PID = Pattern.compile("mSession=Session\\{[^ ]+\\s+(\\d+):");
    private static final Pattern OWNER_UID = Pattern.compile("mOwnerUid=(\\d+)");
    private static final Pattern USER_COMPONENT = Pattern.compile("Window\\{[^ ]+\\s+u(\\d+)\\s+([^}]+)}");
    private static final Pattern WINDOW_TYPE = Pattern.compile("\\bty=([A-Z_]+)");

    public WindowSnapshot parse(String dump) {
        String focusedTarget = focusTarget(dump);
        List<WindowInfo> windows = new ArrayList<>();
        Matcher blocks = BLOCK.matcher(dump);
        while (blocks.find()) {
            String raw = blocks.group();
            Matcher header = HEADER.matcher(raw);
            if (!header.find()) continue;
            int order = Integer.parseInt(header.group(1));
            String title = compact(header.group(2));
            Matcher identity = USER_COMPONENT.matcher(raw);
            boolean hasIdentity = identity.find();
            int userId = hasIdentity ? Integer.parseInt(identity.group(1)) : -1;
            String componentName = hasIdentity ? compact(identity.group(2)) : null;
            int displayId = findInt(DISPLAY, raw, 0);
            int pid = findInt(SESSION_PID, raw, -1);
            Matcher ownerUid = OWNER_UID.matcher(raw);
            int uid = ownerUid.find() ? Integer.parseInt(ownerUid.group(1)) : legacySessionUid(raw);
            int windowType = windowType(raw);
            boolean hasSurface = raw.contains("mHasSurface=true");
            boolean visible = hasSurface && raw.contains("isReadyForDisplay()=true");
            String packageName = packageName(title);
            boolean focused = focusedTarget != null && (focusedTarget.equals(title) || focusedTarget.startsWith(title + "-"));
            windows.add(new WindowInfo(order, title, packageName, userId, componentName, displayId, pid, uid,
                    windowType, focused, visible, hasSurface, raw, null));
        }
        return new WindowSnapshot(windows, focusedTarget);
    }

    private static String focusTarget(String dump) {
        Matcher matcher = FOCUS.matcher(dump);
        return matcher.find() ? compact(matcher.group(1)) : null;
    }

    private static String packageName(String title) {
        int slash = title.indexOf('/');
        if (slash > 0) return title.substring(0, slash);
        if (title.startsWith("com.") || title.startsWith("cn.")) {
            int end = title.indexOf(' ');
            return end > 0 ? title.substring(0, end) : title;
        }
        return null;
    }

    private static int findInt(Pattern pattern, String value, int fallback) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
    }

    private static int windowType(String raw) {
        Matcher matcher = WINDOW_TYPE.matcher(raw);
        if (!matcher.find()) return -1;
        switch (matcher.group(1)) {
            case "BASE_APPLICATION": return 1;
            case "APPLICATION": return 2;
            case "APPLICATION_STARTING": return 3;
            default: return -1;
        }
    }

    private static int legacySessionUid(String raw) {
        Matcher session = SESSION.matcher(raw);
        return session.find() ? Integer.parseInt(session.group(2)) : -1;
    }

    private static String compact(String value) {
        return value.replaceAll("\\s+", "").replace("}:", "");
    }
}
