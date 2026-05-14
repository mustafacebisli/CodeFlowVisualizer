package com.codeflow.migration.model;

/** Tek pipeline adımı görünümü. */
public record MigrationStepView(MigrationStepKind kind, StepStatus status, String detail) {
}
