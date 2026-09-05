package com.droidscope.local;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

public final class BrowserLauncherTest {
    public static void main(String[] args) {
        AtomicReference<URI> opened = new AtomicReference<>();
        new BrowserLauncher(opened::set).open("url_safe-token");
        URI expected = URI.create("http://127.0.0.1:9527/ui/#token=url_safe-token");
        if (!expected.equals(opened.get())) throw new AssertionError("unexpected browser URI " + opened.get());
    }
}
