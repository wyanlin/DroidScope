package com.droidscope.activity;

import com.droidscope.window.WindowInfo;

public final class ActivityWindowJson {
    private ActivityWindowJson() {}

    public static String encode(ActivityWindowSnapshot snapshot) {
        StringBuilder json = new StringBuilder("{\"capturedAtEpochMs\":")
                .append(snapshot.capturedAtEpochMs()).append(",\"windows\":[");
        for (int i = 0; i < snapshot.windows().size(); i++) {
            if (i > 0) json.append(',');
            WindowInfo window = snapshot.windows().get(i);
            json.append("{\"id\":"); quote(json, window.id());
            json.append(",\"order\":").append(window.order())
                    .append(",\"title\":"); quote(json, window.title());
            json.append(",\"packageName\":"); quote(json, window.packageName())
                    .append(",\"componentName\":"); quote(json, window.componentName());
            json.append(",\"userId\":").append(window.userId())
                    .append(",\"displayId\":").append(window.displayId())
                    .append(",\"pid\":").append(window.pid())
                    .append(",\"uid\":").append(window.uid())
                    .append(",\"focused\":").append(window.focused())
                    .append(",\"visible\":").append(window.visible())
                    .append(",\"hasSurface\":").append(window.hasSurface())
                    .append(",\"relatedActivityId\":"); quote(json, window.relatedActivityId());
            json.append(",\"rawBlock\":"); quote(json, window.rawBlock()).append('}');
        }
        json.append("],\"activities\":[");
        for (int i = 0; i < snapshot.activities().size(); i++) {
            if (i > 0) json.append(',');
            ActivityInfo activity = snapshot.activities().get(i);
            json.append("{\"id\":"); quote(json, activity.id());
            json.append(",\"userId\":").append(activity.userId())
                    .append(",\"packageName\":"); quote(json, activity.packageName());
            json.append(",\"componentName\":"); quote(json, activity.componentName())
                    .append(",\"pid\":").append(activity.pid())
                    .append(",\"state\":"); quote(json, activity.state().name());
            json.append(",\"relatedWindowIds\":[");
            for (int j = 0; j < activity.relatedWindowIds().size(); j++) {
                if (j > 0) json.append(',');
                quote(json, activity.relatedWindowIds().get(j));
            }
            json.append("],\"rawBlock\":"); quote(json, activity.rawBlock()).append('}');
        }
        return json.append("]}\n").toString();
    }

    private static StringBuilder quote(StringBuilder out, String value) {
        if (value == null) return out.append("null");
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c == '\n') out.append("\\n");
            else if (c == '\r') out.append("\\r");
            else if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
            else out.append(c);
        }
        return out.append('"');
    }
}
