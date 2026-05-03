package com.codeflow.model;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DependencyGraph {

    public enum EdgeKind {
        METHOD_CALL,
        INJECTION,
        /** Both resolved calls and ctor/field injection between the same pair */
        MIXED
    }

    public static class Edge {
        private final String source;
        private final String target;
        private final List<String> methods;
        private final EdgeKind kind;

        public Edge(String source, String target, List<String> methods, EdgeKind kind) {
            this.source = source;
            this.target = target;
            this.methods = methods;
            this.kind = kind;
        }

        public String getSource() { return source; }
        public String getTarget() { return target; }
        public List<String> getMethods() { return methods; }
        public EdgeKind getKind() { return kind; }
    }

    private final List<String> nodes;
    private final List<Edge> edges;

    public DependencyGraph() {
        this.nodes = new ArrayList<>();
        this.edges = new ArrayList<>();
    }

    public List<String> getNodes() { return nodes; }
    public List<Edge> getEdges() { return edges; }

    private static String edgeKey(String source, String target) {
        return source + "\u0001" + target;
    }

    public static DependencyGraph build(List<CodeClass> classes) {
        DependencyGraph graph = new DependencyGraph();
        Set<String> classNames = new HashSet<>();
        for (CodeClass c : classes) {
            classNames.add(c.getName());
            graph.nodes.add(c.getName());
        }

        Map<String, Map<String, Set<String>>> callEdgeMap = new HashMap<>();
        Map<String, Map<String, Set<String>>> injectEdgeMap = new HashMap<>();

        for (CodeClass c : classes) {
            for (CodeMethod m : c.getMethods()) {
                for (MethodCall call : m.getMethodCalls()) {
                    String target = call.getEffectiveTarget();
                    if (classNames.contains(target) && !target.equals(c.getName())) {
                        callEdgeMap
                            .computeIfAbsent(c.getName(), k -> new HashMap<>())
                            .computeIfAbsent(target, k -> new HashSet<>())
                            .add(call.getTargetMethod());
                    }
                }
            }
            addConstructorInjectionEdges(c, classNames, injectEdgeMap);
            addFieldInjectionEdges(c, classNames, injectEdgeMap);
        }

        Map<String, List<String>> mergedLabels = new LinkedHashMap<>();
        Map<String, EdgeKind> mergedKind = new HashMap<>();

        for (var se : callEdgeMap.entrySet()) {
            for (var te : se.getValue().entrySet()) {
                String k = edgeKey(se.getKey(), te.getKey());
                mergedLabels.putIfAbsent(k, new ArrayList<>());
                for (String m : te.getValue()) {
                    if (!mergedLabels.get(k).contains(m)) mergedLabels.get(k).add(m);
                }
                mergedKind.put(k, EdgeKind.METHOD_CALL);
            }
        }

        for (var se : injectEdgeMap.entrySet()) {
            for (var te : se.getValue().entrySet()) {
                String k = edgeKey(se.getKey(), te.getKey());
                mergedLabels.putIfAbsent(k, new ArrayList<>());
                for (String m : te.getValue()) {
                    if (!mergedLabels.get(k).contains(m)) mergedLabels.get(k).add(m);
                }
                EdgeKind prev = mergedKind.get(k);
                if (prev == EdgeKind.METHOD_CALL) mergedKind.put(k, EdgeKind.MIXED);
                else if (prev == null) mergedKind.put(k, EdgeKind.INJECTION);
            }
        }

        for (Map.Entry<String, List<String>> e : mergedLabels.entrySet()) {
            String[] parts = e.getKey().split("\u0001", 2);
            graph.edges.add(new Edge(parts[0], parts[1], e.getValue(), mergedKind.get(e.getKey())));
        }

        return graph;
    }

    private static void addConstructorInjectionEdges(CodeClass c, Set<String> classNames,
                                                     Map<String, Map<String, Set<String>>> injectEdgeMap) {
        for (CodeMethod m : c.getMethods()) {
            if (!m.getName().equals(c.getName())) continue;
            String params = m.getParameters();
            if (params == null || params.isBlank()) continue;
            for (String part : splitTopLevelCommas(params)) {
                part = part.trim();
                if (part.isEmpty()) continue;
                String[] tokens = part.split("\\s+");
                if (tokens.length < 2) continue;
                String typeName = tokens[0].replaceAll("<[^>]+>", "").trim();
                if (classNames.contains(typeName) && !typeName.equals(c.getName())) {
                    injectEdgeMap
                            .computeIfAbsent(c.getName(), k -> new HashMap<>())
                            .computeIfAbsent(typeName, k -> new HashSet<>())
                            .add("ctor:" + tokens[tokens.length - 1]);
                }
            }
        }
    }

    private static List<String> splitTopLevelCommas(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth = Math.max(0, depth - 1);
            else if (c == ',' && depth == 0) {
                out.add(s.substring(start, i));
                start = i + 1;
            }
        }
        out.add(s.substring(start));
        return out;
    }

    private static final Pattern FIELD_LINE = Pattern.compile(
            "(?:private|public|protected)?\\s*(?:static\\s+)?(?:final\\s+)?(\\w+(?:<[^>]+>)?)\\s+(\\w+)\\s*[;=]");

    private static void addFieldInjectionEdges(CodeClass c, Set<String> classNames,
                                               Map<String, Map<String, Set<String>>> injectEdgeMap) {
        String raw = c.getRawBody();
        if (raw == null || raw.isBlank()) return;
        String[] lines = raw.split("\n", -1);
        boolean pendingInject = false;
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("*")) continue;

            if (line.startsWith("@")) {
                if (line.startsWith("@Autowired")
                        || line.contains("org.springframework.beans.factory.annotation.Autowired")
                        || line.startsWith("@Inject")
                        || line.contains("javax.inject.Inject")
                        || line.contains("jakarta.inject.Inject")) {
                    pendingInject = true;
                }
                continue;
            }

            Matcher m = FIELD_LINE.matcher(line);
            if (pendingInject && m.find()) {
                String typeName = m.group(1).replaceAll("<[^>]+>", "").trim();
                String fieldName = m.group(2);
                Set<String> skip = Set.of("if", "else", "for", "while", "return", "new", "throw", "class");
                if (!skip.contains(typeName) && classNames.contains(typeName) && !typeName.equals(c.getName())) {
                    injectEdgeMap
                            .computeIfAbsent(c.getName(), k -> new HashMap<>())
                            .computeIfAbsent(typeName, k -> new HashSet<>())
                            .add("field:" + fieldName);
                }
                pendingInject = false;
            } else {
                pendingInject = false;
            }
        }
    }
}
