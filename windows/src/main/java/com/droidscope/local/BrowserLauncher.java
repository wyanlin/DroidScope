package com.droidscope.local;

import java.awt.Desktop;
import java.net.URI;
import java.util.function.Consumer;

public final class BrowserLauncher {
    private final Consumer<URI> browse;
    public BrowserLauncher() { this(uri -> {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) Desktop.getDesktop().browse(uri);
            else System.out.println(uri);
        } catch (Exception exception) { System.err.println("Cannot open browser: " + uri); }
    }); }
    BrowserLauncher(Consumer<URI> browse) { this.browse = browse; }
    public void open(String token) { browse.accept(URI.create("http://127.0.0.1:9527/ui/#token=" + token)); }
}
