package com.codeflow.migration.model;

/** Pipeline adımı durumu. */
public enum StepStatus {
    PENDING,
    RUNNING,
    OK,
    WARNING,
    ERROR
}
