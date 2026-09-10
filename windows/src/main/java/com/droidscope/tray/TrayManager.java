package com.droidscope.tray;

import com.droidscope.adb.AdbManager;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.AWTException;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.io.IOException;
import java.nio.file.Path;

public final class TrayManager implements AutoCloseable {
    private final SystemTray tray;
    private final TrayIcon icon;

    private TrayManager(SystemTray tray, TrayIcon icon) {
        this.tray = tray;
        this.icon = icon;
    }

    public static TrayManager start(Path directory, AdbManager adbManager, Runnable onExit) {
        return start(directory, adbManager, onExit, () -> {});
    }

    public static TrayManager start(Path directory, AdbManager adbManager, Runnable onExit, Runnable onOpenDroidScope) {
        if (!SystemTray.isSupported()) return null;
        try {
            PopupMenu menu = new PopupMenu();
            TrayIcon icon = new TrayIcon(createIcon(), "DroidScope", menu);
            icon.setImageAutoSize(true);
            MenuItem open = new MenuItem("Open Receive Folder");
            open.addActionListener(event -> openDirectory(directory));
            menu.add(open);
            MenuItem openDroidScope = new MenuItem("Open DroidScope");
            openDroidScope.addActionListener(event -> onOpenDroidScope.run());
            menu.add(openDroidScope);
            MenuItem reconnect = new MenuItem("Reconnect");
            reconnect.addActionListener(event -> reconnect(adbManager, icon));
            menu.add(reconnect);
            menu.addSeparator();
            MenuItem exit = new MenuItem("Exit");
            exit.addActionListener(event -> onExit.run());
            menu.add(exit);
            SystemTray tray = SystemTray.getSystemTray();
            tray.add(icon);
            return new TrayManager(tray, icon);
        } catch (AWTException e) {
            System.err.println("System tray unavailable: " + e.getMessage());
            return null;
        }
    }

    private static void openDirectory(Path directory) {
        try {
            Desktop.getDesktop().open(directory.toFile());
        } catch (IOException | UnsupportedOperationException e) {
            System.err.println("Cannot open receive directory: " + e.getMessage());
        }
    }

    private static void reconnect(AdbManager adbManager, TrayIcon icon) {
        try {
            int count = adbManager.establishReverse().size();
            icon.displayMessage("DroidScope", "Reconnected " + count + " device(s)", TrayIcon.MessageType.INFO);
        } catch (IOException | InterruptedException e) {
            icon.displayMessage("DroidScope", "Reconnect failed: " + e.getMessage(), TrayIcon.MessageType.ERROR);
        }
    }

    private static Image createIcon() {
        Image image = new java.awt.image.BufferedImage(32, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = ((java.awt.image.BufferedImage) image).createGraphics();
        graphics.setColor(new Color(35, 125, 220));
        graphics.fillRoundRect(2, 2, 28, 28, 8, 8);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(14, 7, 4, 18);
        graphics.fillRect(9, 12, 14, 4);
        graphics.fillRect(9, 20, 14, 4);
        graphics.dispose();
        return image;
    }

    @Override
    public void close() {
        tray.remove(icon);
    }
}
