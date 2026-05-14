package com.codeflow.migration.golden;

import com.codeflow.migration.MigrationPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Classpath'teki golden fixture dosyalarini gecici workspace'e kopyalar. */
final class GoldenTestFixtures {

    private static final String ROOT = "golden-fixtures/";

    private GoldenTestFixtures() {
    }

    static void installManifestV2(Path workspace) throws IOException {
        copyResource(ROOT + "manifest-v2.json", MigrationPaths.manifestJson(workspace));
        copyResource(ROOT + MigrationPaths.CASES_DIR + "/TC001/" + MigrationPaths.EXPECTED_JSON,
                MigrationPaths.casesDir(workspace).resolve("TC001").resolve(MigrationPaths.EXPECTED_JSON));
    }

    static void installScannedCaseOnly(Path workspace, String caseId) throws IOException {
        Path caseDir = MigrationPaths.casesDir(workspace).resolve(caseId);
        copyResource(ROOT + MigrationPaths.CASES_DIR + "/" + caseId + "/" + MigrationPaths.CASE_JSON,
                caseDir.resolve(MigrationPaths.CASE_JSON));
        copyResource(ROOT + MigrationPaths.CASES_DIR + "/" + caseId + "/" + MigrationPaths.EXPECTED_JSON,
                caseDir.resolve(MigrationPaths.EXPECTED_JSON));
    }

    private static void copyResource(String classpathRelative, Path target) throws IOException {
        String cp = "/" + classpathRelative;
        try (InputStream in = GoldenTestFixtures.class.getResourceAsStream(cp)) {
            if (in == null) {
                throw new IOException("Test fixture bulunamadi: " + cp);
            }
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.copy(in, target);
        }
    }
}
