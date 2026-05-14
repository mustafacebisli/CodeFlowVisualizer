package com.codeflow.migration.model;

import com.codeflow.model.CodeClass;

import java.nio.file.Path;
import java.util.List;

/** Workspace tarama sonucu — UI'ya sunulur. */
public record MigrationWorkspaceState(
        Path workspaceRoot,
        List<String> flowWarnings,
        List<CodeClass> javaTypes,
        GoldenManifest goldenManifest,
        double businessAccuracyPercent,
        int testsPassed,
        int testsTotal,
        List<MigrationStepView> steps,
        String lastTestLog
) {
    public static MigrationWorkspaceState empty(Path root) {
        return new MigrationWorkspaceState(
                root, List.of(), List.of(),
                new GoldenManifest(1, List.of()),
                0, 0, 0,
                List.of(),
                ""
        );
    }
}
