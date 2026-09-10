package com.droidscope.surface;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class SurfaceProtoParserTest {
    public static void main(String[] args) throws Exception {
        byte[] fixture = Files.readAllBytes(Path.of(
                "windows/src/test/resources/surface/oneplus9r-api34-20260910/surfaceflinger.pb"));
        SurfaceSnapshot snapshot = new SurfaceProtoParser().parse(fixture);
        assertTrue(snapshot.layers().size() > 100, "real fixture should expose all layers");

        SurfaceLayerInfo launcher = find(snapshot, 5635);
        assertEquals("com.android.launcher3/com.android.launcher3.uioverrides.QuickstepLauncher#5635",
                launcher.rawName());
        assertEquals("com.android.launcher3/com.android.launcher3.uioverrides.QuickstepLauncher",
                launcher.canonicalName());
        assertEquals(92, launcher.parentId());
        assertTrue(launcher.hasBuffer(), "launcher layer should have a buffer");
        assertEquals(10171, launcher.metadataInt32(1));
        assertEquals(1, launcher.metadataInt32(2));
        assertEquals(2795, launcher.metadataInt32(6));
        assertTrue(launcher.inputWindowInfo() != null, "launcher layer should expose input info");
        assertEquals(2, launcher.inputWindowInfo().layoutParamsType());
        assertEquals(Arrays.asList(0, 0, 1080, 2400), launcher.inputWindowInfo().frame());

        SurfaceLayerInfo container = find(snapshot, 92);
        assertEquals(91, container.parentId());
        assertEquals(Arrays.asList(5635), container.childIds());
        assertEquals(null, container.metadataInt32(1));

        SurfaceSnapshot synthetic = new SurfaceProtoParser().parse(snapshotBytes(
                layer(7, "Root#7", 0, 0, true, 42, 11, 12)));
        assertEquals(1, synthetic.layers().size());
        assertEquals(42, synthetic.layers().get(0).metadataInt32(1));

        SurfaceSnapshot empty = new SurfaceProtoParser().parse(snapshotBytes(new byte[0]));
        assertEquals(1, empty.layers().size());

        expectParseFailure(Arrays.copyOf(fixture, fixture.length - 1), "truncated proto");
        expectParseFailure(new byte[] {0x09, 'L', 'Y', 'R', 'T', 'R', 'A', 'C', 'E', 0x12, 0x7f},
                "invalid length");
    }

    private static SurfaceLayerInfo find(SurfaceSnapshot snapshot, int id) {
        return snapshot.layers().stream().filter(layer -> layer.id() == id).findFirst()
                .orElseThrow(() -> new AssertionError("missing layer " + id));
    }

    private static byte[] snapshotBytes(byte[] layer) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[] {0x09, 'L', 'Y', 'R', 'T', 'R', 'A', 'C', 'E'});
        out.write(field(2, field(3, field(1, layer))));
        return out.toByteArray();
    }

    private static byte[] layer(int id, String name, int parent, int child, boolean buffer,
                                int ownerUid, int windowType, int ownerPid) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        appendVarint(out, 1, id);
        append(out, 2, name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        appendVarint(out, 3, child);
        append(out, 5, "Layer".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        appendVarint(out, 25, parent);
        if (buffer) append(out, 27, new byte[0]);
        append(out, 42, mapEntry(1, ownerUid));
        append(out, 42, mapEntry(2, windowType));
        append(out, 42, mapEntry(6, ownerPid));
        appendVarint(out, 99, 123);
        return out.toByteArray();
    }

    private static byte[] mapEntry(int key, int value) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(varint(8));
        out.write(varint(key));
        out.write(varint(18));
        byte[] bytes = int32(value);
        out.write(varint(bytes.length));
        out.write(bytes);
        return out.toByteArray();
    }

    private static byte[] int32(int value) {
        return new byte[] {(byte) value, (byte) (value >>> 8), (byte) (value >>> 16), (byte) (value >>> 24)};
    }

    private static byte[] field(int number, byte[] value) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        append(out, number, value);
        return out.toByteArray();
    }

    private static byte[] field(int n1, byte[] v1, int n2, byte[] v2) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        append(out, n1, v1);
        append(out, n2, v2);
        return out.toByteArray();
    }

    private static void append(ByteArrayOutputStream out, int number, byte[] value) throws Exception {
        out.write(varint((number << 3) | 2));
        out.write(varint(value.length));
        out.write(value);
    }

    private static void appendVarint(ByteArrayOutputStream out, int number, long value) throws Exception {
        out.write(varint(number << 3));
        out.write(varint(value));
    }

    private static byte[] varint(long value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        do {
            int next = (int) (value & 0x7f);
            value >>>= 7;
            out.write(value == 0 ? next : next | 0x80);
        } while (value != 0);
        return out.toByteArray();
    }

    private static void expectParseFailure(byte[] bytes, String message) {
        try {
            new SurfaceProtoParser().parse(bytes);
            throw new AssertionError(message + " should fail");
        } catch (SurfaceProtoParser.ParseException expected) {
            // expected
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("expected " + expected + " but got " + actual);
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
