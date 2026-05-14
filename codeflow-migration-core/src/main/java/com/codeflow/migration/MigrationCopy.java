package com.codeflow.migration;

/** Pipeline ve karşılaştırma servislerinde kullanılan metin şablonları. */
public final class MigrationCopy {

    public static final String WORKSPACE_NOT_OPEN = "Once workspace acin";

    public static String missingDir(String dirName) {
        return dirName + "/ yok";
    }

    public static String missingDirectory(String dirName) {
        return dirName + "/ klasoru yok.";
    }

    public static String analyzeSummary(int cobolFiles, int javaTypes) {
        return "COBOL dosya: " + cobolFiles + ", Java tip: " + javaTypes;
    }

    public static final String NO_FLOW_WARNINGS = "Uyari yok";
    public static final String TESTS_NOT_RUN = "Henuz kosulmadi";
    public static final String SUMMARY_PENDING = "\u2014";

    public static String accuracySummary(double accuracy) {
        return String.format("%.1f%% is dogrulugu", accuracy);
    }

    public static String testRatio(int passed, int total) {
        return passed + "/" + total;
    }

    public static String caseCount(int count) {
        return count + " case";
    }

    public static String cobolWithoutJava(int cobolFiles) {
        return "COBOL kaynak var (" + cobolFiles + " dosya) ancak Java tipi bulunamadi.";
    }

    public static String javaCountLow(int javaTypes, int cobolFiles) {
        return "Java tip sayisi (" + javaTypes + ") COBOL dosya sayisina (" + cobolFiles + ") gore dusuk olabilir.";
    }

    private MigrationCopy() {
    }
}
