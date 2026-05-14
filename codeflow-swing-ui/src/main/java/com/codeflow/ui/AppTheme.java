package com.codeflow.ui;

import java.awt.Color;
import java.awt.Font;

/** Koyu tema renkleri ve font fabrikası — panel/renderer tekrarını önler. */
public final class AppTheme {

    // --- Yüzeyler ---
    public static final Color PANEL_BG = new Color(30, 30, 33);
    public static final Color HEADER_BG = new Color(45, 45, 48);
    public static final Color TOOLBAR_BG = new Color(37, 37, 40);
    public static final Color TOOLBAR_BG_ALT = new Color(40, 40, 43);
    public static final Color BOTTOM_BAR_BG = new Color(35, 35, 38);
    public static final Color CANVAS_BG = new Color(25, 25, 28);
    public static final Color EDITOR_BG = new Color(30, 30, 30);
    public static final Color CHIP_BG = new Color(40, 40, 45);
    public static final Color MIGRATION_LIST_BG = new Color(28, 28, 32);
    public static final Color LINE_NUMBER_BG = new Color(37, 37, 38);
    public static final Color OVERVIEW_CARD_BG = new Color(38, 38, 42);

    // --- Vurgu / durum ---
    public static final Color ACCENT_BLUE = new Color(66, 133, 244);
    public static final Color ACCENT_BLUE_LIGHT = new Color(100, 181, 246);
    public static final Color ACCENT_GREEN = new Color(80, 200, 120);
    public static final Color ACCENT_GREEN_BRIGHT = new Color(129, 199, 132);
    public static final Color SUCCESS_GREEN = new Color(46, 160, 67);
    public static final Color WATCH_ACTIVE = new Color(46, 160, 67);
    public static final Color ERROR_RED = new Color(220, 38, 38);
    public static final Color FAIL_RED = new Color(255, 120, 120);
    public static final Color WARNING_AMBER = new Color(234, 160, 0);
    public static final Color WARNING_CHIP = new Color(255, 193, 7);
    public static final Color ERROR_CHIP = new Color(244, 67, 54);
    public static final Color OK_CHIP = new Color(76, 175, 80);
    public static final Color PENDING_CHIP = new Color(90, 90, 95);
    public static final Color HINT_AMBER = new Color(220, 170, 90);

    // --- Metin ---
    public static final Color TEXT_PRIMARY = new Color(212, 212, 212);
    public static final Color TEXT_SECONDARY = new Color(200, 200, 200);
    public static final Color TEXT_MUTED = new Color(150, 150, 150);
    public static final Color TEXT_DIM = new Color(140, 140, 140);
    public static final Color TEXT_SUBTLE = new Color(180, 180, 180);
    public static final Color TEXT_FAINT = new Color(160, 160, 160);
    public static final Color TEXT_CHIP = new Color(170, 170, 170);
    public static final Color TEXT_EXPLORER_TITLE = new Color(200, 200, 200);

    // --- Seçim / kenarlık ---
    public static final Color SELECTION_BG = new Color(38, 79, 120);
    public static final Color MIGRATION_SELECTION_BG = new Color(55, 71, 95);
    public static final Color BORDER_SUBTLE = new Color(55, 55, 60);
    public static final Color BORDER_OVERVIEW = new Color(70, 70, 78);

    // --- Diyagram ortak ---
    public static final Color ARROW = new Color(100, 100, 110);
    public static final Color ARROW_MUTED = new Color(140, 140, 150);
    public static final Color PROCESS_NODE = new Color(71, 85, 105);
    public static final Color NODE_PRIMARY = new Color(37, 99, 235);
    public static final Color NODE_TEAL = new Color(20, 184, 166);
    public static final Color INJECT_CYAN = new Color(56, 189, 248);
    public static final Color LABEL_MUTED = new Color(180, 180, 190);
    public static final Color SECTION_BAR = new Color(48, 54, 70);
    public static final Color SECTION_BAR_EDGE = new Color(90, 108, 140);
    public static final Color SECTION_TITLE = new Color(200, 210, 230);
    public static final Color TOOLTIP_BG = new Color(45, 45, 48, 230);
    public static final Color TOOLTIP_BORDER = new Color(80, 80, 85);
    public static final Color LINE_NUMBER = new Color(100, 100, 100);
    public static final Color RECORD_PURPLE = new Color(147, 51, 234);
    public static final Color GRAY_MID = new Color(140, 140, 140);
    public static final Color NODE_LABEL_LIGHT = new Color(230, 230, 235);
    public static final Color INTRA_LABEL = new Color(160, 170, 185);

    public static final String CODE_FONT_FAMILY = "JetBrains Mono";

    private AppTheme() {
    }

    public static Font sans(int style, int size) {
        return new Font("SansSerif", style, size);
    }

    public static Font mono(int style, int size) {
        return new Font(Font.MONOSPACED, style, size);
    }

    public static Font codeEditor() {
        for (String f : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
            if (CODE_FONT_FAMILY.equals(f)) {
                return new Font(CODE_FONT_FAMILY, Font.PLAIN, 13);
            }
        }
        return mono(Font.PLAIN, 13);
    }

    public static Color withAlpha(Color base, int alpha) {
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
    }
}
