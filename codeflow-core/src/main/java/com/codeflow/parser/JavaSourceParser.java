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

    /** Noktasız çağrı eşlemesinde atlanacak anahtar kelimeler / tipler */
    private static final Set<String> UNQUALIFIED_CALL_SKIP = Set.of(
            "if", "while", "for", "switch", "catch", "try", "new", "return", "throw", "else", "do",
            "case", "default", "int", "long", "void", "boolean", "byte", "short", "float", "double",
            "char", "var", "class", "interface", "enum", "record", "extends", "implements", "import",
            "package", "null", "true", "false", "instanceof", "final", "static", "public", "private",
            "protected", "synchronized", "volatile", "transient", "native", "strictfp", "assert",
            "yield", "break", "continue", "super", "this", "new");

    public List<CodeClass> parse(String source) {
        return parse(source, null);
    }

    /**
     * @param sourceFileName optional logical file name (e.g. {@code Foo.java}) for explorer / tagging
     */
    public List<CodeClass> parse(String source, String sourceFileName) {
        List<CodeClass> classes = extractClasses(source, sourceFileName);
        resolveMethodCalls(classes);
        return classes;
    }

    /** Parses without cross-file call resolution; merge lists then call {@link #resolveCrossFile}. */
    public List<CodeClass> parseUnresolved(String source, String sourceFileName) {
        return extractClasses(source, sourceFileName);
    }

    public void resolveCrossFile(List<CodeClass> classes) {
        resolveMethodCalls(classes);
    }

    // --- Class extraction ---

    private List<CodeClass> extractClasses(String source, String sourceFileName) {
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
            cc.setRawBody(body);
            if (sourceFileName != null) {
                cc.setSourceFileName(sourceFileName);
            }

            if (kind == CodeClass.Kind.ENUM) {
                EnumBodySplit split = splitEnumBody(body);
                cc.getEnumConstants().addAll(split.constants);
                Set<String> skipNames = new HashSet<>(split.constants);
                cc.getFields().addAll(extractFields(split.remainder, skipNames));
                cc.getMethods().addAll(extractMethods(split.remainder, cc.getName()));
            } else {
                cc.getFields().addAll(extractFields(body, Collections.emptySet()));
                cc.getMethods().addAll(extractMethods(body, cc.getName()));
            }
            result.add(cc);
        }
        return result;
    }

    private static final class EnumBodySplit {
        final List<String> constants = new ArrayList<>();
        String remainder = "";
    }

    private EnumBodySplit splitEnumBody(String body) {
        EnumBodySplit r = new EnumBodySplit();
        int semi = indexOfTopLevelSemicolon(body);
        String constantsChunk;
        if (semi >= 0) {
            constantsChunk = body.substring(0, semi).trim();
            r.remainder = body.substring(semi + 1).trim();
        } else {
            Pattern memberStart = Pattern.compile(
                    "(?m)^\\s*(?:@\\w+(?:\\.\\w+)*\\s+)*(?:public|private|protected|static)\\s+");
            Matcher mm = memberStart.matcher(body);
            if (mm.find()) {
                constantsChunk = body.substring(0, mm.start()).trim();
                r.remainder = body.substring(mm.start()).trim();
            } else {
                constantsChunk = body.trim();
                r.remainder = "";
            }
        }
        r.constants.addAll(parseEnumConstantIdentifiers(constantsChunk));
        return r;
    }

    private static int indexOfTopLevelSemicolon(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth = Math.max(0, depth - 1);
            else if (c == ';' && depth == 0) return i;
        }
        return -1;
    }

    private static List<String> parseEnumConstantIdentifiers(String constantsChunk) {
        List<String> names = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < constantsChunk.length(); i++) {
            char c = constantsChunk.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth = Math.max(0, depth - 1);
            else if (c == ',' && depth == 0) {
                addFirstEnumIdentifier(cur.toString(), names);
                cur.setLength(0);
            } else cur.append(c);
        }
        addFirstEnumIdentifier(cur.toString(), names);
        return names;
    }

    private static void addFirstEnumIdentifier(String chunk, List<String> names) {
        String s = chunk.trim();
        if (s.isEmpty()) return;
        int cmt = s.indexOf("//");
        if (cmt >= 0) s = s.substring(0, cmt).trim();
        Matcher m = Pattern.compile("^([A-Za-z_]\\w*)").matcher(s);
        if (m.find()) names.add(m.group(1));
    }

    // --- Field extraction ---

    private List<CodeClass.CodeField> extractFields(String body, Set<String> skipNames) {
        List<CodeClass.CodeField> fields = new ArrayList<>();
        Pattern p = Pattern.compile(
            "(?:private|protected|public)?\\s*(?:static\\s+)?(?:final\\s+)?(\\w+(?:<[^>]+>)?)\\s+(\\w+)\\s*[;=]");
        Matcher m = p.matcher(body);
        Set<String> skip = new HashSet<>(Set.of(
                "return", "if", "else", "for", "while", "class", "new", "void", "throw", "case", "switch", "default",
                "try", "catch", "finally", "assert", "break", "continue", "synchronized", "yield"));
        while (m.find()) {
            String type = m.group(1);
            String name = m.group(2);
            if (skip.contains(type) || skip.contains(name)) continue;
            if (skipNames.contains(name)) continue;
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

        Set<String> skipReturnTypes = Set.of(
                "if", "else", "for", "while", "switch", "catch", "new", "return", "class", "try");

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
            } else if (s.equals("TRY:")) {
                i = parseTryChain(stmts, i, to, nodes);
            } else if (s.startsWith("SWITCH_HEAD:")) {
                i = parseSwitch(stmts, i, to, nodes);
            } else if (s.startsWith("SYNC_HEAD:")) {
                i = parseSyncBlock(stmts, i, to, nodes);
            } else if (s.startsWith("FOR:") || s.startsWith("WHILE:") || s.startsWith("DOWHILE:")
                    || s.startsWith("FOREACH:")) {
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

    private int parseSwitch(List<String> stmts, int pos, int limit, List<FlowNode> out) {
        String head = stmts.get(pos);
        String expr = head.substring("SWITCH_HEAD:".length());
        int i = pos + 1;
        if (i >= limit || !stmts.get(i).equals("BLOCK_START")) {
            FlowNode sw = new FlowNode(FlowNode.Type.SWITCH, "switch", expr);
            out.add(sw);
            return i;
        }
        int blockEnd = findBlockEnd(stmts, i, limit);
        List<String> inner = stmts.subList(i + 1, blockEnd);
        List<FlowNode.SwitchArm> arms = new ArrayList<>();
        int j = 0;
        while (j < inner.size()) {
            String t = inner.get(j);
            if (t.startsWith("CASE:")) {
                String lab = t.substring("CASE:".length());
                j++;
                List<String> caseToks = new ArrayList<>();
                while (j < inner.size()) {
                    String u = inner.get(j);
                    if (u.startsWith("CASE:") || u.equals("DEFAULT") || u.startsWith("DEFAULT:")) break;
                    caseToks.add(u);
                    j++;
                }
                arms.add(new FlowNode.SwitchArm(lab, parseStatementList(caseToks, 0, caseToks.size())));
            } else if (t.equals("DEFAULT") || t.startsWith("DEFAULT:")) {
                String lab = t.startsWith("DEFAULT:") ? t.substring("DEFAULT:".length()) : "default";
                j++;
                List<String> caseToks = new ArrayList<>();
                while (j < inner.size()) {
                    String u = inner.get(j);
                    if (u.startsWith("CASE:") || u.equals("DEFAULT") || u.startsWith("DEFAULT:")) break;
                    caseToks.add(u);
                    j++;
                }
                arms.add(new FlowNode.SwitchArm(lab.isEmpty() ? "default" : lab,
                        parseStatementList(caseToks, 0, caseToks.size())));
            } else {
                j++;
            }
        }
        FlowNode sw = new FlowNode(FlowNode.Type.SWITCH, "switch", expr);
        sw.getSwitchArms().addAll(arms);
        out.add(sw);
        return blockEnd + 1;
    }

    private int parseTryChain(List<String> stmts, int pos, int limit, List<FlowNode> out) {
        List<FlowNode.TryCatchArm> arms = new ArrayList<>();
        int i = pos;
        if (!stmts.get(i).equals("TRY:")) {
            return pos + 1;
        }
        i++;
        if (i >= limit) {
            FlowNode empty = new FlowNode(FlowNode.Type.TRY_CATCH, "try/catch", "");
            out.add(empty);
            return i;
        }
        int tryEnd = findBlockEnd(stmts, i, limit);
        List<FlowNode> tryBody = (i < tryEnd)
                ? parseStatementList(stmts, i + 1, tryEnd)
                : new ArrayList<>();
        arms.add(new FlowNode.TryCatchArm("try", tryBody));
        i = tryEnd + 1;

        while (i < limit && stmts.get(i).startsWith("CATCH:")) {
            String param = stmts.get(i).substring("CATCH:".length()).trim();
            i++;
            int catchEnd = findBlockEnd(stmts, i, limit);
            List<FlowNode> catchBody = (i < catchEnd)
                    ? parseStatementList(stmts, i + 1, catchEnd)
                    : new ArrayList<>();
            arms.add(new FlowNode.TryCatchArm("catch (" + param + ")", catchBody));
            i = catchEnd + 1;
        }

        if (i < limit && stmts.get(i).equals("FINALLY")) {
            i++;
            int finEnd = findBlockEnd(stmts, i, limit);
            List<FlowNode> finBody = (i < finEnd)
                    ? parseStatementList(stmts, i + 1, finEnd)
                    : new ArrayList<>();
            arms.add(new FlowNode.TryCatchArm("finally", finBody));
            i = finEnd + 1;
        }

        FlowNode node = new FlowNode(FlowNode.Type.TRY_CATCH, "try/catch", "");
        node.getTryCatchArms().addAll(arms);
        out.add(node);
        return i;
    }

    private int parseSyncBlock(List<String> stmts, int pos, int limit, List<FlowNode> out) {
        String s = stmts.get(pos);
        String detail = s.length() > "SYNC_HEAD:".length()
                ? s.substring("SYNC_HEAD:".length()).trim()
                : "";
        int i = pos + 1;
        int blockEnd = findBlockEnd(stmts, i, limit);
        FlowNode node = new FlowNode(FlowNode.Type.SYNC_BLOCK, "synchronized", detail);
        if (i < blockEnd) {
            node.getTrueBranch().addAll(parseStatementList(stmts, i + 1, blockEnd));
        }
        out.add(node);
        return blockEnd + 1;
    }

    private int parseIfChain(List<String> stmts, int pos, int limit, List<FlowNode> out) {
        String ifStmt = stmts.get(pos);
        String cond = ifStmt.substring(3);
        FlowNode ifNode = new FlowNode(FlowNode.Type.CONDITION, "if", cond);

        int i = pos + 1;

        int blockEnd = findBlockEnd(stmts, i, limit);
        if (i < blockEnd) {
            ifNode.getTrueBranch().addAll(parseStatementList(stmts, i + 1, blockEnd));
        }
        i = blockEnd + 1;

        if (i < limit) {
            String next = stmts.get(i);
            if (next.startsWith("ELSEIF:")) {
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
        String cond = stmt.substring(7);
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
        String label;
        String detail;

        if (s.startsWith("FOR:")) {
            label = "for";
            detail = s.substring(4);
        } else if (s.startsWith("FOREACH:")) {
            label = "forEach";
            detail = s.substring("FOREACH:".length());
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

        if ("do-while".equals(label) && i < limit && stmts.get(i).startsWith("WHILE:")) {
            String w = stmts.get(i);
            FlowNode withCond = new FlowNode(FlowNode.Type.LOOP, label, w.substring(6));
            withCond.getTrueBranch().addAll(loopNode.getTrueBranch());
            loopNode = withCond;
            i++;
        }

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
     * {@code lines[startLine]} içinde {@code openParenColumn} konumundaki {@code '('} ile eşleşen
     * kapanan {@code ')'} satır ve sütununu bulur (çok satırlı {@code for}/{@code while} koşulu).
     */
    private static int[] findClosingParen(String[] lines, int startLine, int openParenColumn) {
        int depth = 0;
        for (int i = startLine; i < lines.length; i++) {
            String l = lines[i];
            int from = (i == startLine) ? openParenColumn : 0;
            for (int c = from; c < l.length(); c++) {
                char ch = l.charAt(c);
                if (ch == '(') {
                    depth++;
                } else if (ch == ')') {
                    depth--;
                    if (depth == 0) {
                        return new int[]{i, c};
                    }
                }
            }
        }
        return new int[]{startLine, Math.max(openParenColumn, lines[startLine].length() - 1)};
    }

    /** Parantez bloğunu tek satır benzeri metne birleştirir (parse için). */
    private static String mergeLinesForParen(String[] lines, int startLine, int startCol,
                                             int endLine, int endCol) {
        if (startLine == endLine) {
            return lines[startLine].substring(startCol, Math.min(endCol + 1, lines[startLine].length())).trim();
        }
        StringBuilder sb = new StringBuilder();
        sb.append(lines[startLine].substring(startCol).trim());
        for (int i = startLine + 1; i < endLine; i++) {
            sb.append(' ').append(lines[i].trim());
        }
        sb.append(' ').append(lines[endLine].substring(0, Math.min(endCol + 1, lines[endLine].length())).trim());
        return sb.toString().trim();
    }

    private static boolean hasBlockBraceAfter(String[] lines, int closeLine, int closeCol) {
        if (closeLine >= lines.length) return false;
        String tail = lines[closeLine].substring(Math.min(closeCol + 1, lines[closeLine].length()));
        if (tail.contains("{")) return true;
        int nx = nextNonEmptyLine(lines, closeLine + 1);
        return nx >= 0 && "{".equals(lines[nx].trim());
    }

    /**
     * Split method body into structured statement tokens (line-oriented heuristics).
     * <ul>
     *   <li>{@code for}/{@code while}: koşul parantezi çok satıra yayılabilir; açılış süslü aynı satır veya sonraki satırda.</li>
     *   <li>{@code .forEach(} zincir çağrısı — akışta {@code forEach} döngü düğümü.</li>
     *   <li>{@code do} ... {@code \}} {@code while(cond);}: closing brace and trailing {@code while} on one line.</li>
     *   <li>{@code switch}, {@code case}, {@code default} for flowchart arms (no fall-through modelling).</li>
     *   <li>{@code try}/{@code catch}/{@code finally}, {@code synchronized}, {@code assert}, {@code break}/{@code continue}, {@code yield}, {@code case x ->} (satır tabanlı).</li>
     * </ul>
     */
    private List<String> splitStatements(String body) {
        List<String> result = new ArrayList<>();
        String[] lines = body.split("\\r?\\n", -1);
        int idx = 0;
        while (idx < lines.length) {
            String line = lines[idx].trim();
            if (line.isEmpty()) {
                idx++;
                continue;
            }

            if (line.matches("(?s)\\}\\s*while\\s*\\(.*")) {
                result.add("BLOCK_END");
                int wAt = line.indexOf("while");
                result.add("WHILE:" + extractParenContent(line.substring(wAt), "while"));
                idx++;
                continue;
            }

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
            } else if (line.startsWith("} catch") || line.startsWith("}catch")) {
                if (line.startsWith("}")) result.add("BLOCK_END");
                int cAt = line.indexOf("catch");
                String inner = extractParenContent(line.substring(cAt), "catch");
                result.add("CATCH:" + inner);
                if (line.contains("{")) result.add("BLOCK_START");
            } else if (line.equals("}")) {
                result.add("BLOCK_END");
            } else if (line.equals("try") || line.startsWith("try ") || line.startsWith("try{") || line.startsWith("try(")) {
                result.add("TRY:");
                if (line.contains("{")) {
                    result.add("BLOCK_START");
                } else {
                    int next = nextNonEmptyLine(lines, idx + 1);
                    if (next >= 0 && lines[next].trim().equals("{")) result.add("BLOCK_START");
                }
            } else if (line.equals("finally") || line.startsWith("finally ") || line.startsWith("finally{")) {
                result.add("FINALLY");
                if (line.contains("{")) {
                    result.add("BLOCK_START");
                } else {
                    int next = nextNonEmptyLine(lines, idx + 1);
                    if (next >= 0 && lines[next].trim().equals("{")) result.add("BLOCK_START");
                }
            } else if (line.startsWith("synchronized ") || line.startsWith("synchronized(")) {
                result.add("SYNC_HEAD:" + extractParenContent(line, "synchronized"));
                if (line.contains("{")) {
                    result.add("BLOCK_START");
                } else {
                    int next = nextNonEmptyLine(lines, idx + 1);
                    if (next >= 0 && lines[next].trim().equals("{")) result.add("BLOCK_START");
                }
            } else if (line.startsWith("switch ") || line.startsWith("switch(")) {
                result.add("SWITCH_HEAD:" + extractParenContent(line, "switch"));
                if (line.contains("{")) result.add("BLOCK_START");
            } else if (line.contains("->")) {
                String t = line.trim();
                int arrow = t.indexOf("->");
                String left = t.substring(0, arrow).trim();
                String right = t.substring(arrow + 2).trim();
                if (left.startsWith("case ") || left.startsWith("case:")) {
                    String lab = left.startsWith("case:") ? left.substring(5).trim() : left.substring(5).trim();
                    result.add("CASE:" + lab);
                    if (right.equals("{")) result.add("BLOCK_START");
                    else if (!right.isEmpty()) result.add(right);
                } else if (left.equals("default") || left.startsWith("default ")) {
                    result.add("DEFAULT");
                    if (right.equals("{")) result.add("BLOCK_START");
                    else if (!right.isEmpty()) result.add(right);
                } else {
                    result.add(line);
                }
            } else if (line.startsWith("case ") || line.startsWith("case:")) {
                String rest = line.startsWith("case:") ? line.substring(5).trim() : line.substring(5).trim();
                int colon = rest.lastIndexOf(':');
                String lab = colon >= 0 ? rest.substring(0, colon).trim() : rest;
                result.add("CASE:" + lab);
                if (line.contains("{")) result.add("BLOCK_START");
            } else if (line.equals("default:") || line.equals("default") || line.startsWith("default:")) {
                if (line.startsWith("default:") && line.length() > 8) {
                    result.add("DEFAULT:" + line.substring(8).trim());
                } else {
                    result.add("DEFAULT");
                }
                if (line.contains("{")) result.add("BLOCK_START");
            } else if (line.contains(".forEach(")) {
                int fe = line.indexOf(".forEach(");
                int open = fe + ".forEach".length();
                int[] close = findClosingParen(lines, idx, open);
                String merged = mergeLinesForParen(lines, idx, fe + 1, close[0], close[1]);
                int fkw = merged.indexOf("forEach");
                String inner = fkw >= 0 ? extractParenContent(merged.substring(fkw), "forEach") : "";
                result.add("FOREACH:" + inner);
                if (hasBlockBraceAfter(lines, close[0], close[1])) {
                    result.add("BLOCK_START");
                }
                idx = close[0] + 1;
                continue;
            } else if (line.startsWith("for ") || line.startsWith("for(")) {
                int kw = lines[idx].indexOf("for");
                int open = lines[idx].indexOf('(', Math.max(0, kw));
                if (open < 0) {
                    result.add(line);
                } else {
                    int[] close = findClosingParen(lines, idx, open);
                    String merged = mergeLinesForParen(lines, idx, Math.max(0, kw), close[0], close[1]);
                    result.add("FOR:" + extractParenContent(merged, "for"));
                    if (hasBlockBraceAfter(lines, close[0], close[1])) {
                        result.add("BLOCK_START");
                    }
                    idx = close[0] + 1;
                    continue;
                }
            } else if (line.startsWith("while ") || line.startsWith("while(")) {
                int kw = lines[idx].indexOf("while");
                int open = lines[idx].indexOf('(', Math.max(0, kw));
                if (open < 0) {
                    result.add(line);
                } else {
                    int[] close = findClosingParen(lines, idx, open);
                    String merged = mergeLinesForParen(lines, idx, Math.max(0, kw), close[0], close[1]);
                    result.add("WHILE:" + extractParenContent(merged, "while"));
                    if (hasBlockBraceAfter(lines, close[0], close[1])) {
                        result.add("BLOCK_START");
                    }
                    idx = close[0] + 1;
                    continue;
                }
            } else if (line.startsWith("do ") || line.equals("do {") || line.equals("do{")) {
                result.add("DOWHILE:");
                if (line.contains("{")) {
                    result.add("BLOCK_START");
                } else {
                    int next = nextNonEmptyLine(lines, idx + 1);
                    if (next >= 0 && lines[next].trim().equals("{")) result.add("BLOCK_START");
                }
            } else if (line.startsWith("assert ") || line.startsWith("assert(")) {
                int cut = line.startsWith("assert(") ? 6 : 7;
                result.add("ASSERT:" + line.substring(cut).replace(";", "").trim());
            } else if (line.startsWith("yield ") || line.startsWith("yield(")) {
                int cut = line.startsWith("yield(") ? 5 : 6;
                result.add("YIELD:" + line.substring(cut).replace(";", "").trim());
            } else if (line.equals("break;") || line.equals("break")) {
                result.add("BREAK");
            } else if (line.startsWith("break ")) {
                result.add("BREAK:" + line.replaceFirst("(?i)break\\s+", "").replace(";", "").trim());
            } else if (line.equals("continue;") || line.equals("continue")) {
                result.add("CONTINUE");
            } else if (line.startsWith("continue ")) {
                result.add("CONTINUE:" + line.replaceFirst("(?i)continue\\s+", "").replace(";", "").trim());
            } else if (line.equals("{")) {
                result.add("BLOCK_START");
            } else {
                result.add(line);
            }
            idx++;
        }
        return result;
    }

    private static int nextNonEmptyLine(String[] lines, int start) {
        for (int i = start; i < lines.length; i++) {
            if (!lines[i].trim().isEmpty()) return i;
        }
        return -1;
    }

    private FlowNode parseSimpleStatement(String line) {
        if (line.startsWith("ASSERT:")) {
            return new FlowNode(FlowNode.Type.ASSERT_COND, "assert", line.substring("ASSERT:".length()).trim());
        } else if (line.startsWith("YIELD:")) {
            return new FlowNode(FlowNode.Type.YIELD_STMT, "yield", line.substring("YIELD:".length()).trim());
        } else if (line.equals("BREAK") || line.startsWith("BREAK:")) {
            String lab = line.equals("BREAK") ? "break" : "break " + line.substring("BREAK:".length()).trim();
            return new FlowNode(FlowNode.Type.BREAK, lab, "");
        } else if (line.equals("CONTINUE") || line.startsWith("CONTINUE:")) {
            String lab = line.equals("CONTINUE") ? "continue"
                    : "continue " + line.substring("CONTINUE:".length()).trim();
            return new FlowNode(FlowNode.Type.CONTINUE, lab, "");
        } else if (line.startsWith("return")) {
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
        Set<String> seen = new HashSet<>();

        Pattern dotted = Pattern.compile("(\\w+)\\.(\\w+)\\s*\\(");
        Matcher md = dotted.matcher(body);
        while (md.find()) {
            String target = md.group(1);
            String method = md.group(2);
            String key = target + "." + method;
            if (seen.contains(key) || IGNORED_TARGETS.contains(target)) continue;
            seen.add(key);
            calls.add(new MethodCall(ownerClass, ownerMethod, target, method));
        }

        Pattern sup = Pattern.compile("super\\.(\\w+)\\s*\\(");
        Matcher ms = sup.matcher(body);
        while (ms.find()) {
            String method = ms.group(1);
            String key = "super." + method;
            if (seen.contains(key)) continue;
            seen.add(key);
            calls.add(new MethodCall(ownerClass, ownerMethod, "super", method));
        }

        Pattern qStatic = Pattern.compile("\\b([A-Z]\\w*)\\.(\\w+)\\s*\\(");
        Matcher mq = qStatic.matcher(body);
        while (mq.find()) {
            String cls = mq.group(1);
            String method = mq.group(2);
            if (IGNORED_TARGETS.contains(cls)) continue;
            String key = cls + "." + method;
            if (seen.contains(key)) continue;
            seen.add(key);
            calls.add(new MethodCall(ownerClass, ownerMethod, cls, method));
        }

        Pattern unq = Pattern.compile("(?m)(?:^|;|\\{|\\}|\\))\\s*(\\w+)\\s*\\(");
        Matcher mu = unq.matcher(body);
        while (mu.find()) {
            String name = mu.group(1);
            if (UNQUALIFIED_CALL_SKIP.contains(name)) continue;
            String key = "." + name;
            if (seen.contains(key)) continue;
            seen.add(key);
            calls.add(new MethodCall(ownerClass, ownerMethod, "", name));
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
                    if (call.getResolvedTargetClass() == null && "this".equals(expr)) {
                        String tm = call.getTargetMethod();
                        if (c.getMethods().stream().anyMatch(mm -> mm.getName().equals(tm))) {
                            call.setResolvedTargetClass(c.getName());
                        }
                    }
                    if (call.getResolvedTargetClass() == null && "super".equals(expr)) {
                        if (!c.getSuperTypes().isEmpty()) {
                            call.setResolvedTargetClass(c.getSuperTypes().get(0));
                        } else {
                            call.setResolvedTargetClass(c.getName());
                        }
                    }
                    if (call.getResolvedTargetClass() == null && (expr == null || expr.isEmpty())) {
                        String tm = call.getTargetMethod();
                        if (c.getMethods().stream().anyMatch(mm -> mm.getName().equals(tm))) {
                            call.setResolvedTargetClass(c.getName());
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
