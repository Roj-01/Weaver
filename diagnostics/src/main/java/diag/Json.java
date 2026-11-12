package diag;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;

/** كاتب JSON صغير (بلا اعتماديات خارجية) — مُخرَج مقروء بمسافات بادئة. */
public final class Json {

    private final StringBuilder b = new StringBuilder();
    private final ArrayDeque<int[]> stack = new ArrayDeque<>(); // [isArray, itemCount]
    private boolean inKey = false;

    private void indent() {
        b.append('\n');
        for (int i = 0; i < stack.size(); i++) b.append("  ");
    }

    private void valueStart() {
        if (stack.isEmpty() || inKey) return;
        int[] top = stack.peek();
        if (top[0] == 1) { // عنصر مصفوفة: فاصلة + سطر جديد بإزاحة صحيحة
            if (top[1] > 0) b.append(',');
            top[1]++;
            indent();
        }
    }

    public Json obj() {
        valueStart();
        b.append('{');
        stack.push(new int[]{0, 0});
        inKey = false;
        return this;
    }

    public Json arr() {
        valueStart();
        b.append('[');
        stack.push(new int[]{1, 0});
        inKey = false;
        return this;
    }

    public Json end() {
        int[] top = stack.pop();
        if (top[1] > 0) indent();
        b.append(top[0] == 1 ? ']' : '}');
        inKey = false;
        return this;
    }

    public Json key(String k) {
        int[] top = stack.peek();
        if (top != null && top[0] == 0) {
            if (top[1] > 0) b.append(',');
            top[1]++;
        }
        indent();
        b.append(quote(k)).append(": ");
        inKey = true;
        return this;
    }

    public Json val(String s) {
        valueStart();
        b.append(s == null ? "null" : quote(s));
        inKey = false;
        return this;
    }

    public Json val(int v) {
        valueStart();
        b.append(v);
        inKey = false;
        return this;
    }

    public Json val(boolean v) {
        valueStart();
        b.append(v);
        inKey = false;
        return this;
    }

    public Json val(double v) {
        valueStart();
        b.append(BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
        inKey = false;
        return this;
    }

    public String done() {
        return b.toString();
    }

    static String quote(String s) {
        StringBuilder o = new StringBuilder(s.length() + 2);
        o.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> o.append("\\\"");
                case '\\' -> o.append("\\\\");
                case '\n' -> o.append("\\n");
                case '\r' -> o.append("\\r");
                case '\t' -> o.append("\\t");
                case '\b' -> o.append("\\b");
                case '\f' -> o.append("\\f");
                default -> {
                    if (c < 0x20) o.append(String.format("\\u%04x", (int) c));
                    else o.append(c);
                }
            }
        }
        o.append('"');
        return o.toString();
    }
}
