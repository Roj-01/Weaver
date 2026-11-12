package net.weaver.mixin.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Consumer;

/**
 * ASCII marker sink (CONTRACTS.md section 8). Every line is also mirrored to an optional file so
 * a harness or CI can assert on it without scraping mixed stdout.
 *
 * <p>Never throws: a broken sink must never break a class load or a game tick.
 */
public final class Marker {

    private static final Object LOCK = new Object();
    private static volatile Consumer<String> sink;
    private static volatile Path file;

    private Marker() {
    }

    /** Routes markers to a foreign sink (the kernel's own log) instead of stdout. */
    public static void forwardTo(Consumer<String> sink) {
        Marker.sink = sink;
    }

    /** Mirrors every marker as an appended line (UTF-8, created on demand). */
    public static void alsoWriteTo(Path path) {
        Marker.file = path;
    }

    public static void emit(String line) {
        String safe = line == null ? "" : line.replace('\r', ' ').replace('\n', ' ');
        Consumer<String> current = sink;
        if (current != null) {
            try {
                current.accept(safe);
            } catch (Throwable ignored) {
                // the foreign sink owns its own error handling
            }
        } else {
            try {
                System.out.println(safe);
                System.out.flush();
            } catch (Throwable ignored) {
                // a closed stdout must not break weaving
            }
        }
        Path target = file;
        if (target != null) {
            synchronized (LOCK) {
                try {
                    Path parent = target.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.write(target, (safe + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException ignored) {
                    // diagnostics only
                }
            }
        }
    }

    /** One-line, marker-safe description of a throwable (root cause first). */
    public static String oneLine(Throwable t) {
        if (t == null) {
            return "null";
        }
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return (root.getClass().getSimpleName() + (message == null ? "" : ": " + message))
                .replace('\r', ' ').replace('\n', ' ');
    }

    /** Marker-safe free text (no quotes, no newlines). */
    public static String text(Object value) {
        if (value == null) {
            return "null";
        }
        return String.valueOf(value).replace('"', '\'').replace('\r', ' ').replace('\n', ' ');
    }

    public static String ms(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }
}
