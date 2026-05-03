package com.codeflow.model;

import java.util.ArrayList;
import java.util.List;

public class CodeClass {

    public enum Kind { CLASS, INTERFACE, ENUM, RECORD }

    private final String name;
    private final Kind kind;
    private final List<String> superTypes;
    private final List<CodeField> fields;
    private final List<CodeMethod> methods;
    private final List<String> enumConstants = new ArrayList<>();
    /** Logical source file (e.g. "Cart.java") when parsed from disk; null in single-buffer editor mode */
    private String sourceFileName;
    /** Class body inside outer `{` ... `}`; used for injection heuristics */
    private String rawBody;

    public CodeClass(String name, Kind kind) {
        this.name = name;
        this.kind = kind;
        this.superTypes = new ArrayList<>();
        this.fields = new ArrayList<>();
        this.methods = new ArrayList<>();
    }

    public String getName() { return name; }
    public Kind getKind() { return kind; }
    public List<String> getSuperTypes() { return superTypes; }
    public List<CodeField> getFields() { return fields; }
    public List<CodeMethod> getMethods() { return methods; }
    public List<String> getEnumConstants() { return enumConstants; }
    public String getSourceFileName() { return sourceFileName; }
    public void setSourceFileName(String sourceFileName) { this.sourceFileName = sourceFileName; }
    public String getRawBody() { return rawBody; }
    public void setRawBody(String rawBody) { this.rawBody = rawBody; }

    public static class CodeField {
        private final String name;
        private final String typeName;
        private final boolean isFinal;

        public CodeField(String name, String typeName, boolean isFinal) {
            this.name = name;
            this.typeName = typeName;
            this.isFinal = isFinal;
        }

        public String getName() { return name; }
        public String getTypeName() { return typeName; }
        public boolean isFinal() { return isFinal; }
    }
}
