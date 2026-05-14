package com.codeflow.model;

import java.util.ArrayList;
import java.util.List;

public class CodeMethod {

    private final String name;
    private final String returnType;
    private final String parameters;
    private final String body;
    private final List<FlowNode> flowNodes;
    private final List<MethodCall> methodCalls;

    public CodeMethod(String name, String returnType, String parameters, String body) {
        this.name = name;
        this.returnType = returnType != null ? returnType : "void";
        this.parameters = parameters != null ? parameters : "";
        this.body = body != null ? body : "";
        this.flowNodes = new ArrayList<>();
        this.methodCalls = new ArrayList<>();
    }

    public String getName() { return name; }
    public String getReturnType() { return returnType; }
    public String getParameters() { return parameters; }
    public String getBody() { return body; }
    public List<FlowNode> getFlowNodes() { return flowNodes; }
    public List<MethodCall> getMethodCalls() { return methodCalls; }

    public String getSignature() {
        return returnType + " " + name + "(" + parameters + ")";
    }
}
