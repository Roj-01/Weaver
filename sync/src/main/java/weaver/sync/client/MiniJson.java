package weaver.sync.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader (objects, arrays, strings with escapes, numbers, booleans, null).
 *
 * <p>No external dependency on purpose: docs/CONVENTIONS.md §8 asks us to use the platform before
 * pulling a library, and we only need to read one {@code version.json}.
 */
final class MiniJson {

    private MiniJson() {
    }

    static Object parse(String text) {
        Parser parser = new Parser(text);
        parser.skipWhitespace();
        Object value = parser.value();
        parser.skipWhitespace();
        if (!parser.eof()) {
            throw new IllegalArgumentException("unexpected trailing JSON at offset " + parser.position);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asObject(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    @SuppressWarnings("unchecked")
    static List<Object> asArray(Object value) {
        return value instanceof List ? (List<Object>) value : null;
    }

    static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    static boolean asBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static final class Parser {
        private final String text;
        private int position;

        Parser(String text) {
            this.text = text;
        }

        boolean eof() {
            return position >= text.length();
        }

        void skipWhitespace() {
            while (!eof() && Character.isWhitespace(text.charAt(position))) {
                position++;
            }
        }

        Object value() {
            if (eof()) {
                throw error("unexpected end of JSON");
            }
            char c = text.charAt(position);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{');
            skipWhitespace();
            if (peek() == '}') {
                position++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = string();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                map.put(key, value());
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    position++;
                    continue;
                }
                expect('}');
                return map;
            }
        }

        private List<Object> array() {
            List<Object> list = new ArrayList<>();
            expect('[');
            skipWhitespace();
            if (peek() == ']') {
                position++;
                return list;
            }
            while (true) {
                skipWhitespace();
                list.add(value());
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    position++;
                    continue;
                }
                expect(']');
                return list;
            }
        }

        private String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (eof()) {
                    throw error("unterminated string");
                }
                char c = text.charAt(position++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char escape = text.charAt(position++);
                switch (escape) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        sb.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                        position += 4;
                    }
                    default -> throw error("bad escape \\" + escape);
                }
            }
        }

        private Object number() {
            int start = position;
            while (!eof() && "+-0123456789.eE".indexOf(text.charAt(position)) >= 0) {
                position++;
            }
            String raw = text.substring(start, position);
            if (raw.isEmpty()) {
                throw error("expected a value");
            }
            return Double.valueOf(raw);
        }

        private Object literal(String token, Object value) {
            if (!text.startsWith(token, position)) {
                throw error("expected '" + token + "'");
            }
            position += token.length();
            return value;
        }

        private char peek() {
            if (eof()) {
                throw error("unexpected end of JSON");
            }
            return text.charAt(position);
        }

        private void expect(char expected) {
            if (eof() || text.charAt(position) != expected) {
                throw error("expected '" + expected + "'");
            }
            position++;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " (offset " + position + ")");
        }
    }
}
