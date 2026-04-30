package com.codeflow.parser;

import com.codeflow.model.*;

import java.util.*;
import java.util.regex.*;

public class JavaSourceParser {

    private static final Set<String> IGNORED_TARGETS = Set.of(
        "System", "out", "err", "Math", "String", "Integer", "Double",
        "Boolean", "Long", "Float", "Arrays", "Collections", "Objects",
        "Optional", "List", "Map", "Set", "this", "super"
    );

    public List<CodeClass> parse(String source) {
        List<CodeClass> classes = extractClasses(source);
        resolveMethodCalls(classes);
        return classes;
    }

    // --- Class extraction ---

    private List<CodeClass> extractClasses(String source) {
        List<CodeClass> result = new ArrayList<>();
        Pattern p = Pattern.compile(
            "(class|interface|enum|record)\\s+(\\w+)(?:\\s+extends\\s+(\\w+))?(?:\\s+implements\\s+([^{]+))?\\s*\\{");
        Matcher m = p.matcher(source);

        while (m.find()) {
            CodeClass.Kind kind = switch (m.group(1)) {
                case "interface" -> CodeClass.Kind.INTERFACE;
                case "enum" -> CodeClass.Kind.ENUM;
                case "record" -> CodeClass.Kind.RECORD;
                default -> CodeClass.Kind.CLASS;
            };

            CodeClass cc = new CodeClass(m.group(2), kind);
            if (m.group(3) != null) cc.getSuperTypes().add(m.group(3).trim());
            if (m.group(4) != null) {
                for (String s : m.group(4).split(","))
                    cc.getSuperTypes().add(s.trim());
            }

            String body = extractBraceBlock(source, m.start() + m.group().length() - 1);
            cc.getFields().addAll(extractFields(body));
            cc.getMethods().addAll(extractMethods(body, cc.getName()));
            result.add(cc);
        }
        return result;
    }

    // --- Field extraction ---

    private List<CodeClass.CodeField> extractFields(String body) {
        List<CodeClass.CodeField> fields = new ArrayList<>();
        Pattern p = Pattern.compile(
            "(?:private|protected|public)?\\s*(?:static\\s+)?(?:final\\s+)?(\\w+(?:<[^>]+>)?)\\s+(\\w+)\\s*[;=]");
        Matcher m = p.matcher(body);
        Set<String> skip = Set.of("return", "if", "else", "for", "while", "class", "new", "void", "throw");
        while (m.find()) {
            String type = m.group(1);
            String name = m.group(2);
            if (skip.contains(type) || skip.contains(name)) continue;
            boolean isFinal = body.substring(Math.max(0, m.start() - 10), m.start()).contains("final");
            fields.add(new CodeClass.CodeField(name, type, isFinal));
        }
        return fields;
    }

    // --- Method extraction ---

    private List<CodeMethod> extractMethods(String classBody, String ownerClass) {
        List<CodeMethod> methods = new ArrayList<>();
        Pattern p = Pattern.compile(
            "(?:public|private|protected)?\\s*(?:static\\s+)?(?:final\\s+)?(?:synchronized\\s+)?" +
            "(\\w+(?:<[^>]+>)?)\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*(?:throws\\s+[^{]+)?\\s*\\{");
        Matcher m = p.matcher(classBody);

        Set<String> skipReturnTypes = Set.of("if", "else", "for", "while", "switch", "catch", "new", "return", "class");

        while (m.find()) {
            String returnType = m.group(1);
            String name = m.group(2);
            String params = m.group(3).trim();

            if (skipReturnTypes.contains(returnType)) continue;
            if (skipReturnTypes.contains(name)) continue;

            String methodBody = extractBraceBlock(classBody, m.start() + m.group().length() - 1);

            CodeMethod method = new CodeMethod(name, returnType, params, methodBody);
            method.getFlowNodes().addAll(buildFlowTree(methodBody));
            method.getMethodCalls().addAll(extractMethodCalls(methodBody, ownerClass, name));
            methods.add(method);
        }
        return methods;
    }

    // ========== TREE-BASED FLOW PARSING ==========

    private List<FlowNode> buildFlowTree(String body) {
        List<String> statements = splitStatements(body);
        return parseStatementList(statements, 0, statements.size());
    }

    private List<FlowNode> parseStatementList(List<String> stmts, int from, int to) {
        List<FlowNode> nodes = new ArrayList<>();
        int i = from;
        while (i < to) {
            String s = stmts.get(i);

            if (s.startsWith("IF:")) {
                i = parseIfChain(stmts, i, to, nodes);
            } else if (s.startsWith("FOR:") || s.startsWith("WHILE:") || s.startsWith("DOWHILE:")) {
                i = parseLoop(stmts, i, to, nodes);
            } else if (s.startsWith("BLOCK_START") || s.startsWith("BLOCK_END")) {
                i++;
            } else {
                nodes.add(parseSimpleStatement(s));
                i++;
            }
        }
        return nodes;
    }

