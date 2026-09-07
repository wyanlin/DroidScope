package com.droidscope.window;

public final class WindowJson {
    private WindowJson() {}

    public static String encode(WindowSnapshot snapshot) {
        StringBuilder json = new StringBuilder("{\"focusedTarget\":");
        quote(json, snapshot.focusedTarget());
        json.append(",\"windows\":[");
        for (int i = 0; i < snapshot.windows().size(); i++) {
            if (i > 0) json.append(',');
            WindowInfo window = snapshot.windows().get(i);
            json.append("{\"order\":").append(window.order())
                    .append(",\"title\":"); quote(json, window.title());
            json.append(",\"packageName\":"); quote(json, window.packageName());
            json.append(",\"displayId\":").append(window.displayId())
                    .append(",\"focused\":").append(window.focused())
                    .append(",\"visible\":").append(window.visible())
                    .append(",\"hasSurface\":").append(window.hasSurface())
                    .append(",\"rawBlock\":"); quote(json, window.rawBlock()).append('}');
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
