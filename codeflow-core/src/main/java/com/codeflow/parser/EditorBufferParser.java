package com.codeflow.parser;

import com.codeflow.model.CodeClass;

import java.util.ArrayList;
import java.util.List;

/**
 * Editör tamponundaki {@code // === dosya ===} ayraçlarına göre çok dosyalı parse.
 */
public final class EditorBufferParser {

    private static final String MARKER = "// === ";

    private final JavaSourceParser parser;

    public EditorBufferParser(JavaSourceParser parser) {
        this.parser = parser;
    }

    public List<CodeClass> parse(String code) {
        if (code == null || !code.contains(MARKER)) {
            return parser.parse(code != null ? code : "", null);
        }
        List<CodeClass> all = new ArrayList<>();
        int pos = 0;
        while (pos < code.length()) {
            int mark = code.indexOf(MARKER, pos);
            if (mark < 0) {
                String tail = code.substring(pos).trim();
                if (!tail.isEmpty()) {
                    all.addAll(parser.parseUnresolved(tail, null));
                }
                break;
            }
            String gap = code.substring(pos, mark).trim();
            if (!gap.isEmpty()) {
                all.addAll(parser.parseUnresolved(gap, null));
            }
            int lineEnd = code.indexOf('\n', mark);
            if (lineEnd < 0) {
                String rest = code.substring(mark + MARKER.length()).trim();
                if (rest.endsWith("===")) {
                    rest = rest.substring(0, rest.length() - 3).trim();
                }
                if (!rest.isEmpty()) {
                    all.addAll(parser.parseUnresolved(rest, null));
                }
                break;
            }
            String header = code.substring(mark + MARKER.length(), lineEnd).trim();
            if (header.endsWith("===")) {
                header = header.substring(0, header.length() - 3).trim();
            }
            int nextMark = code.indexOf(MARKER, lineEnd + 1);
            String segment = (nextMark < 0 ? code.substring(lineEnd + 1) : code.substring(lineEnd + 1, nextMark)).trim();
            if (!segment.isEmpty()) {
                all.addAll(parser.parseUnresolved(segment, header));
            }
            pos = nextMark >= 0 ? nextMark : code.length();
        }
        parser.resolveCrossFile(all);
        return all;
    }
}
