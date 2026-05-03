package com.codeflow.model;

import java.util.ArrayList;
import java.util.List;

public class FlowNode {

    public enum Type {
        START, END,
        CONDITION,       // if / else if
        SWITCH,          // switch(expr) with case arms (see switchArms)
        TRY_CATCH,       // try / catch / finally arms (see tryCatchArms)
        SYNC_BLOCK,      // synchronized (lock) { ... }
        LOOP,            // for / while / do-while
        VARIABLE_DECL,   // int x = ...
        METHOD_CALL,     // someObject.doSomething()
        RETURN,          // return ...
        YIELD_STMT,      // switch ifadesi: yield ...
        THROW,           // throw ...
        ASSERT_COND,     // assert condition [: message]
        BREAK,           // break
        CONTINUE,        // continue
        GENERAL          // other statements
    }

    /** One arm of a switch: label is case constant or "default" */
    public static final class SwitchArm {
        private final String label;
        private final List<FlowNode> body;

        public SwitchArm(String label, List<FlowNode> body) {
            this.label = label;
            this.body = body != null ? body : new ArrayList<>();
        }

        public String getLabel() { return label; }
        public List<FlowNode> getBody() { return body; }
    }

    /** try / catch (...) / finally kolu */
    public static final class TryCatchArm {
        private final String header;
        private final List<FlowNode> body;

        public TryCatchArm(String header, List<FlowNode> body) {
            this.header = header != null ? header : "";
            this.body = body != null ? body : new ArrayList<>();
        }

        public String getHeader() { return header; }
        public List<FlowNode> getBody() { return body; }
    }

    private final Type type;
    private final String label;
    private final String detail;

    // Tree structure for branching
    private final List<FlowNode> trueBranch;   // if-body or loop-body
    private final List<FlowNode> falseBranch;  // else-body
    private final List<SwitchArm> switchArms = new ArrayList<>();
    private final List<TryCatchArm> tryCatchArms = new ArrayList<>();

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
    public List<SwitchArm> getSwitchArms() { return switchArms; }
    public List<TryCatchArm> getTryCatchArms() { return tryCatchArms; }

    public boolean hasBranches() {
        return !trueBranch.isEmpty() || !falseBranch.isEmpty();
    }

    public boolean hasSwitchArms() {
        return !switchArms.isEmpty();
    }

    public boolean hasTryCatchArms() {
        return !tryCatchArms.isEmpty();
    }
}
