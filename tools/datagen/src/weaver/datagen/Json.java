package weaver.datagen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal, dependency-free JSON reader/writer (JDK only — no Gson, no Jackson).
 *
 * <p>Parsing is strict: a generated file that this parser cannot read is a file the game's
 * own codec is unlikely to read either, so {@code --verify} re-parses every emitted file with
 * this class as a first gate.
 *
 * <p>Writing is pretty-printed with 2-space indentation and a trailing newline, the same
 * layout vanilla's own data files use, and preserves insertion order (callers put keys in
 * vanilla's field order).
 */
public final class Json {

    private Json() {
    }

    /** Thrown when the input is not valid JSON. */
    public static final class ParseError extends RuntimeException {
        public ParseError(String message) {
            super(message);
        }
    }

    // ---------------------------------------------------------------- read

    public static Object parse(String text) {
        Parser p = new Parser(text);
        Object value = p.value();
        p.skipWs();
        if (!p.atEnd()) {
            throw p.error("trailing characters after the top-level value");
        }
        return value;
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        boolean atEnd() {
            return i >= s.length();
        }

        void skipWs() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    i++;
                } else {
                    break;
                }
            }
        }

        ParseError error(String message) {
            int line = 1;
            int col = 1;
            int limit = Math.min(i, s.length());
            for (int k = 0; k < limit; k++) {
                if (s.charAt(k) == '\n') {
                    line++;
                    col = 1;
                } else {
                    col++;
                }
            }
            return new ParseError(message + " at line " + line + ", column " + col);
        }

        Object value() {
            skipWs();
            if (atEnd()) {
                throw error("unexpected end of input");
            }
            char c = s.charAt(i);
            switch (c) {
                case '{':
                    return object();
                case '[':
                    return array();
                case '"':
                    return string();
                case 't':
                    literal("true");
                    return Boolean.TRUE;
                case 'f':
                    literal("false");
                    return Boolean.FALSE;
                case 'n':
                    literal("null");
                    return null;
                default:
                    return number();
            }
        }

        private void literal(String word) {
            if (!s.startsWith(word, i)) {
                throw error("expected '" + word + "'");
            }
            i += word.length();
        }

        private Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            i++; // '{'
            skipWs();
            if (!atEnd() && s.charAt(i) == '}') {
                i++;
                return map;
            }
            while (true) {
                skipWs();
                if (atEnd() || s.charAt(i) != '"') {
                    throw error("expected a quoted object key");
                }
                String key = string();
                skipWs();
                if (atEnd() || s.charAt(i) != ':') {
                    throw error("expected ':' after object key \"" + key + "\"");
                }
                i++;
                Object v = value();
                if (map.containsKey(key)) {
                    throw error("duplicate object key \"" + key + "\"");
                }
                map.put(key, v);
                skipWs();
                if (atEnd()) {
                    throw error("unexpected end of input inside an object");
                }
                char c = s.charAt(i);
                if (c == ',') {
                    i++;
                    continue;
                }
                if (c == '}') {
                    i++;
                    return map;
                }
                throw error("expected ',' or '}' in an object");
            }
        }

        private List<Object> array() {
            List<Object> list = new ArrayList<>();
            i++; // '['
            skipWs();
            if (!atEnd() && s.charAt(i) == ']') {
                i++;
                return list;
            }
            while (true) {
                list.add(value());
                skipWs();
                if (atEnd()) {
                    throw error("unexpected end of input inside an array");
                }
                char c = s.charAt(i);
                if (c == ',') {
                    i++;
                    continue;
                }
                if (c == ']') {
                    i++;
                    return list;
                }
                throw error("expected ',' or ']' in an array");
            }
        }

        private String string() {
            i++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw error("unterminated string");
                }
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (atEnd()) {
                        throw error("unterminated escape sequence");
                    }
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (i + 4 > s.length()) {
                                throw error("truncated \\u escape");
                            }
                            String hex = s.substring(i, i + 4);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException ex) {
                                throw error("bad \\u escape \\u" + hex);
                            }
                            i += 4;
                        }
                        default -> throw error("unknown escape \\" + e);
                    }
                    continue;
                }
                if (c < 0x20) {
                    throw error("raw control character in a string");
                }
                sb.append(c);
            }
        }

        private Object number() {
            int start = i;
            if (!atEnd() && (s.charAt(i) == '-' || s.charAt(i) == '+')) {
                i++;
            }
            boolean floating = false;
            while (!atEnd()) {
                char c = s.charAt(i);
                if (c >= '0' && c <= '9') {
                    i++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    floating = floating || c == '.' || c == 'e' || c == 'E';
                    i++;
                } else {
                    break;
                }
            }
            String token = s.substring(start, i);
            if (token.isEmpty() || token.equals("-")) {
                throw error("expected a JSON value");
            }
            try {
                return floating ? (Object) Double.valueOf(token) : (Object) Long.valueOf(token);
            } catch (NumberFormatException e) {
                throw error("bad number '" + token + "'");
            }
        }
    }

    // ---------------------------------------------------------------- write

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder(1024);
        writeValue(value, sb, 0);
        sb.append('\n');
        return sb.toString();
    }

    private static void indent(StringBuilder sb, int depth) {
        sb.append("  ".repeat(depth));
    }

    private static void writeValue(Object value, StringBuilder sb, int depth) {
        if (value == null) {
            sb.append("null");
            return;
        }
        if (value instanceof String str) {
            writeString(str, sb);
            return;
        }
        if (value instanceof Boolean b) {
            sb.append(b ? "true" : "false");
            return;
        }
        if (value instanceof Double || value instanceof Float) {
            sb.append(value);
            return;
        }
        if (value instanceof Number n) {
            sb.append(n.longValue());
            return;
        }
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append("{\n");
            int i = 0;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                indent(sb, depth + 1);
                writeString(String.valueOf(e.getKey()), sb);
                sb.append(": ");
                writeValue(e.getValue(), sb, depth + 1);
                if (++i < map.size()) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            indent(sb, depth);
            sb.append('}');
            return;
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append("[\n");
            for (int i = 0; i < list.size(); i++) {
                indent(sb, depth + 1);
                writeValue(list.get(i), sb, depth + 1);
                if (i + 1 < list.size()) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            indent(sb, depth);
            sb.append(']');
            return;
        }
        throw new IllegalArgumentException("not a JSON value: " + value.getClass().getName());
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // ---------------------------------------------------------------- helpers

    /** An insertion-ordered object; {@code kv} is a flat key/value list. */
    public static Map<String, Object> obj(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    public static List<Object> arr(Object... items) {
        List<Object> list = new ArrayList<>();
        for (Object item : items) {
            list.add(item);
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object value, String where) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new ParseError(where + ": expected an object, found " + typeName(value));
        }
        return (Map<String, Object>) map;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asArray(Object value, String where) {
        if (!(value instanceof List<?> list)) {
            throw new ParseError(where + ": expected an array, found " + typeName(value));
        }
        return (List<Object>) list;
    }

    public static String asString(Object value, String where) {
        if (!(value instanceof String s)) {
            throw new ParseError(where + ": expected a string, found " + typeName(value));
        }
        return s;
    }

    public static String typeName(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?>) {
            return "object";
        }
        if (value instanceof List<?>) {
            return "array";
        }
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof Number) {
            return "number";
        }
        return value.getClass().getSimpleName();
    }
}