    private int parseIfChain(List<String> stmts, int pos, int limit, List<FlowNode> out) {
        String ifStmt = stmts.get(pos);
        String cond = ifStmt.substring(3);
        FlowNode ifNode = new FlowNode(FlowNode.Type.CONDITION, "if", cond);

        int i = pos + 1;

        // True branch: collect until matching BLOCK_END
        int blockEnd = findBlockEnd(stmts, i, limit);
        if (i < blockEnd) {
            ifNode.getTrueBranch().addAll(parseStatementList(stmts, i + 1, blockEnd));
        }
        i = blockEnd + 1;

        // Check for else if / else chains -> put them in falseBranch
        if (i < limit) {
            String next = stmts.get(i);
            if (next.startsWith("ELSEIF:")) {
                // else if becomes a nested condition in the false branch
                i = parseIfChain_elseIf(stmts, i, limit, ifNode.getFalseBranch());
            } else if (next.equals("ELSE")) {
                i++;
                int elseEnd = findBlockEnd(stmts, i, limit);
                if (i < elseEnd) {
                    ifNode.getFalseBranch().addAll(parseStatementList(stmts, i + 1, elseEnd));
                }
                i = elseEnd + 1;
            }
        }

        out.add(ifNode);
        return i;
    }

    private int parseIfChain_elseIf(List<String> stmts, int pos, int limit, List<FlowNode> out) {
        String stmt = stmts.get(pos);
        String cond = stmt.substring(7); // "ELSEIF:..."
        FlowNode node = new FlowNode(FlowNode.Type.CONDITION, "else if", cond);

        int i = pos + 1;
        int blockEnd = findBlockEnd(stmts, i, limit);
        if (i < blockEnd) {
            node.getTrueBranch().addAll(parseStatementList(stmts, i + 1, blockEnd));
        }
        i = blockEnd + 1;

        if (i < limit) {
            String next = stmts.get(i);
            if (next.startsWith("ELSEIF:")) {
                i = parseIfChain_elseIf(stmts, i, limit, node.getFalseBranch());
            } else if (next.equals("ELSE")) {
                i++;
                int elseEnd = findBlockEnd(stmts, i, limit);
                if (i < elseEnd) {
                    node.getFalseBranch().addAll(parseStatementList(stmts, i + 1, elseEnd));
                }
                i = elseEnd + 1;
            }
        }

        out.add(node);
        return i;
    }

    private int parseLoop(List<String> stmts, int pos, int limit, List<FlowNode> out) {
        String s = stmts.get(pos);
        FlowNode.Type type = FlowNode.Type.LOOP;
        String label, detail;

        if (s.startsWith("FOR:")) {
            label = "for";
            detail = s.substring(4);
        } else if (s.startsWith("WHILE:")) {
            label = "while";
            detail = s.substring(6);
        } else {
            label = "do-while";
            detail = s.length() > 8 ? s.substring(8) : "";
        }

        FlowNode loopNode = new FlowNode(type, label, detail);

        int i = pos + 1;
        int blockEnd = findBlockEnd(stmts, i, limit);
        if (i < blockEnd) {
            loopNode.getTrueBranch().addAll(parseStatementList(stmts, i + 1, blockEnd));
        }
        i = blockEnd + 1;

        out.add(loopNode);
        return i;
    }

