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
            int displayId = findInt(DISPLAY, raw, 0);
            Matcher session = SESSION.matcher(raw);
            int pid = session.find() ? Integer.parseInt(session.group(1)) : -1;
            session = SESSION.matcher(raw);
            int uid = session.find() ? Integer.parseInt(session.group(2)) : -1;
            boolean hasSurface = raw.contains("mHasSurface=true");
            boolean visible = hasSurface && raw.contains("isReadyForDisplay()=true");
            String packageName = packageName(title);
            boolean focused = focusedTarget != null && (focusedTarget.equals(title) || focusedTarget.startsWith(title + "-"));
            windows.add(new WindowInfo(order, title, packageName, displayId, pid, uid, focused, visible, hasSurface, raw));
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

    private static String compact(String value) {
        return value.replaceAll("\\s+", "").replace("}:", "");
    }
}
