package com.codeflow.parser;

import com.codeflow.model.CodeClass;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Klasördeki {@code .java} dosyalarını birleşik editör tamponu ve {@link CodeClass} listesine dönüştürür.
 */
public final class JavaProjectSources {

    private final JavaSourceParser parser;

    public JavaProjectSources(JavaSourceParser parser) {
        this.parser = parser;
    }

    public LoadResult loadDirectory(Path root) throws IOException {
        StringBuilder allCode = new StringBuilder();
        List<CodeClass> allClasses = new ArrayList<>();

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!file.toString().endsWith(SourceExtensions.JAVA)) {
                    return FileVisitResult.CONTINUE;
                }
                String content = Files.readString(file);
                String logical = root.relativize(file).toString().replace('\\', '/');
                allCode.append("\n// === ").append(logical).append(" ===\n");
                allCode.append(content).append('\n');
                allClasses.addAll(parser.parseUnresolved(content, logical));
                return FileVisitResult.CONTINUE;
            }
        });

        parser.resolveCrossFile(allClasses);
        return new LoadResult(allCode.toString(), List.copyOf(allClasses));
    }

    public record LoadResult(String combinedCode, List<CodeClass> classes) {
    }
}
