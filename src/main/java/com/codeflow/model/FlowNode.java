package com.codeflow.model;

import java.util.ArrayList;
import java.util.List;

public class FlowNode {

    public enum Type {
        START, END,
        CONDITION,       // if / else if / switch
        LOOP,            // for / while / do-while
        VARIABLE_DECL,   // int x = ...
        METHOD_CALL,     // someObject.doSomething()
        RETURN,          // return ...
        THROW,           // throw ...
        GENERAL          // other statements
    }

    private final Type type;
    private final String label;
    private final String detail;

    // Tree structure for branching
    private final List<FlowNode> trueBranch;   // if-body or loop-body
    private final List<FlowNode> falseBranch;  // else-body

    public FlowNode(Type type, String label, String detail) {
        this.type = type;
        this.label = label;
        this.detail = detail != null ? detail : "";
        this.trueBranch = new ArrayList<>();
        this.falseBranch = new ArrayList<>();
    }

    public FlowNode(Type type, String label) {
        this(type, label, "");
    }

    public Type getType() { return type; }
    public String getLabel() { return label; }
    public String getDetail() { return detail; }
    public List<FlowNode> getTrueBranch() { return trueBranch; }
    public List<FlowNode> getFalseBranch() { return falseBranch; }

    public boolean hasBranches() {
        return !trueBranch.isEmpty() || !falseBranch.isEmpty();
    }
}
