package net.weaver.core.config;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Crash-safe file replacement shared by the core JSON stores.
 *
 * <p>{@link #write(Path, byte[], Mover)} writes the content to a temporary
 * sibling through a {@link FileChannel}, forces it to stable storage, moves it
 * over the target (atomically when the file system supports it), and then
 * makes a best-effort attempt to force the parent directory so the rename
 * itself survives a power loss. Directory forcing is not supported on every
 * platform (for example Windows); such failures are ignored.</p>
 *
 * <p>{@link #backupPath(Path, String)} names timestamped sibling backups as
 * {@code <file>.<reason>-<yyyyMMdd-HHmmss-SSS>[-n].bak}.</p>
 */
public final class DurableFiles
{
    private static final DateTimeFormatter BACKUP_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private DurableFiles()
    {
    }

    /**
     * Moves a finished temporary file to its target, replacing the target.
     */
    @FunctionalInterface
    public interface Mover
    {
        /**
         * Moves {@code source} to {@code target}, replacing the target.
         *
         * @param source the source file
         * @param target the final file
         * @throws IOException when the move fails
         */
        void move(Path source, Path target) throws IOException;
    }

    /**
     * Durably replaces {@code target} with {@code content}.
     *
     * @param target  the file to replace, not {@code null}
     * @param content the complete new content, not {@code null}
     * @throws IOException when the content cannot be written or moved; the
     *                     previous target is then left untouched
     */
    public static void write(Path target, byte[] content) throws IOException
    {
        write(target, content, DurableFiles::moveReplacing);
    }

    /**
     * Durably replaces {@code target} with {@code content} using a custom
     * final move.
     *
     * @param target  the file to replace, not {@code null}
     * @param content the complete new content, not {@code null}
     * @param mover   the final move, not {@code null}
     * @throws IOException when the content cannot be written or moved; the
     *                     previous target is then left untouched and the
     *                     temporary file is removed
     */
    public static void write(Path target, byte[] content, Mover mover) throws IOException
    {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(mover, "mover");
        Path directory = target.toAbsolutePath().getParent();
        if (directory == null)
        {
            throw new IOException("file has no parent directory: " + target);
        }
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, temporaryPrefix(target), ".tmp");
        try
        {
            try (FileChannel channel = FileChannel.open(temporary,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING))
            {
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining())
                {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            mover.move(temporary, target);
            temporary = null;
        }
        finally
        {
            deleteQuietly(temporary);
        }
        forceDirectory(directory);
    }

    /**
     * Moves a file over a target, atomically when supported.
     *
     * @param source the source file
     * @param target the target file, replaced when it exists
     * @throws IOException when the move fails
     */
    public static void moveReplacing(Path source, Path target) throws IOException
    {
        // On Windows a sync client, antivirus, or indexer can hold the target for a moment;
        // a short retry turns that into a delay instead of a failed save.
        for (int attempt = 0; ; attempt++)
        {
            try
            {
                moveOnce(source, target);
                return;
            }
            catch (java.nio.file.FileSystemException busy)
            {
                if (busy instanceof java.nio.file.NoSuchFileException || attempt >= 5)
                {
                    throw busy;
                }
                try
                {
                    Thread.sleep(20L << attempt);
                }
                catch (InterruptedException interrupted)
                {
                    Thread.currentThread().interrupt();
                    throw busy;
                }
            }
        }
    }

    private static void moveOnce(Path source, Path target) throws IOException
    {
        try
        {
            Files.move(source, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (AtomicMoveNotSupportedException failure)
        {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Returns an unused timestamped sibling backup path for {@code file}.
     *
     * <p>The reason is reduced to {@code [A-Za-z0-9_-]} characters; a missing
     * or empty reason becomes {@code backup}. A counter suffix keeps the name
     * unique when the same timestamp already exists.</p>
     *
     * @param file   the file being backed up, not {@code null}
     * @param reason a short label, or {@code null}
     * @return a path that did not exist when this method checked
     */
    public static Path backupPath(Path file, String reason)
    {
        Objects.requireNonNull(file, "file");
        String base = fileNameText(file) + "." + sanitizeReason(reason) + "-"
                + BACKUP_TIMESTAMP.format(LocalDateTime.now());
        Path backup = file.resolveSibling(base + ".bak");
        int counter = 1;
        while (Files.exists(backup))
        {
            backup = file.resolveSibling(base + "-" + counter + ".bak");
            counter++;
        }
        return backup;
    }

    /**
     * Forces a directory's metadata to stable storage, ignoring every failure.
     *
     * @param directory the directory, or {@code null}
     */
    public static void forceDirectory(Path directory)
    {
        if (directory == null)
        {
            return;
        }
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ))
        {
            channel.force(true);
        }
        catch (IOException | RuntimeException unsupported)
        {
            // Not supported on every platform (Windows refuses to open directories).
        }
    }

    static String sanitizeReason(String reason)
    {
        if (reason == null)
        {
            return "backup";
        }
        String sanitized = reason.replaceAll("[^A-Za-z0-9_-]+", "-")
                .replaceAll("^-+|-+$", "");
        return sanitized.isEmpty() ? "backup" : sanitized;
    }

    private static String fileNameText(Path file)
    {
        Path name = file.getFileName();
        return name == null ? "file" : name.toString();
    }

    private static String temporaryPrefix(Path target)
    {
        String prefix = fileNameText(target) + ".";
        return prefix.length() >= 3 ? prefix : prefix + "tmp";
    }

    private static void deleteQuietly(Path path)
    {
        if (path == null)
        {
            return;
        }
        try
        {
            Files.deleteIfExists(path);
        }
        catch (IOException ignored)
        {
        }
    }
}
