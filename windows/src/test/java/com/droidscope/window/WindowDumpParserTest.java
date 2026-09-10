package com.droidscope.window;

import java.nio.file.Files;
import java.nio.file.Path;

public final class WindowDumpParserTest {
    public static void main(String[] args) throws Exception {
        String sample = "mCurrentFocus=Window{abc u0 com.sprd.engineermode/com.sprd.engineermode.EngineerModeActivity}-[Surface(name=*Title#1)/@0x1]\n"
                + "  Window #0 Window{abc u0 com.sprd.engineer\n"
                + "mode/com.sprd.engineermode.EngineerModeActivity}-[Surface(name=*Title#1)/@0x1]:\n"
                + "    mDisplayId=0 rootTaskId=13 mSession=Session{abc 10796:1000}\n"
                + "    mAttrs={(0,0)(fillxfill) ty=BASE_APPLICATION}\n"
                + "    mHasSurface=true isReadyForDisplay()=true\n";
        WindowSnapshot snapshot = new WindowDumpParser().parse(sample);
        assertEquals(1, snapshot.windows().size());
        WindowInfo window = snapshot.windows().get(0);
        assertEquals("com.sprd.engineermode/com.sprd.engineermode.EngineerModeActivity", window.title());
        assertEquals("com.sprd.engineermode", window.packageName());
        assertEquals(0, window.userId());
        assertEquals("com.sprd.engineermode/com.sprd.engineermode.EngineerModeActivity", window.componentName());
        assertEquals(0, window.displayId());
        assertEquals(10796, window.pid());
        assertEquals(1000, window.uid());
        assertEquals(1, window.windowType());
        assertTrue(window.focused(), "focused window should be detected");
        assertTrue(window.hasSurface(), "surface should be detected");
        assertTrue(window.visible(), "ready window should be visible");

        String android14 = "  Window #1 Window{abc u0 com.demo/.Main}-:\n"
                + "    mDisplayId=0 mSession=Session{abc 2795:u0a10171}\n"
                + "    mOwnerUid=10171\n    mAttrs={(0,0)(fillxfill) ty=BASE_APPLICATION}\n";
        WindowInfo android14Window = new WindowDumpParser().parse(android14).windows().get(0);
        assertEquals(2795, android14Window.pid());
        assertEquals(10171, android14Window.uid());
        assertEquals(1, android14Window.windowType());

        Path fixture13 = Path.of("docs/private/fixtures/window/android-13-unisoc/dumpsys-window.txt");
        Path fixture14 = Path.of("docs/private/fixtures/window/android-14-lineageos/dumpsys-window.txt");
        if (Files.exists(fixture13) && Files.exists(fixture14)) {
            assertTrue(new WindowDumpParser().parse(Files.readString(fixture13)).windows().size() > 10,
                    "Android 13 fixture should expose detailed windows");
            assertTrue(new WindowDumpParser().parse(Files.readString(fixture14)).windows().size() > 5,
                    "Android 14 fixture should expose detailed windows");
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("expected " + expected + " but got " + actual);
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
