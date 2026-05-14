package com.codeflow.util;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.prefs.Preferences;

/**
 * Pencere boyutu / konumu, akış yakınlaştırma ve son izlenen klasör ({@link java.util.prefs.Preferences}).
 */
public final class AppPreferences {

    private static final Preferences P = Preferences.userNodeForPackage(com.codeflow.CodeFlow.class);

    private static final String K_WIN_X = "windowX";
    private static final String K_WIN_Y = "windowY";
    private static final String K_WIN_W = "windowW";
    private static final String K_WIN_H = "windowH";
    private static final String K_SPLIT_OUTER = "splitOuter";
    private static final String K_SPLIT_INNER = "splitInner";
    private static final String K_FLOW_ZOOM = "flowZoom";
    private static final String K_LAST_WATCH = "lastWatchDir";

    private AppPreferences() {
    }

    public static void applyWindowBounds(Window w) {
        int x = P.getInt(K_WIN_X, -1);
        int y = P.getInt(K_WIN_Y, -1);
        int width = P.getInt(K_WIN_W, -1);
        int height = P.getInt(K_WIN_H, -1);
        if (width > 200 && height > 200 && x >= 0 && y >= 0) {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            Rectangle union = new Rectangle();
            for (GraphicsDevice gd : ge.getScreenDevices()) {
                union = union.union(gd.getDefaultConfiguration().getBounds());
            }
            if (union.contains(x + 50, y + 50)) {
                w.setBounds(x, y, width, height);
                return;
            }
        }
    }

    public static void saveWindowBounds(Window w) {
        if (!(w instanceof Component)) return;
        Component c = (Component) w;
        Rectangle b = c.getBounds();
        P.putInt(K_WIN_X, b.x);
        P.putInt(K_WIN_Y, b.y);
        P.putInt(K_WIN_W, b.width);
        P.putInt(K_WIN_H, b.height);
    }

    public static void applySplitters(JSplitPane outer, JSplitPane inner) {
        int o = P.getInt(K_SPLIT_OUTER, -1);
        if (o > 40) outer.setDividerLocation(o);
        int i = P.getInt(K_SPLIT_INNER, -1);
        if (i > 200) inner.setDividerLocation(i);
    }

    public static void saveSplitters(JSplitPane outer, JSplitPane inner) {
        P.putInt(K_SPLIT_OUTER, outer.getDividerLocation());
        P.putInt(K_SPLIT_INNER, inner.getDividerLocation());
    }

    public static double getFlowZoom() {
        return P.getDouble(K_FLOW_ZOOM, 1.0);
    }

    public static void setFlowZoom(double zoom) {
        P.putDouble(K_FLOW_ZOOM, Math.max(0.25, Math.min(3.0, zoom)));
    }

    public static String getLastWatchDirectory() {
        return P.get(K_LAST_WATCH, "");
    }

    public static void setLastWatchDirectory(String absolutePath) {
        P.put(K_LAST_WATCH, absolutePath != null ? absolutePath : "");
    }

    /** JFileChooser için geçerli bir klasör döndürür (yoksa {@code null}). */
    public static File lastWatchDirectoryAsFile() {
        String s = getLastWatchDirectory();
        if (s.isEmpty()) return null;
        File f = new File(s);
        return f.isDirectory() ? f : null;
    }
}
