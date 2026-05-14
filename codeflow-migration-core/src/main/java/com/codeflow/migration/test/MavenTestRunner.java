package com.codeflow.migration.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Hedef Maven projesinde {@code mvn -q test} calistirir. */
public final class MavenTestRunner {

    private static final Pattern SUREFIRE_SUMMARY = Pattern.compile(
            "Tests run:\\s*(\\d+),\\s*Failures:\\s*(\\d+),\\s*Errors:\\s*(\\d+),\\s*Skipped:\\s*(\\d+)");

    public MavenTestResult runTests(Path targetMavenRoot, long timeoutMinutes) throws IOException, InterruptedException {
        if (!Files.isRegularFile(targetMavenRoot.resolve("pom.xml"))) {
            return MavenTestResult.noPom(targetMavenRoot);
        }
        ProcessBuilder pb = new ProcessBuilder("mvn", "-q", "test");
        pb.directory(targetMavenRoot.toFile());
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        StringBuilder log = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.append(line).append('\n');
            }
        }
        boolean finished = proc.waitFor(timeoutMinutes, TimeUnit.MINUTES);
        if (!finished) {
            proc.destroyForcibly();
            return new MavenTestResult(false, 0, 0, 0, log + "\n[timeout]\n");
        }
        int exit = proc.exitValue();
        String text = log.toString();
        Matcher m = SUREFIRE_SUMMARY.matcher(text);
        if (m.find()) {
            int run = Integer.parseInt(m.group(1));
            int failures = Integer.parseInt(m.group(2));
            int errors = Integer.parseInt(m.group(3));
            int passed = Math.max(0, run - failures - errors);
            return new MavenTestResult(exit == 0, passed, run, failures + errors, text);
        }
        boolean ok = exit == 0;
        return new MavenTestResult(ok, ok ? 1 : 0, 1, ok ? 0 : 1, text);
    }

    public record MavenTestResult(
            boolean success,
            int passed,
            int total,
            int failed,
            String log
    ) {
        static MavenTestResult noPom(Path dir) {
            return new MavenTestResult(false, 0, 0, 0, "pom.xml bulunamadi: " + dir);
        }

        public double accuracyPercent() {
            if (total <= 0) return 0;
            return 100.0 * passed / total;
        }
    }
}
