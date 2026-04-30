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
