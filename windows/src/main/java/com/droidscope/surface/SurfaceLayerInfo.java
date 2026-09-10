package com.droidscope.surface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SurfaceLayerInfo {
    private final int id;
    private final String rawName;
    private final String canonicalName;
    private final String type;
    private final Integer parentId;
    private final List<Integer> childIds;
    private final Integer layerStack;
    private final Integer z;
    private final FloatRect bounds;
    private final FloatRect screenBounds;
    private final boolean hasBuffer;
    private final ActiveBuffer activeBuffer;
    private final Long currentFrame;
    private final Map<Integer, byte[]> metadata;
    private final InputWindowInfo inputWindowInfo;

    public SurfaceLayerInfo(int id, String rawName, String type, Integer parentId, List<Integer> childIds,
                            Integer layerStack, Integer z, FloatRect bounds, FloatRect screenBounds,
                            ActiveBuffer activeBuffer, Long currentFrame, Map<Integer, byte[]> metadata,
                            InputWindowInfo inputWindowInfo) {
        this.id = id;
        this.rawName = rawName;
        this.canonicalName = canonicalName(id, rawName);
        this.type = type;
        this.parentId = parentId;
        this.childIds = Collections.unmodifiableList(new ArrayList<>(childIds));
        this.layerStack = layerStack;
        this.z = z;
        this.bounds = bounds;
        this.screenBounds = screenBounds;
        this.hasBuffer = activeBuffer != null;
        this.activeBuffer = activeBuffer;
        this.currentFrame = currentFrame;
        Map<Integer, byte[]> copied = new LinkedHashMap<>();
        metadata.forEach((key, value) -> copied.put(key, value.clone()));
        this.metadata = Collections.unmodifiableMap(copied);
        this.inputWindowInfo = inputWindowInfo;
    }

    public int id() { return id; }
    public String rawName() { return rawName; }
    public String canonicalName() { return canonicalName; }
    public String type() { return type; }
    public Integer parentId() { return parentId; }
    public List<Integer> childIds() { return childIds; }
    public Integer layerStack() { return layerStack; }
    public Integer z() { return z; }
    public FloatRect bounds() { return bounds; }
    public FloatRect screenBounds() { return screenBounds; }
    public boolean hasBuffer() { return activeBuffer != null; }
    public ActiveBuffer activeBuffer() { return activeBuffer; }
    public Long currentFrame() { return currentFrame; }
    public Map<Integer, byte[]> metadata() { return metadata; }
    public InputWindowInfo inputWindowInfo() { return inputWindowInfo; }

    public Integer metadataInt32(int key) {
        byte[] value = metadata.get(key);
        if (value == null || value.length != 4) return null;
        return (value[0] & 0xff) | ((value[1] & 0xff) << 8)
                | ((value[2] & 0xff) << 16) | (value[3] << 24);
    }

    private static String canonicalName(int id, String rawName) {
        if (rawName == null) return null;
        int marker = rawName.lastIndexOf('#');
        if (marker < 0 || marker == rawName.length() - 1) return null;
        String suffix = rawName.substring(marker + 1);
        if (!suffix.matches("[0-9]+")) return null;
        try {
            return Long.parseLong(suffix) == id ? rawName.substring(0, marker) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static final class FloatRect {
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;
        public FloatRect(float left, float top, float right, float bottom) {
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        }
    }

    public static final class ActiveBuffer {
        public final int width;
        public final int height;
        public final int stride;
        public final int format;
        public ActiveBuffer(int width, int height, int stride, int format) {
            this.width = width; this.height = height; this.stride = stride; this.format = format;
        }
    }

    public static final class InputWindowInfo {
        private final Integer layoutParamsType;
        private final List<Integer> frame;
        public InputWindowInfo(Integer layoutParamsType, List<Integer> frame) {
            this.layoutParamsType = layoutParamsType;
            this.frame = frame == null ? null : Collections.unmodifiableList(new ArrayList<>(frame));
        }
        public Integer layoutParamsType() { return layoutParamsType; }
        public List<Integer> frame() { return frame; }
    }
}
