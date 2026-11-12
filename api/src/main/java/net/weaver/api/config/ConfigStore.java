package net.weaver.api.config;

import net.weaver.api.event.Side;

import java.nio.file.Path;

/**
 * Persistence backend for one mod's configuration file.
 *
 * <p>The manager reads on the initial load and on every explicit
 * {@link ConfigManager#reload()}, and writes only when
 * {@link ConfigManager#save()} is called. Implementations never need to
 * support concurrent readers and writers from other processes. They must be
 * safe to use from the manager's load/reload/save usage: a failed write must
 * leave the previous file intact, and a read that cannot parse the content
 * must report {@link ConfigStoreException#corrupt() corrupt} content rather
 * than throwing an unrelated exception type.
 *
 * @see ConfigManager
 */
public interface ConfigStore
{
    /**
     * The side this store belongs to.
     *
     * <p>{@link ConfigManager#open} requires exact equality with the spec side,
     * including {@link Side#BOTH}.
     *
     * @return the store side, never {@code null}
     */
    Side side();

    /**
     * The file this store reads and writes.
     *
     * @return the file location, never {@code null}
     */
    Path location();

    /**
     * Reads the current document.
     *
     * @return the parsed document, or {@code null} when the file does not exist
     * @throws ConfigStoreException when the file cannot be read, or with
     *                              {@code corrupt=true} when its content cannot
     *                              be parsed
     */
    ConfigDocument read() throws ConfigStoreException;

    /**
     * Writes the document atomically.
     *
     * <p>Implementations must not damage a previous file: write to a temporary
     * sibling and move it into place, or use an equivalent atomic strategy.
     * When this method throws, the previously stored file must remain intact
     * and readable.
     *
     * @param document the document to write, not {@code null}
     * @throws ConfigStoreException when the document cannot be written
     */
    void write(ConfigDocument document) throws ConfigStoreException;

    /**
     * Creates a backup of the current file.
     *
     * @param reason a short reason such as {@code "corrupt"}, not {@code null}
     * @return the backup path, or {@code null} when there is no file to back
     *         up
     * @throws ConfigStoreException when the backup cannot be created
     */
    Path backup(String reason) throws ConfigStoreException;

    /**
     * Returns a change token for the backing file.
     *
     * <p>The token is compared only for equality across time; it is not a
     * wall-clock timestamp. Implementations that support change detection
     * should return {@link java.nio.file.Files#getLastModifiedTime(Path) the
     * file's last-modified time}, using the finest resolution the file system
     * provides. The default implementation returns {@link Long#MIN_VALUE},
     * which means "no change information is available"; a watcher must then
     * fall back to an explicit {@link ConfigManager#reload()} call.
     *
     * <p>Implementations must be fail-soft: a missing file or an attribute
     * read failure returns {@link Long#MIN_VALUE} and never throws.
     *
     * @return the file's last-modified token, or {@link Long#MIN_VALUE} when
     *         the file is missing or the change information is unavailable
     */
    default long lastModified()
    {
        return Long.MIN_VALUE;
    }
}
