package com.codeflow.migration.flow;

import com.codeflow.migration.MigrationCopy;
import com.codeflow.migration.MigrationPaths;
import com.codeflow.model.CodeClass;
import com.codeflow.parser.JavaProjectSources;
import com.codeflow.parser.JavaSourceParser;
import com.codeflow.parser.SourceExtensions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * İlk sürüm: COBOL dosya sayısı ile Java tip sayısını kıyaslar; hedef Java akışı parse edilir.
 */
public final class FlowComparisonService {

    private static final String EXT_CBL = SourceExtensions.CBL;
    private static final String EXT_COB = SourceExtensions.COB;
    private static final String EXT_CPY = SourceExtensions.CPY;

    private final JavaProjectSources projectSources = new JavaProjectSources(new JavaSourceParser());

    public FlowComparisonResult compare(Path legacyDir, Path targetDir) throws IOException {
        int cobolFiles = countCobolFiles(legacyDir);
        List<CodeClass> javaTypes = loadJavaTypes(targetDir);
        List<String> warnings = new ArrayList<>();
        if (cobolFiles > 0 && javaTypes.isEmpty()) {
            warnings.add(MigrationCopy.cobolWithoutJava(cobolFiles));
        } else if (cobolFiles > 0 && javaTypes.size() < cobolFiles / 2) {
            warnings.add(MigrationCopy.javaCountLow(javaTypes.size(), cobolFiles));
        }
        if (!Files.isDirectory(legacyDir)) {
            warnings.add(MigrationCopy.missingDirectory(MigrationPaths.LEGACY_DIR));
        }
        if (!Files.isDirectory(targetDir)) {
            warnings.add(MigrationCopy.missingDirectory(MigrationPaths.TARGET_DIR));
        }
        return new FlowComparisonResult(cobolFiles, javaTypes.size(), javaTypes, warnings);
    }

    private int countCobolFiles(Path legacyDir) throws IOException {
        if (!Files.isDirectory(legacyDir)) return 0;
        int n = 0;
        try (Stream<Path> walk = Files.walk(legacyDir)) {
            for (Path p : walk.toList()) {
                if (!Files.isRegularFile(p)) continue;
                String name = p.getFileName().toString().toLowerCase();
                if (name.endsWith(EXT_CBL) || name.endsWith(EXT_COB) || name.endsWith(EXT_CPY)) {
                    n++;
                }
            }
        }
        return n;
    }

    private List<CodeClass> loadJavaTypes(Path targetDir) throws IOException {
        if (!Files.isDirectory(targetDir)) {
            return List.of();
        }
        return projectSources.loadDirectory(targetDir).classes();
    }

    public record FlowComparisonResult(
            int cobolSourceFiles,
            int javaTypeCount,
            List<CodeClass> javaTypes,
            List<String> warnings
    ) {
        public int warningCount() {
            return warnings.size();
        }
    }
}
