package com.codeflow.migration.workspace;

import com.codeflow.migration.MigrationCopy;
import com.codeflow.migration.MigrationPaths;
import com.codeflow.parser.SourceExtensions;
import com.codeflow.migration.flow.FlowComparisonService;
import com.codeflow.migration.golden.GoldenManifestLoader;
import com.codeflow.migration.model.*;
import com.codeflow.migration.test.MavenTestRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Workspace tarar, pipeline adimlarini ve metrikleri uretir. */
public final class MigrationWorkspaceService {

    private final GoldenManifestLoader manifestLoader = new GoldenManifestLoader();
    private final FlowComparisonService flowComparison = new FlowComparisonService();
    private final MavenTestRunner testRunner = new MavenTestRunner();

    private Path currentRoot;
    private MigrationWorkspaceState lastState = MigrationWorkspaceState.empty(Path.of("."));

    public MigrationWorkspaceState scan(Path workspaceRoot) throws IOException {
        currentRoot = workspaceRoot.normalize().toAbsolutePath();
        Path legacy = MigrationPaths.legacyDir(currentRoot);
        Path target = MigrationPaths.targetDir(currentRoot);

        FlowComparisonService.FlowComparisonResult flow =
                flowComparison.compare(legacy, target);
        GoldenManifest manifest = manifestLoader.load(currentRoot);

        int goldenTotal = manifest.cases().size();
        int goldenPass = (int) manifest.cases().stream()
                .filter(c -> c.status() == GoldenCaseStatus.PASS).count();

        double accuracy = goldenTotal > 0 ? 100.0 * goldenPass / goldenTotal : 0;

        List<MigrationStepView> steps = buildSteps(legacy, target, flow, manifest, goldenPass, goldenTotal, accuracy, "");

        lastState = new MigrationWorkspaceState(
                currentRoot,
                Files.isDirectory(legacy) ? legacy : null,
                Files.isDirectory(target) ? target : null,
                flow.cobolSourceFiles(),
                countJavaFiles(target),
                flow.javaTypeCount(),
                flow.warningCount(),
                flow.warnings(),
                flow.javaTypes(),
                manifest,
                accuracy,
                goldenPass,
                goldenTotal,
                steps,
                ""
        );
        return lastState;
    }

    public MigrationWorkspaceState runTests() throws IOException, InterruptedException {
        if (currentRoot == null) {
            throw new IllegalStateException(MigrationCopy.WORKSPACE_NOT_OPEN);
        }
        Path target = MigrationPaths.targetDir(currentRoot);
        MavenTestRunner.MavenTestResult result = testRunner.runTests(target, 15);

        MigrationWorkspaceState base = lastState;
        FlowComparisonService.FlowComparisonResult flow =
                flowComparison.compare(
                        base.legacyPath() != null ? base.legacyPath() : MigrationPaths.legacyDir(currentRoot),
                        target);

        int total = result.total() > 0 ? result.total() : base.goldenManifest().cases().size();
        int passed = result.total() > 0 ? result.passed() : base.testsPassed();
        double accuracy = total > 0 ? 100.0 * passed / total : result.accuracyPercent();

        GoldenManifest manifest = base.goldenManifest();
        List<MigrationStepView> steps = buildSteps(
                base.legacyPath(),
                Files.isDirectory(target) ? target : null,
                flow,
                manifest,
                passed,
                total,
                accuracy,
                result.log());

        lastState = new MigrationWorkspaceState(
                currentRoot,
                base.legacyPath(),
                Files.isDirectory(target) ? target : null,
                flow.cobolSourceFiles(),
                countJavaFiles(target),
                flow.javaTypeCount(),
                flow.warningCount(),
                flow.warnings(),
                flow.javaTypes(),
                manifest,
                accuracy,
                passed,
                total,
                steps,
                result.log()
        );
        return lastState;
    }

    public MigrationWorkspaceState getLastState() {
        return lastState;
    }

    public Path getCurrentRoot() {
        return currentRoot;
    }

    private static int countJavaFiles(Path target) throws IOException {
        if (!Files.isDirectory(target)) return 0;
        int n = 0;
        try (var walk = Files.walk(target)) {
            for (Path p : walk.toList()) {
                if (Files.isRegularFile(p) && p.toString().endsWith(SourceExtensions.JAVA)) n++;
            }
        }
        return n;
    }

    private static List<MigrationStepView> buildSteps(
            Path legacy,
            Path target,
            FlowComparisonService.FlowComparisonResult flow,
            GoldenManifest manifest,
            int passed,
            int total,
            double accuracy,
            String testLog) {

        List<MigrationStepView> steps = new ArrayList<>();
        steps.add(step(MigrationStepKind.LEGACY_LINKED,
                legacy != null && Files.isDirectory(legacy) ? StepStatus.OK : StepStatus.ERROR,
                legacy != null ? legacy.getFileName().toString() : MigrationCopy.missingDir(MigrationPaths.LEGACY_DIR)));

        steps.add(step(MigrationStepKind.TARGET_LINKED,
                target != null && Files.isDirectory(target) ? StepStatus.OK : StepStatus.ERROR,
                target != null ? target.getFileName().toString() : MigrationCopy.missingDir(MigrationPaths.TARGET_DIR)));

        StepStatus analyze = (flow.cobolSourceFiles() > 0 || flow.javaTypeCount() > 0)
                ? StepStatus.OK : StepStatus.WARNING;
        steps.add(step(MigrationStepKind.ANALYZED, analyze,
                MigrationCopy.analyzeSummary(flow.cobolSourceFiles(), flow.javaTypeCount())));

        StepStatus flowSt = flow.warnings().isEmpty() ? StepStatus.OK
                : (flow.javaTypeCount() > 0 ? StepStatus.WARNING : StepStatus.ERROR);
        steps.add(step(MigrationStepKind.FLOW_COMPARED, flowSt,
                flow.warnings().isEmpty() ? MigrationCopy.NO_FLOW_WARNINGS : flow.warnings().get(0)));

        StepStatus goldenSt = manifest.cases().isEmpty() ? StepStatus.WARNING : StepStatus.OK;
        steps.add(step(MigrationStepKind.GOLDEN_LOADED, goldenSt,
                MigrationCopy.caseCount(manifest.cases().size())));

        StepStatus testSt = testLog.isEmpty() ? StepStatus.PENDING
                : (passed == total && total > 0 ? StepStatus.OK : StepStatus.WARNING);
        steps.add(step(MigrationStepKind.TESTS_RUN, testSt,
                total > 0 ? MigrationCopy.testRatio(passed, total) : MigrationCopy.TESTS_NOT_RUN));

        StepStatus sumSt = total > 0 ? (accuracy >= 99 ? StepStatus.OK : StepStatus.WARNING) : StepStatus.PENDING;
        steps.add(step(MigrationStepKind.SUMMARY, sumSt,
                total > 0 ? MigrationCopy.accuracySummary(accuracy) : MigrationCopy.SUMMARY_PENDING));

        return steps;
    }

    private static MigrationStepView step(MigrationStepKind kind, StepStatus status, String detail) {
        return new MigrationStepView(kind, status, detail);
    }
}
