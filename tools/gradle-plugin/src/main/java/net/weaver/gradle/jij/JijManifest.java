package net.weaver.gradle.jij;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Reads and writes {@code META-INF/weaver/jij.json}.
 *
 * <p>The emitted manifest is deterministic and pretty-printed:
 *
 * <pre>{@code
 * {
 *   "schemaVersion": 1,
 *   "firstWins": true,
 *   "jars": [
 *     {
 *       "file": "META-INF/weaver/jars/lib-1.0.0.jar",
 *       "id": "lib",
 *       "version": "1.0.0",
 *       "optional": false
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <p>The parser is intentionally dependency-free so the Gradle plugin only
 * uses the Gradle API.
 */
public final class JijManifest {

    private JijManifest() {
    }

    /**
     * Parses a jar-in-jar manifest.
     *
     * @param json the manifest bytes, UTF-8 encoded
     * @return the nested jars in manifest order
     * @throws IllegalArgumentException if the JSON is malformed, the schema
     *         version is unsupported, an entry is incomplete, or an entry name
     *         is unsafe or outside {@link JijLayout#ROOT}
     */
    public static List<JijJar> parse(byte[] json) {
        if (json == null) {
            throw new IllegalArgumentException("jij manifest is missing");
        }
        String text = new String(json, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        Object rootValue = new Parser(text).parse();
        if (!(rootValue instanceof Map<?, ?> root)) {
            throw new IllegalArgumentException("jij manifest must be a JSON object");
        }
        Object schemaValue = root.get("schemaVersion");
        if (schemaValue != null) {
            if (!(schemaValue instanceof Number number)
                    || number.doubleValue() != JijLayout.SCHEMA_VERSION) {
                throw new IllegalArgumentException("unsupported jij schemaVersion: " + schemaValue);
            }
        }
        Object firstWins = root.get("firstWins");
        if (firstWins != null && !(firstWins instanceof Boolean)) {
            throw new IllegalArgumentException("jij firstWins must be a boolean");
        }
        Object jarsValue = root.get("jars");
        if (!(jarsValue instanceof List<?> jars)) {
            throw new IllegalArgumentException("jij manifest must contain a jars array");
        }
        List<JijJar> nested = new ArrayList<>(jars.size());
        for (int index = 0; index < jars.size(); index++) {
            String where = "jij jar[" + index + "]";
            if (!(jars.get(index) instanceof Map<?, ?> jar)) {
                throw new IllegalArgumentException(where + " must be a JSON object");
            }
            String file = string(jar, "file", where);
            String id = string(jar, "id", where);
            String version = string(jar, "version", where);
            if (!id.matches("[a-z0-9_-]+")) {
                throw new IllegalArgumentException(where + " has an invalid id: " + id);
            }
            Object optionalValue = jar.get("optional");
            boolean optional;
            if (optionalValue == null) {
                optional = false;
            } else if (optionalValue instanceof Boolean flag) {
                optional = flag;
            } else {
                throw new IllegalArgumentException(where + " must have a boolean optional");
            }
            JijLayout.validateEntryName(file);
            if (!file.startsWith(JijLayout.ROOT)) {
                throw new IllegalArgumentException(where + " is outside " + JijLayout.ROOT + ": " + file);
            }
            nested.add(new JijJar(file, id, version, optional));
        }
        return List.copyOf(nested);
    }

    /**
     * Writes a jar-in-jar manifest. The list order is preserved so callers can
     * sort entries before writing.
     *
     * @param jars the nested jars to declare
     * @return deterministic, pretty-printed JSON
     * @throws NullPointerException if {@code jars} is {@code null}
     */
    public static String write(List<JijJar> jars) {
        Objects.requireNonNull(jars, "jars");
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"schemaVersion\": ").append(JijLayout.SCHEMA_VERSION).append(",\n");
        json.append("  \"firstWins\": true,\n");
        json.append("  \"jars\": [");
        if (!jars.isEmpty()) {
            for (int index = 0; index < jars.size(); index++) {
                JijJar jar = jars.get(index);
                if (index > 0) {
                    json.append(",");
                }
                json.append("\n    {\n");
                json.append("      \"file\": ").append(quote(jar.entryName())).append(",\n");
                json.append("      \"id\": ").append(quote(jar.id())).append(",\n");
                json.append("      \"version\": ").append(quote(jar.version())).append(",\n");
                json.append("      \"optional\": ").append(jar.optional()).append("\n");
                json.append("    }");
            }
            json.append("\n  ");
        }
        json.append("]\n");
        json.append("}\n");
        return json.toString();
    }

    private static String string(Map<?, ?> object, String key, String where) {
        Object value = object.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(where + " must have a non-blank string " + key);
        }
        return text;
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
        return out.toString();
    }

    /** Minimal JSON parser: objects, arrays, strings, numbers, booleans, and null. */
    private static final class Parser {

        private final String text;
        private int index;

        private Parser(String text) {
            this.text = text;
        }

        private Object parse() {
            Object value = value();
            skipWhitespace();
            if (index != text.length()) {
                throw error("trailing content");
            }
            return value;
        }

        private Object value() {
            skipWhitespace();
            if (index >= text.length()) {
                throw error("unexpected end of input");
            }
            char c = text.charAt(index);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield number();
                    }
                    throw error("unexpected character '" + c + "'");
                }
            };
        }

        private Map<String, Object> object() {
            expect('{');
            skipWhitespace();
            Map<String, Object> object = new LinkedHashMap<>();
            if (peek('}')) {
                index++;
                return object;
            }
            while (true) {
                skipWhitespace();
                if (!peek('"')) {
                    throw error("expected an object key");
                }
                String key = string();
                skipWhitespace();
                expect(':');
                Object value = value();
                if (object.containsKey(key)) {
                    throw error("duplicate object key '" + key + "'");
                }
                object.put(key, value);
                skipWhitespace();
                if (peek('}')) {
                    index++;
                    return object;
                }
                expect(',');
            }
        }

        private List<Object> array() {
            expect('[');
            skipWhitespace();
            List<Object> array = new ArrayList<>();
            if (peek(']')) {
                index++;
                return array;
            }
            while (true) {
                array.add(value());
                skipWhitespace();
                if (peek(']')) {
                    index++;
                    return array;
                }
                expect(',');
            }
        }

        private String string() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (index < text.length()) {
                char c = text.charAt(index++);
                if (c == '"') {
                    return out.toString();
                }
                if (c == '\\') {
                    if (index >= text.length()) {
                        throw error("unterminated escape");
                    }
                    char escaped = text.charAt(index++);
                    switch (escaped) {
                        case '"' -> out.append('"');
                        case '\\' -> out.append('\\');
                        case '/' -> out.append('/');
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case 'u' -> {
                            if (index + 4 > text.length()) {
                                throw error("truncated unicode escape");
                            }
                            String hex = text.substring(index, index + 4);
                            try {
                                out.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException e) {
                                throw error("invalid unicode escape \\u" + hex);
                            }
                            index += 4;
                        }
                        default -> throw error("invalid escape \\" + escaped);
                    }
                    continue;
                }
                if (c < 0x20) {
                    throw error("control character in string");
                }
                out.append(c);
            }
            throw error("unterminated string");
        }

        private Object number() {
            int start = index;
            if (peek('-')) {
                index++;
            }
            while (index < text.length() && Character.isDigit(text.charAt(index))) {
                index++;
            }
            boolean fractional = false;
            if (index < text.length() && text.charAt(index) == '.') {
                fractional = true;
                index++;
                while (index < text.length() && Character.isDigit(text.charAt(index))) {
                    index++;
                }
            }
            if (index < text.length() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
                fractional = true;
                index++;
                if (index < text.length() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
                    index++;
                }
                while (index < text.length() && Character.isDigit(text.charAt(index))) {
                    index++;
                }
            }
            String number = text.substring(start, index);
            try {
                return fractional ? (Object) Double.valueOf(number) : (Object) Long.valueOf(number);
            } catch (NumberFormatException e) {
                throw error("invalid number '" + number + "'");
            }
        }

        private Object literal(String literal, Object value) {
            if (!text.startsWith(literal, index)) {
                throw error("expected '" + literal + "'");
            }
            index += literal.length();
            return value;
        }

        private void expect(char expected) {
            skipWhitespace();
            if (index >= text.length() || text.charAt(index) != expected) {
                throw error("expected '" + expected + "'");
            }
            index++;
        }

        private boolean peek(char expected) {
            return index < text.length() && text.charAt(index) == expected;
        }

        private void skipWhitespace() {
            while (index < text.length()) {
                char c = text.charAt(index);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    index++;
                } else {
                    return;
                }
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException("jij manifest JSON error at " + index + ": " + message);
        }
    }
}
