package com.codeflow.migration.model;

/** Pipeline üst şeridindeki adımlar. */
public enum MigrationStepKind {
    LEGACY_LINKED("Eski kaynak"),
    TARGET_LINKED("Yeni kaynak"),
    ANALYZED("Analiz"),
    FLOW_COMPARED("Akis karsilastirma"),
    GOLDEN_LOADED("Golden testler"),
    TESTS_RUN("Test kosumu"),
    SUMMARY("Sonuc ozeti");

    public final String label;

    MigrationStepKind(String label) {
        this.label = label;
    }
}
