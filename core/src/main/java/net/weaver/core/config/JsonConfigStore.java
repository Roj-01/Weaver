package net.weaver.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.weaver.api.config.ConfigDocument;
import net.weaver.api.config.ConfigStore;
import net.weaver.api.config.ConfigStoreException;
import net.weaver.api.event.Side;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * A UTF-8 JSON {@link ConfigStore} backed by one file per side.
 *
 * <p>The file is a pretty-printed object with a numeric {@code schemaVersion}
 * and a flat {@code values} object. Unknown keys and nested objects or arrays
 * are preserved so the configuration manager can round-trip values it does not
 * understand. Writes go to a temporary file in the same directory that is
 * forced to stable storage before it replaces the target with an atomic move
 * when the file system supports one (see {@link DurableFiles}). Content that
 * is not valid UTF-8 is reported as corrupt.</p>
 *
 * <p>Operations on one instance are serialized. Concurrent access from
 * multiple processes is not supported; use one process per configuration
 * file.</p>
 */
public final class JsonConfigStore implements ConfigStore
{
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    private final Path file;
    private final Side side;
    private final FileMover mover;
    private final Object lock = new Object();

    /**
     * Creates a store for one configuration file.
     *
     * @param file the target file, never {@code null}
     * @param side the side this store represents, never {@code null}
     * @throws NullPointerException when {@code file} or {@code side} is
     *                              {@code null}
     */
    public JsonConfigStore(Path file, Side side)
    {
        this(file, side, JsonConfigStore::moveReplacing);
    }

    JsonConfigStore(Path file, Side side, FileMover mover)
    {
        this.file = Objects.requireNonNull(file, "file");
        this.side = Objects.requireNonNull(side, "side");
        this.mover = Objects.requireNonNull(mover, "mover");
    }

    /**
     * Returns the side this store represents.
     *
     * @return the configured side
     */
    @Override
    public Side side()
    {
        return side;
    }

    /**
     * Returns the path of the backing configuration file.
     *
     * @return the configuration file path
     */
    @Override
    public Path location()
    {
        return file;
    }

    /**
     * Returns the backing file's last-modified time as a change token.
     *
     * <p>The value uses the finest resolution reported by the file system and
     * is intended only for equality comparison by the configuration watcher.
     * A missing file, an unsupported attribute, or any I/O failure returns
     * {@link Long#MIN_VALUE}; this method never throws.
     *
     * @return the last-modified token, or {@link Long#MIN_VALUE} when unknown
     */
    @Override
    public long lastModified()
    {
        synchronized (lock)
        {
            try
            {
                return Files.getLastModifiedTime(file).to(TimeUnit.NANOSECONDS);
            }
            catch (IOException | RuntimeException failure)
            {
                return Long.MIN_VALUE;
            }
        }
    }

