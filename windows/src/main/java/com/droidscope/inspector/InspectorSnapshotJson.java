package com.droidscope.inspector;

import com.droidscope.activity.ActivityInfo;
import com.droidscope.surface.SurfaceLayerInfo;
import com.droidscope.surface.SurfaceWindowRelation;
import com.droidscope.window.WindowInfo;

public final class InspectorSnapshotJson {
    private InspectorSnapshotJson() {}

    public static String encode(InspectorSnapshot snapshot) {
        StringBuilder out = new StringBuilder("{\"capturedAtEpochMs\":")
                .append(snapshot.capturedAtEpochMs()).append(",\"serial\":");
        quote(out, snapshot.serial()).append(",\"windows\":[");
        for (int i = 0; i < snapshot.windows().size(); i++) {
            if (i > 0) out.append(',');
            WindowInfo window = snapshot.windows().get(i);
            out.append("{\"id\":"); quote(out, window.id()).append(",\"title\":"); quote(out, window.title());
            out.append(",\"packageName\":"); quote(out, window.packageName()).append(",\"componentName\":"); quote(out, window.componentName());
            out.append(",\"userId\":").append(window.userId()).append(",\"displayId\":").append(window.displayId());
            out.append(",\"pid\":").append(window.pid()).append(",\"uid\":").append(window.uid());
            out.append(",\"windowType\":").append(window.windowType()).append(",\"focused\":").append(window.focused());
            out.append(",\"visible\":").append(window.visible()).append(",\"hasSurface\":").append(window.hasSurface());
            out.append(",\"relatedActivityId\":"); quote(out, window.relatedActivityId()).append(",\"rawBlock\":"); quote(out, window.rawBlock()).append('}');
        }
        out.append("],\"activities\":[");
        for (int i = 0; i < snapshot.activities().size(); i++) {
            if (i > 0) out.append(',');
            ActivityInfo activity = snapshot.activities().get(i);
            out.append("{\"id\":"); quote(out, activity.id()).append(",\"userId\":").append(activity.userId());
            out.append(",\"packageName\":"); quote(out, activity.packageName()).append(",\"componentName\":"); quote(out, activity.componentName());
            out.append(",\"pid\":").append(activity.pid()).append(",\"state\":"); quote(out, activity.state().name());
            out.append(",\"relatedWindowIds\":[");
            for (int j = 0; j < activity.relatedWindowIds().size(); j++) { if (j > 0) out.append(','); quote(out, activity.relatedWindowIds().get(j)); }
            out.append("],\"rawBlock\":"); quote(out, activity.rawBlock()).append('}');
        }
        out.append("],\"surfaces\":[");
        for (int i = 0; i < snapshot.surfaces().size(); i++) {
            if (i > 0) out.append(',');
            SurfaceLayerInfo layer = snapshot.surfaces().get(i);
            SurfaceWindowRelation relation = snapshot.relations().forSurface(layer.id());
            out.append("{\"id\":").append(layer.id()).append(",\"name\":"); quote(out, layer.rawName());
            out.append(",\"canonicalName\":"); quote(out, layer.canonicalName()).append(",\"type\":"); quote(out, layer.type());
            out.append(",\"parentId\":"); numberOrNull(out, layer.parentId()).append(",\"childIds\":[");
            for (int j = 0; j < layer.childIds().size(); j++) { if (j > 0) out.append(','); out.append(layer.childIds().get(j)); }
            out.append("],\"layerStack\":"); numberOrNull(out, layer.layerStack()).append(",\"z\":"); numberOrNull(out, layer.z());
            out.append(",\"bounds\":"); rect(out, layer.bounds()).append(",\"screenBounds\":"); rect(out, layer.screenBounds());
            out.append(",\"hasBuffer\":").append(layer.hasBuffer()).append(",\"activeBuffer\":"); buffer(out, layer.activeBuffer());
            out.append(",\"currentFrame\":"); numberOrNull(out, layer.currentFrame()).append(",\"inputWindowInfo\":"); inputWindowInfo(out, layer);
            out.append(",\"metadata\":{");
            boolean first = true; for (Integer key : layer.metadata().keySet()) { if (!first) out.append(','); first = false; quote(out, String.valueOf(key)).append(':'); Integer decoded = layer.metadataInt32(key); quote(out, decoded == null ? bytesHex(layer.metadata().get(key)) : String.valueOf(decoded)); }
            out.append("},\"relationKind\":"); quote(out, relation.kind().name()).append(",\"relatedWindowId\":"); quote(out, relation.windowId()).append('}');
        }
        out.append("],\"relations\":{\"surfaceWindow\":[");
        boolean first = true;
        for (SurfaceWindowRelation relation : snapshot.relations().byWindowId().values()) {
            if (!first) out.append(','); first = false;
            out.append("{\"windowId\":"); quote(out, relation.windowId()).append(",\"surfaceId\":"); numberOrNull(out, relation.surfaceId());
            out.append(",\"kind\":"); quote(out, relation.kind().name()).append(",\"candidateCount\":").append(relation.candidateCount()).append('}');
        }
        return out.append("]}}\n").toString();
    }

    private static StringBuilder numberOrNull(StringBuilder out, Integer value) { return value == null ? out.append("null") : out.append(value); }
    private static StringBuilder numberOrNull(StringBuilder out, Long value) { return value == null ? out.append("null") : out.append(value); }
    private static StringBuilder rect(StringBuilder out, SurfaceLayerInfo.FloatRect rect) {
        if (rect == null) return out.append("null");
        return out.append("{\"left\":").append(rect.left).append(",\"top\":").append(rect.top)
                .append(",\"right\":").append(rect.right).append(",\"bottom\":").append(rect.bottom).append('}');
    }
    private static StringBuilder buffer(StringBuilder out, SurfaceLayerInfo.ActiveBuffer buffer) {
        if (buffer == null) return out.append("null");
        return out.append("{\"width\":").append(buffer.width).append(",\"height\":").append(buffer.height)
                .append(",\"stride\":").append(buffer.stride).append(",\"format\":").append(buffer.format).append('}');
    }
    private static StringBuilder inputWindowInfo(StringBuilder out, SurfaceLayerInfo layer) {
        SurfaceLayerInfo.InputWindowInfo info = layer.inputWindowInfo();
        if (info == null) return out.append("null");
        out.append("{\"layoutParamsType\":"); numberOrNull(out, info.layoutParamsType()).append(",\"frame\":");
        if (info.frame() == null) out.append("null"); else { out.append('['); for (int i = 0; i < info.frame().size(); i++) { if (i > 0) out.append(','); out.append(info.frame().get(i)); } out.append(']'); }
        return out.append('}');
    }
    private static String bytesHex(byte[] bytes) { StringBuilder out = new StringBuilder(); for (byte b : bytes) out.append(String.format("%02x", b & 0xff)); return out.toString(); }
    private static StringBuilder quote(StringBuilder out, String value) {
        if (value == null) return out.append("null"); out.append('"');
        for (int i = 0; i < value.length(); i++) { char c = value.charAt(i); if (c == '"' || c == '\\') out.append('\\').append(c); else if (c == '\n') out.append("\\n"); else if (c == '\r') out.append("\\r"); else if (c < 0x20) out.append(String.format("\\u%04x", (int) c)); else out.append(c); }
        return out.append('"');
    }
}
