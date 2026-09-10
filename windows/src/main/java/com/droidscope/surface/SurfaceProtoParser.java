package com.droidscope.surface;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SurfaceProtoParser {
    private static final byte[] MAGIC = "LYRTRACE".getBytes(StandardCharsets.US_ASCII);

    public SurfaceSnapshot parse(byte[] payload) throws ParseException {
        if (payload == null || payload.length < 9 || payload[0] != 0x09) {
            throw new ParseException("missing LYRTRACE envelope");
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (payload[i + 1] != MAGIC[i]) throw new ParseException("invalid LYRTRACE envelope");
        }
        List<SurfaceLayerInfo> layers = new ArrayList<>();
        for (Field root : fields(payload, 9, payload.length)) {
            if (root.number != 2 || root.wireType != 2) continue;
            for (Field entry : fields(root.bytes, 0, root.bytes.length)) {
                if (entry.number != 3 || entry.wireType != 2) continue;
                for (Field layer : fields(entry.bytes, 0, entry.bytes.length)) {
                    if (layer.number == 1 && layer.wireType == 2) layers.add(parseLayer(layer.bytes));
                }
            }
        }
        return new SurfaceSnapshot(layers);
    }

    private SurfaceLayerInfo parseLayer(byte[] bytes) throws ParseException {
        int id = 0;
        String name = null;
        String type = null;
        Integer parent = null;
        Integer layerStack = null;
        Integer z = null;
        List<Integer> children = new ArrayList<>();
        SurfaceLayerInfo.FloatRect bounds = null;
        SurfaceLayerInfo.FloatRect screenBounds = null;
        SurfaceLayerInfo.ActiveBuffer activeBuffer = null;
        Long currentFrame = null;
        Map<Integer, byte[]> metadata = new LinkedHashMap<>();
        SurfaceLayerInfo.InputWindowInfo inputWindowInfo = null;
        for (Field field : fields(bytes, 0, bytes.length)) {
            switch (field.number) {
                case 1: id = int32(field); break;
                case 2: name = text(field); break;
                case 3: addRepeatedInt32(children, field); break;
                case 5: type = text(field); break;
                case 9: layerStack = int32(field); break;
                case 10: z = int32(field); break;
                case 25: parent = int32(field); break;
                case 27: activeBuffer = parseBuffer(field.bytes); break;
                case 37: currentFrame = unsignedLong(field); break;
                case 42: parseMetadata(metadata, field.bytes); break;
                case 45: bounds = parseFloatRect(field.bytes); break;
                case 46: screenBounds = parseFloatRect(field.bytes); break;
                case 47: inputWindowInfo = parseInputWindowInfo(field.bytes); break;
                default: break;
            }
        }
        return new SurfaceLayerInfo(id, name, type, parent, children, layerStack, z, bounds, screenBounds,
                activeBuffer, currentFrame, metadata, inputWindowInfo);
    }

    private static SurfaceLayerInfo.ActiveBuffer parseBuffer(byte[] bytes) throws ParseException {
        int width = 0, height = 0, stride = 0, format = 0;
        for (Field field : fields(bytes, 0, bytes.length)) {
            switch (field.number) {
                case 1: width = int32(field); break;
                case 2: height = int32(field); break;
                case 3: stride = int32(field); break;
                case 4: format = int32(field); break;
                default: break;
            }
        }
        return new SurfaceLayerInfo.ActiveBuffer(width, height, stride, format);
    }

    private static SurfaceLayerInfo.FloatRect parseFloatRect(byte[] bytes) throws ParseException {
        float[] values = new float[4];
        for (Field field : fields(bytes, 0, bytes.length)) {
            if (field.number >= 1 && field.number <= 4 && field.wireType == 5) {
                values[field.number - 1] = Float.intBitsToFloat(int32Fixed(field.bytes));
            }
        }
        return new SurfaceLayerInfo.FloatRect(values[0], values[1], values[2], values[3]);
    }

    private static SurfaceLayerInfo.InputWindowInfo parseInputWindowInfo(byte[] bytes) throws ParseException {
        Integer layoutType = null;
        List<Integer> frame = null;
        for (Field field : fields(bytes, 0, bytes.length)) {
            if (field.number == 2 && field.wireType == 0) layoutType = int32(field);
            if (field.number == 3 && field.wireType == 2) {
                List<Integer> values = new ArrayList<>();
                for (Field frameField : fields(field.bytes, 0, field.bytes.length)) {
                    if (frameField.wireType == 0 && frameField.number >= 1 && frameField.number <= 4) {
                        while (values.size() < frameField.number - 1) values.add(0);
                        if (values.size() == frameField.number - 1) values.add(int32(frameField));
                    }
                }
                while (values.size() < 4) values.add(0);
                frame = values;
            }
        }
        return new SurfaceLayerInfo.InputWindowInfo(layoutType, frame);
    }

    private static void parseMetadata(Map<Integer, byte[]> metadata, byte[] bytes) throws ParseException {
        Integer key = null;
        byte[] value = null;
        for (Field field : fields(bytes, 0, bytes.length)) {
            if (field.number == 1 && field.wireType == 0) key = int32(field);
            if (field.number == 2 && field.wireType == 2) value = field.bytes.clone();
        }
        if (key != null && value != null) metadata.put(key, value);
    }

    private static void addRepeatedInt32(List<Integer> values, Field field) throws ParseException {
        if (field.wireType == 0) values.add(int32(field));
        else if (field.wireType == 2) {
            int offset = 0;
            while (offset < field.bytes.length) {
                Varint packed = readVarint(field.bytes, offset, field.bytes.length);
                offset = packed.nextOffset;
                values.add((int) packed.value);
            }
        }
    }

    private static String text(Field field) throws ParseException {
        if (field.wireType != 2) throw new ParseException("expected text field");
        return new String(field.bytes, StandardCharsets.UTF_8);
    }

    private static int int32(Field field) throws ParseException {
        if (field.wireType != 0) throw new ParseException("expected varint");
        return (int) field.varint;
    }

    private static long unsignedLong(Field field) throws ParseException {
        if (field.wireType != 0) throw new ParseException("expected varint");
        return field.varint;
    }

    private static int int32Fixed(byte[] bytes) throws ParseException {
        if (bytes.length != 4) throw new ParseException("invalid fixed32");
        return (bytes[0] & 0xff) | ((bytes[1] & 0xff) << 8) | ((bytes[2] & 0xff) << 16) | (bytes[3] << 24);
    }

    private static List<Field> fields(byte[] bytes, int start, int end) throws ParseException {
        List<Field> result = new ArrayList<>();
        int offset = start;
        while (offset < end) {
            Varint key = readVarint(bytes, offset, end);
            offset = key.nextOffset;
            int number = (int) (key.value >>> 3);
            int wireType = (int) (key.value & 7);
            if (number <= 0 || wireType == 4 || wireType == 3) throw new ParseException("invalid wire type");
            if (wireType == 0) {
                Varint value = readVarint(bytes, offset, end);
                offset = value.nextOffset;
                result.add(new Field(number, wireType, value.value, null));
            } else if (wireType == 1) {
                if (offset + 8 > end) throw new ParseException("truncated fixed64");
                result.add(new Field(number, wireType, 0, copy(bytes, offset, offset + 8)));
                offset += 8;
            } else if (wireType == 2) {
                Varint length = readVarint(bytes, offset, end);
                offset = length.nextOffset;
                if (length.value > Integer.MAX_VALUE || length.value < 0 || offset + (int) length.value > end) {
                    throw new ParseException("invalid length");
                }
                result.add(new Field(number, wireType, 0, copy(bytes, offset, offset + (int) length.value)));
                offset += (int) length.value;
            } else if (wireType == 5) {
                if (offset + 4 > end) throw new ParseException("truncated fixed32");
                result.add(new Field(number, wireType, 0, copy(bytes, offset, offset + 4)));
                offset += 4;
            } else {
                throw new ParseException("unsupported wire type");
            }
        }
        return result;
    }

    private static byte[] copy(byte[] bytes, int start, int end) {
        byte[] copy = new byte[end - start];
        System.arraycopy(bytes, start, copy, 0, copy.length);
        return copy;
    }

    private static Varint readVarint(byte[] bytes, int offset, int end) throws ParseException {
        long value = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            if (offset >= end) throw new ParseException("truncated varint");
            int current = bytes[offset++] & 0xff;
            value |= (long) (current & 0x7f) << shift;
            if ((current & 0x80) == 0) return new Varint(value, offset);
        }
        throw new ParseException("varint overflow");
    }

    private static final class Varint {
        final long value;
        final int nextOffset;
        Varint(long value, int nextOffset) { this.value = value; this.nextOffset = nextOffset; }
    }

    private static final class Field {
        final int number;
        final int wireType;
        final long varint;
        final byte[] bytes;
        Field(int number, int wireType, long varint, byte[] bytes) {
            this.number = number; this.wireType = wireType; this.varint = varint; this.bytes = bytes;
        }
    }

    public static final class ParseException extends Exception {
        public ParseException(String message) { super(message); }
    }
}
