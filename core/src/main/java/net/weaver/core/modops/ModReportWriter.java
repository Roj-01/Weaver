package net.weaver.core.modops;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Writes a {@link ModList} to disk in JSON or text form.
 *
 * <p>Parent directories are created when needed. Files are written as UTF-8
 * with a trailing platform line separator.
 */
public final class ModReportWriter {

    private ModReportWriter() {
    }

    /**
     * Writes a report.
     *
     * @param list report to write
     * @param path destination file
     * @param json {@code true} for JSON, {@code false} for text lines
     * @throws IOException          if the file cannot be written
     * @throws NullPointerException if {@code list} or {@code path} is {@code null}
     */
    public static void write(ModList list, Path path, boolean json) throws IOException {
        Objects.requireNonNull(list, "list");
        Objects.requireNonNull(path, "path");
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String body = json
                ? list.toJson()
                : String.join(System.lineSeparator(), list.toTextLines());
        Files.writeString(path, body + System.lineSeparator(), StandardCharsets.UTF_8);
    }
}
