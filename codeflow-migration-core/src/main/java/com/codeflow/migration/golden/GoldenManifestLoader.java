package com.codeflow.migration.golden;

import com.codeflow.migration.MigrationPaths;
import com.codeflow.migration.model.GoldenCase;
import com.codeflow.migration.model.GoldenCaseStatus;
import com.codeflow.migration.model.GoldenManifest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * {@code manifest.json} ve {@code cases/} klasörünü okur (harici JSON kütüphanesi yok).
 */
public final class GoldenManifestLoader {

    private static final Pattern CASE_BLOCK = Pattern.compile(
            "\\{\\s*\"id\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"program\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"status\"\\s*:\\s*\"([^\"]*)\"",
            Pattern.DOTALL);

    public GoldenManifest load(Path workspaceRoot) throws IOException {
        Path manifest = MigrationPaths.manifestJson(workspaceRoot);
        if (!Files.isRegularFile(manifest)) {
            return scanCasesDirectory(workspaceRoot);
        }
        String json = Files.readString(manifest);
        int version = 1;
        Matcher vm = Pattern.compile("\"version\"\\s*:\\s*(\\d+)").matcher(json);
        if (vm.find()) {
            version = Integer.parseInt(vm.group(1));
        }
        List<GoldenCase> cases = new ArrayList<>();
        Matcher m = CASE_BLOCK.matcher(json);
        while (m.find()) {
            String id = m.group(1);
            String program = m.group(2);
            GoldenCaseStatus status = parseStatus(m.group(3));
            cases.add(enrichFromCaseDir(workspaceRoot, new GoldenCase(id, program, status, "")));
        }
        if (cases.isEmpty()) {
            return new GoldenManifest(version, scanCasesDirectory(workspaceRoot).cases());
        }
        return new GoldenManifest(version, cases);
    }

    private GoldenManifest scanCasesDirectory(Path workspaceRoot) throws IOException {
        Path casesDir = MigrationPaths.casesDir(workspaceRoot);
        if (!Files.isDirectory(casesDir)) {
            return new GoldenManifest(1, List.of());
        }
        List<GoldenCase> cases = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(casesDir)) {
            dirs.filter(Files::isDirectory).sorted().forEach(dir -> {
                String id = dir.getFileName().toString();
                String program = "";
                Path caseJson = dir.resolve(MigrationPaths.CASE_JSON);
                if (Files.isRegularFile(caseJson)) {
                    try {
                        String cj = Files.readString(caseJson);
                        Matcher pm = Pattern.compile("\"program\"\\s*:\\s*\"([^\"]+)\"").matcher(cj);
                        if (pm.find()) program = pm.group(1);
                    } catch (IOException ex) {
                        program = "";
                    }
                }
                boolean hasExpected = Files.isRegularFile(dir.resolve(MigrationPaths.EXPECTED_JSON));
                cases.add(new GoldenCase(id, program, GoldenCaseStatus.UNKNOWN,
                        hasExpected ? "" : MigrationPaths.EXPECTED_JSON + " yok"));
            });
        }
        return new GoldenManifest(1, cases);
    }

    private static GoldenCase enrichFromCaseDir(Path workspaceRoot, GoldenCase base) {
        Path dir = MigrationPaths.casesDir(workspaceRoot).resolve(base.id());
        if (!Files.isDirectory(dir)) {
            return new GoldenCase(base.id(), base.program(), base.status(), "case klasoru yok");
        }
        if (!Files.isRegularFile(dir.resolve(MigrationPaths.EXPECTED_JSON))) {
            return new GoldenCase(base.id(), base.program(), GoldenCaseStatus.UNKNOWN, MigrationPaths.EXPECTED_JSON + " yok");
        }
        return base;
    }

    private static GoldenCaseStatus parseStatus(String raw) {
        if (raw == null) return GoldenCaseStatus.UNKNOWN;
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "pass" -> GoldenCaseStatus.PASS;
            case "fail" -> GoldenCaseStatus.FAIL;
            default -> GoldenCaseStatus.UNKNOWN;
        };
    }
}
