package com.codeflow.model;

public class MethodCall {

    private final String callerClass;
    private final String callerMethod;
    private final String targetExpression;
    private final String targetMethod;
    private String resolvedTargetClass;

    public MethodCall(String callerClass, String callerMethod,
                      String targetExpression, String targetMethod) {
        this.callerClass = callerClass;
        this.callerMethod = callerMethod;
        this.targetExpression = targetExpression;
        this.targetMethod = targetMethod;
    }

    public String getCallerClass() { return callerClass; }
    public String getCallerMethod() { return callerMethod; }
    public String getTargetExpression() { return targetExpression; }
    public String getTargetMethod() { return targetMethod; }
    public String getResolvedTargetClass() { return resolvedTargetClass; }

    public void setResolvedTargetClass(String cls) { this.resolvedTargetClass = cls; }

    public String getEffectiveTarget() {
        return resolvedTargetClass != null ? resolvedTargetClass : targetExpression;
    }
}