    private int findBlockEnd(List<String> stmts, int pos, int limit) {
        if (pos >= limit || !stmts.get(pos).equals("BLOCK_START")) return pos;
        int depth = 0;
        for (int i = pos; i < limit; i++) {
            if (stmts.get(i).equals("BLOCK_START")) depth++;
            if (stmts.get(i).equals("BLOCK_END")) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return limit;
    }

    /**
     * Split method body into structured statement tokens.
     * Converts raw lines into: IF:cond, ELSEIF:cond, ELSE, FOR:expr,
     * WHILE:cond, DOWHILE:, BLOCK_START, BLOCK_END, and plain statements.
     */
    private List<String> splitStatements(String body) {
        List<String> result = new ArrayList<>();
        String[] lines = body.split("\\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("if ") || line.startsWith("if(")) {
                result.add("IF:" + extractParenContent(line, "if"));
                if (line.contains("{")) result.add("BLOCK_START");
            } else if (line.startsWith("} else if") || line.startsWith("else if")) {
                if (line.startsWith("}")) result.add("BLOCK_END");
                result.add("ELSEIF:" + extractParenContent(line, "else if"));
                if (line.contains("{")) result.add("BLOCK_START");
            } else if (line.startsWith("} else") || line.equals("else {") || line.equals("else")
                       || line.startsWith("} else {")) {
                if (line.startsWith("}")) result.add("BLOCK_END");
                result.add("ELSE");
                if (line.contains("{")) result.add("BLOCK_START");
            } else if (line.equals("}")) {
                result.add("BLOCK_END");
            } else if (line.startsWith("for ") || line.startsWith("for(")) {
                result.add("FOR:" + extractParenContent(line, "for"));
                if (line.contains("{")) result.add("BLOCK_START");
            } else if (line.startsWith("while ") || line.startsWith("while(")) {
                result.add("WHILE:" + extractParenContent(line, "while"));
                if (line.contains("{")) result.add("BLOCK_START");
            } else if (line.startsWith("do ") || line.equals("do {") || line.equals("do{")) {
                result.add("DOWHILE:");
                if (line.contains("{")) result.add("BLOCK_START");
            } else if (line.equals("{")) {
                result.add("BLOCK_START");
            } else {
                result.add(line);
            }
        }
        return result;
    }

    private FlowNode parseSimpleStatement(String line) {
        if (line.startsWith("return")) {
            String val = line.replaceFirst("return\\s*", "").replace(";", "").trim();
            return new FlowNode(FlowNode.Type.RETURN, "return", val);
        } else if (line.startsWith("throw")) {
            String val = line.replaceFirst("throw\\s*", "").replace(";", "").trim();
            return new FlowNode(FlowNode.Type.THROW, "throw", val);
        } else if (isVariableDeclaration(line)) {
            String decl = line.replace(";", "").trim();
            return new FlowNode(FlowNode.Type.VARIABLE_DECL, shortDecl(decl), decl);
        } else if (line.contains("(") && !line.startsWith("//") && !line.startsWith("case ")
                    && !line.startsWith("@") && !line.startsWith("*")) {
            String callName = line.substring(0, line.indexOf('(')).trim();
            if (!callName.isEmpty() && !callName.startsWith("new ")) {
                return new FlowNode(FlowNode.Type.METHOD_CALL, shortCall(callName), line.replace(";", "").trim());
            }
        }
        return new FlowNode(FlowNode.Type.GENERAL, line.replace(";", "").trim());
    }

    private boolean isVariableDeclaration(String line) {
        return line.matches("^(int|long|float|double|boolean|char|byte|short|String|var|final\\s+\\w+|" +
                            "List|Map|Set|\\w+)\\s+\\w+\\s*[=;].*");
    }

    private String shortDecl(String decl) {
        int eq = decl.indexOf('=');
        if (eq > 0) return decl.substring(0, eq).trim();
        return decl;
    }

    private String shortCall(String call) {
        if (call.length() > 30) return call.substring(0, 27) + "...";
        return call;
    }

    // --- Method call extraction ---

    private List<MethodCall> extractMethodCalls(String body, String ownerClass, String ownerMethod) {
        List<MethodCall> calls = new ArrayList<>();
        Pattern p = Pattern.compile("(\\w+)\\.(\\w+)\\s*\\(");
        Matcher m = p.matcher(body);
        Set<String> seen = new HashSet<>();

        while (m.find()) {
            String target = m.group(1);
            String method = m.group(2);
            String key = target + "." + method;
            if (seen.contains(key) || IGNORED_TARGETS.contains(target)) continue;
            seen.add(key);
            calls.add(new MethodCall(ownerClass, ownerMethod, target, method));
        }
        return calls;
    }

    // --- Resolve calls across classes ---

    private void resolveMethodCalls(List<CodeClass> classes) {
        Map<String, String> fieldTypeMap = new HashMap<>();
        Set<String> classNames = new HashSet<>();
        for (CodeClass c : classes) {
            classNames.add(c.getName());
            for (CodeClass.CodeField f : c.getFields()) {
                fieldTypeMap.put(c.getName() + "." + f.getName(), f.getTypeName());
            }
        }

        for (CodeClass c : classes) {
            for (CodeMethod m : c.getMethods()) {
                for (MethodCall call : m.getMethodCalls()) {
                    String expr = call.getTargetExpression();
                    if (classNames.contains(expr)) {
                        call.setResolvedTargetClass(expr);
                    } else {
                        String fieldKey = c.getName() + "." + expr;
                        String type = fieldTypeMap.get(fieldKey);
                        if (type != null && classNames.contains(type)) {
                            call.setResolvedTargetClass(type);
                        }
                    }
                }
            }
        }
    }

    // --- Helpers ---

    private String extractBraceBlock(String source, int braceStart) {
        if (braceStart >= source.length() || source.charAt(braceStart) != '{') return "";
        int depth = 0;
        int i = braceStart;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}') depth--;
            if (depth == 0) break;
            i++;
        }
        if (i <= braceStart + 1) return "";
        return source.substring(braceStart + 1, i);
    }

    private String extractParenContent(String line, String keyword) {
        int start = line.indexOf('(');
        if (start < 0) return line.replace(keyword, "").replace("{", "").trim();
        int depth = 0;
        int end = start;
        while (end < line.length()) {
            if (line.charAt(end) == '(') depth++;
            if (line.charAt(end) == ')') depth--;
            if (depth == 0) break;
            end++;
        }
        if (end > start + 1) return line.substring(start + 1, end);
        return "";
    }
}
