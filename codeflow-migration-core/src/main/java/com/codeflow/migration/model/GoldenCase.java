package com.codeflow.migration.model;

/** Tek golden test case. */
public record GoldenCase(
        String id,
        String program,
        GoldenCaseStatus status,
        String message
) {
}
