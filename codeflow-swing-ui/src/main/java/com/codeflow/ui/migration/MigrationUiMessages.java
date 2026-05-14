package com.codeflow.ui.migration;

import com.codeflow.migration.MigrationPaths;

/** Migration paneli metinleri. */
public final class MigrationUiMessages {

    public static final String TITLE = "COBOL \u2192 Java migration";
    public static final String ACCURACY_EMPTY = "Is dogrulugu: \u2014";
    public static final String WORKSPACE_NONE = "Workspace secilmedi";
    public static final String OPEN_WORKSPACE = "Workspace ac...";
    public static final String RESCAN = "Yeniden tara";
    public static final String RUN_TESTS = "Testleri kos";
    public static final String OPEN_WORKSPACE_FIRST = "Once workspace acin.";
    public static final String WARNING = "Uyari";
    public static final String SCAN_ERROR = "Tarama hatasi";
    public static final String TEST_ERROR = "Test hatasi";
    public static final String PLACEHOLDER_OPEN = "Workspace acin";

    public static String goldenHelpHint() {
        return "\nGolden testler " + MigrationPaths.META_DIR + "/" + MigrationPaths.GOLDEN_DIR
                + "/ altinda.\n" + MigrationPaths.LEGACY_DIR + "/ ve " + MigrationPaths.TARGET_DIR
                + "/ klasorlerini workspace kokune koyun.\n";
    }

    private MigrationUiMessages() {
    }
}