    /**
     * Reads and parses the backing file.
     *
     * <p>A missing file is not an error and returns {@code null}. Nested
     * objects and arrays become unmodifiable maps and lists, integral numbers
     * become {@link Long} values when possible, and other numbers become
     * {@link Double} values.</p>
     *
     * @return the parsed document, or {@code null} when the file does not exist
     * @throws ConfigStoreException when the file cannot be read or is corrupt
     */
    @Override
    public ConfigDocument read() throws ConfigStoreException
    {
        synchronized (lock)
        {
            if (Files.notExists(file))
            {
                return null;
            }
            String json;
            try
            {
                json = Files.readString(file, StandardCharsets.UTF_8);
            }
            catch (CharacterCodingException failure)
            {
                throw ConfigStoreException.corrupt(
                        "Corrupt config " + file + ": not valid UTF-8", failure);
            }
            catch (IOException failure)
            {
                throw ConfigStoreException.io("cannot read config " + file, failure);
            }
            JsonElement parsed;
            try
            {
                parsed = JsonParser.parseString(json);
            }
            catch (RuntimeException failure)
            {
                throw ConfigStoreException.corrupt(
                        "Corrupt config " + file + ": " + reason(failure), failure);
            }
            if (parsed == null || !parsed.isJsonObject())
            {
                throw ConfigStoreException.corrupt(
                        "Corrupt config " + file + ": root must be a JSON object");
            }
            JsonObject root = parsed.getAsJsonObject();
            int schemaVersion = readSchemaVersion(root);
            JsonElement valuesElement = root.get("values");
            if (valuesElement == null || !valuesElement.isJsonObject())
            {
                throw ConfigStoreException.corrupt(
                        "Corrupt config " + file + ": missing or non-object \"values\"");
            }
            Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : valuesElement.getAsJsonObject().entrySet())
            {
                values.put(entry.getKey(), toJava(entry.getValue()));
            }
            return new ConfigDocument(schemaVersion, Collections.unmodifiableMap(values));
        }
    }

    /**
     * Writes a document atomically over the backing file.
     *
     * <p>Parent directories are created when needed. The previous file is
     * replaced only when the final move succeeds.</p>
     *
     * @param document the document to write, never {@code null}
     * @throws NullPointerException when {@code document} is {@code null}
     * @throws ConfigStoreException when the document cannot be serialized or
     *                              the file cannot be written
     */
    @Override
    public void write(ConfigDocument document) throws ConfigStoreException
    {
        Objects.requireNonNull(document, "document");
        synchronized (lock)
        {
            String json;
            try
            {
                JsonObject root = new JsonObject();
                root.addProperty("schemaVersion", document.schemaVersion());
                JsonObject values = new JsonObject();
                for (Map.Entry<String, Object> entry : document.values().entrySet())
                {
                    values.add(entry.getKey(), GSON.toJsonTree(entry.getValue()));
                }
                root.add("values", values);
                json = GSON.toJson(root) + "\n";
            }
            catch (RuntimeException failure)
            {
                throw ConfigStoreException.io("cannot write config " + file, failure);
            }
            try
            {
                DurableFiles.write(file, json.getBytes(StandardCharsets.UTF_8), mover::move);
            }
            catch (IOException | RuntimeException failure)
            {
                throw ConfigStoreException.io("cannot write config " + file, failure);
            }
        }
    }

    /**
     * Moves the backing file to a timestamped sibling backup.
     *
     * <p>The reason is reduced to {@code [A-Za-z0-9_-]} characters and used in
     * the backup name; a missing or empty reason becomes {@code backup}. A
     * counter suffix keeps the name unique when the same timestamp already
     * exists.</p>
     *
     * @param reason a short label for the backup, or {@code null}
     * @return the backup path, or {@code null} when the file does not exist
     * @throws ConfigStoreException when the file cannot be moved
     */
    @Override
    public Path backup(String reason) throws ConfigStoreException
    {
        synchronized (lock)
        {
            if (Files.notExists(file))
            {
                return null;
            }
            Path backup = DurableFiles.backupPath(file, reason);
            try
            {
                mover.move(file, backup);
            }
            catch (IOException | RuntimeException failure)
            {
                throw ConfigStoreException.io(
                        "cannot back up config " + file + " to " + backup, failure);
            }
            return backup;
        }
    }

    private int readSchemaVersion(JsonObject root)
    {
        JsonElement element = root.get("schemaVersion");
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
        {
            throw ConfigStoreException.corrupt(
                    "Corrupt config " + file + ": missing or non-number \"schemaVersion\"");
        }
        try
        {
            int version = element.getAsBigDecimal().intValueExact();
            if (version < 1)
            {
                throw ConfigStoreException.corrupt(
                        "Corrupt config " + file + ": \"schemaVersion\" must be at least 1");
            }
            return version;
        }
        catch (ArithmeticException | NumberFormatException failure)
        {
            throw ConfigStoreException.corrupt(
                    "Corrupt config " + file + ": \"schemaVersion\" must be an integer", failure);
        }
    }

    private Object toJava(JsonElement element)
    {
        if (element == null || element.isJsonNull())
        {
            return null;
        }
        if (element.isJsonPrimitive())
        {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean())
            {
                return primitive.getAsBoolean();
            }
            if (primitive.isString())
            {
                return primitive.getAsString();
            }
            return numberToJava(primitive);
        }
        if (element.isJsonArray())
        {
            List<Object> list = new ArrayList<>();
            for (JsonElement child : element.getAsJsonArray())
            {
                list.add(toJava(child));
            }
            return Collections.unmodifiableList(list);
        }
        if (element.isJsonObject())
        {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet())
            {
                map.put(entry.getKey(), toJava(entry.getValue()));
            }
            return Collections.unmodifiableMap(map);
        }
        throw ConfigStoreException.corrupt(
                "Corrupt config " + file + ": unsupported JSON value");
    }

    private Object numberToJava(JsonPrimitive primitive)
    {
        String literal = primitive.getAsString();
        if (literal.indexOf('.') < 0 && literal.indexOf('e') < 0 && literal.indexOf('E') < 0)
        {
            try
            {
                return Long.parseLong(literal);
            }
            catch (NumberFormatException outOfLongRange)
            {
            }
        }
        try
        {
            return Double.parseDouble(literal);
        }
        catch (NumberFormatException failure)
        {
            throw ConfigStoreException.corrupt(
                    "Corrupt config " + file + ": invalid number " + literal, failure);
        }
    }

    private static String reason(RuntimeException failure)
    {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static void moveReplacing(Path source, Path target) throws IOException
    {
        DurableFiles.moveReplacing(source, target);
    }

    /** Moves a finished temporary file to its target; replaced in tests. */
    @FunctionalInterface
    interface FileMover
    {
        /**
         * Moves {@code source} to {@code target}, replacing the target.
         *
         * @param source the temporary file
         * @param target the final file
         * @throws IOException when the move fails
         */
        void move(Path source, Path target) throws IOException;
    }
}
