package com.codeflow.migration;

import java.nio.file.Path;

/** `.codeflow/` altındaki sabit yollar. */
public final class MigrationPaths {

    public static final String META_DIR = ".codeflow";
    public static final String MIGRATION_JSON = "migration.json";
    public static final String GOLDEN_DIR = "golden";
    public static final String MANIFEST_JSON = "manifest.json";
    public static final String CASES_DIR = "cases";

    public static final String LEGACY_DIR = "legacy";
    public static final String TARGET_DIR = "target";

    public static final String CASE_JSON = "case.json";
    public static final String EXPECTED_JSON = "expected.json";

    private MigrationPaths() {
    }

    public static Path metaDir(Path workspaceRoot) {
        return workspaceRoot.resolve(META_DIR);
    }

    public static Path migrationJson(Path workspaceRoot) {
        return metaDir(workspaceRoot).resolve(MIGRATION_JSON);
    }

    public static Path goldenDir(Path workspaceRoot) {
        return metaDir(workspaceRoot).resolve(GOLDEN_DIR);
    }

    public static Path manifestJson(Path workspaceRoot) {
        return goldenDir(workspaceRoot).resolve(MANIFEST_JSON);
    }

    public static Path casesDir(Path workspaceRoot) {
        return goldenDir(workspaceRoot).resolve(CASES_DIR);
    }

    public static Path legacyDir(Path workspaceRoot) {
        return workspaceRoot.resolve(LEGACY_DIR);
    }

    public static Path targetDir(Path workspaceRoot) {
        return workspaceRoot.resolve(TARGET_DIR);
    }
}
