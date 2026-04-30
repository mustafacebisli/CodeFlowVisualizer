package com.codeflow.model;

import java.util.*;

public class DependencyGraph {

    private final List<String> nodes;
    private final List<Edge> edges;

    public DependencyGraph() {
        this.nodes = new ArrayList<>();
        this.edges = new ArrayList<>();
    }

    public List<String> getNodes() { return nodes; }
    public List<Edge> getEdges() { return edges; }

    public static DependencyGraph build(List<CodeClass> classes) {
        DependencyGraph graph = new DependencyGraph();
        Set<String> classNames = new HashSet<>();
        for (CodeClass c : classes) {
            classNames.add(c.getName());
            graph.nodes.add(c.getName());
        }

        Map<String, Map<String, Set<String>>> edgeMap = new HashMap<>();

        for (CodeClass c : classes) {
            for (CodeMethod m : c.getMethods()) {
                for (MethodCall call : m.getMethodCalls()) {
                    String target = call.getEffectiveTarget();
                    if (classNames.contains(target) && !target.equals(c.getName())) {
                        edgeMap
                            .computeIfAbsent(c.getName(), k -> new HashMap<>())
                            .computeIfAbsent(target, k -> new HashSet<>())
                            .add(call.getTargetMethod());
                    }
                }
            }
        }

        for (var sourceEntry : edgeMap.entrySet()) {
            for (var targetEntry : sourceEntry.getValue().entrySet()) {
                graph.edges.add(new Edge(
                    sourceEntry.getKey(),
                    targetEntry.getKey(),
                    new ArrayList<>(targetEntry.getValue())
                ));
            }
        }

        return graph;
    }

    public static class Edge {
        private final String source;
        private final String target;
        private final List<String> methods;

        public Edge(String source, String target, List<String> methods) {
            this.source = source;
            this.target = target;
            this.methods = methods;
        }

        public String getSource() { return source; }
        public String getTarget() { return target; }
        public List<String> getMethods() { return methods; }
    }
}
