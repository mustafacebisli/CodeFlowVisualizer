package com.codeflow.migration.golden;

import com.codeflow.migration.model.GoldenCaseStatus;
import com.codeflow.migration.model.GoldenManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GoldenManifestLoaderTest {

    private final GoldenManifestLoader loader = new GoldenManifestLoader();

    @Test
    void load_readsManifestCases(@TempDir Path workspace) throws Exception {
        GoldenTestFixtures.installManifestV2(workspace);

        GoldenManifest manifest = loader.load(workspace);

        assertEquals(2, manifest.version());
        assertEquals(1, manifest.cases().size());
        assertEquals("TC001", manifest.cases().get(0).id());
        assertEquals("DEMO01", manifest.cases().get(0).program());
        assertEquals(GoldenCaseStatus.PASS, manifest.cases().get(0).status());
    }

    @Test
    void load_scansCasesDirectoryWhenManifestMissing(@TempDir Path workspace) throws Exception {
        GoldenTestFixtures.installScannedCaseOnly(workspace, "TC002-demo");

        GoldenManifest manifest = loader.load(workspace);

        assertEquals(1, manifest.cases().size());
        assertEquals("TC002-demo", manifest.cases().get(0).id());
        assertEquals("PAYROLL", manifest.cases().get(0).program());
    }
}
