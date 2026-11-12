package net.weaver.core.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.weaver.api.command.PermissionStore;
import net.weaver.core.LifecycleHooks;
import net.weaver.core.config.DurableFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * A UTF-8 JSON {@link PermissionStore} backed by one file.
 *
 * <p>The file contains a numeric {@code schemaVersion} and a {@code subjects}
 * object. Each subject key maps to an object of permission node text to a
 * boolean decision. Subject and node keys are written in sorted order so the
 * file is deterministic.
 *
 * <p>Writes go to a temporary file in the same directory that is forced to
 * stable storage and then replaces the target with an atomic move when the
 * file system supports one. A missing file loads as an empty snapshot and
 * reports one {@code [weaver] PERMISSION STORE EMPTY} line. Any other load
 * failure (malformed JSON, invalid UTF-8, an I/O error, an empty file) also
 * loads as an empty snapshot, but the unreadable file is first moved aside to
 * {@code <file>.corrupt-<timestamp>.bak} (copied when it cannot be moved) and
 * a {@code [weaver] PERMISSION STORE LOAD FAILED ... backup=<path>} line is
 * reported, so later saves cannot destroy the stored decisions. When the file
 * can be neither moved nor copied, saves through this instance are refused.
 *
 * <p>Operations on one instance are serialized. Concurrent access from
 * multiple processes is not supported; use one process per permission file.
 */
public final class JsonPermissionStore implements PermissionStore
{
    private static final int SCHEMA_VERSION = 1;
    private static final String CORRUPT_BACKUP_REASON = "corrupt";
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final Path file;
    private final Consumer<String> log;
    private final AtomicBoolean missingReported = new AtomicBoolean();
    private final AtomicBoolean loadFailureReported = new AtomicBoolean();
    private final Object lock = new Object();
    private volatile String saveBlockedReason;

    /**
     * Creates a store for one permission file.
     *
     * @param file the target file, not {@code null}
     * @param log  the log sink, or {@code null} for the loader log
     * @throws NullPointerException when {@code file} is {@code null}
     */
    public JsonPermissionStore(Path file, Consumer<String> log)
    {
        this.file = Objects.requireNonNull(file, "file");
        this.log = log != null ? log : JsonPermissionStore::loaderLog;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Map<String, Boolean>> load()
    {
        synchronized (lock)
        {
            if (Files.notExists(file))
            {
                if (missingReported.compareAndSet(false, true))
                {
                    log.accept("[weaver] PERMISSION STORE EMPTY file=" + file
                            + " reason=missing");
                }
                return Map.of();
            }
            try
            {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                JsonElement parsed = JsonParser.parseString(json);
                if (parsed == null || !parsed.isJsonObject())
                {
                    throw new IllegalStateException("root must be a JSON object");
                }
                return readSubjects(parsed.getAsJsonObject());
            }
            catch (NoSuchFileException missing)
            {
                if (missingReported.compareAndSet(false, true))
                {
                    log.accept("[weaver] PERMISSION STORE EMPTY file=" + file
                            + " reason=missing");
                }
                return Map.of();
            }
            catch (Throwable failure)
            {
                Path backup = preserveUnreadable();
                if (loadFailureReported.compareAndSet(false, true) || backup != null)
                {
                    log.accept("[weaver] PERMISSION STORE LOAD FAILED file=" + file
                            + " backup=" + (backup == null ? "none" : backup)
                            + (saveBlockedReason == null ? "" : " saves=blocked")
                            + " reason=" + reason(failure));
                }
                return Map.of();
            }
        }
    }

    /**
     * Moves an unreadable file aside so the next save cannot overwrite it.
     *
     * <p>The file is moved to {@code <file>.corrupt-<timestamp>.bak}. When it
     * cannot be moved it is copied instead; when it cannot be copied either,
     * saves through this instance are refused so the original survives.
     *
     * @return the backup path, or {@code null} when nothing was preserved
     */
    private Path preserveUnreadable()
    {
        Path backup = DurableFiles.backupPath(file, CORRUPT_BACKUP_REASON);
        try
        {
            Files.move(file, backup);
            missingReported.set(true);
            return backup;
        }
        catch (NoSuchFileException gone)
        {
            return null;
        }
        catch (IOException | RuntimeException moveFailure)
        {
            try
            {
                Files.copy(file, backup);
                return backup;
            }
            catch (IOException | RuntimeException copyFailure)
            {
                saveBlockedReason = "unreadable permission store " + file
                        + " could not be backed up (" + reason(copyFailure)
                        + "); refusing to overwrite it";
                return null;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void save(Map<String, Map<String, Boolean>> decisions)
    {
        Objects.requireNonNull(decisions, "decisions");
        synchronized (lock)
        {
            if (saveBlockedReason != null)
            {
                throw new IllegalStateException(saveBlockedReason);
            }
            String json = GSON.toJson(writeSubjects(decisions)) + "\n";
            try
            {
                DurableFiles.write(file, json.getBytes(StandardCharsets.UTF_8));
            }
            catch (IOException | RuntimeException failure)
            {
                throw new IllegalStateException(
                        "Cannot write permission store " + file + ": " + reason(failure), failure);
            }
        }
    }

    private Map<String, Map<String, Boolean>> readSubjects(JsonObject root)
    {
        JsonElement subjectsElement = root.get("subjects");
        if (subjectsElement == null || subjectsElement.isJsonNull())
        {
            return Map.of();
        }
        if (!subjectsElement.isJsonObject())
        {
            throw new IllegalStateException("\"subjects\" must be a JSON object");
        }
        Map<String, Map<String, Boolean>> subjects = new TreeMap<>();
        for (Map.Entry<String, JsonElement> subject : subjectsElement.getAsJsonObject().entrySet())
        {
            if (subject.getKey() == null || subject.getKey().isBlank()
                    || subject.getValue() == null || !subject.getValue().isJsonObject())
            {
                continue;
            }
            Map<String, Boolean> nodes = new TreeMap<>();
            for (Map.Entry<String, JsonElement> node
                    : subject.getValue().getAsJsonObject().entrySet())
            {
                JsonElement value = node.getValue();
                if (node.getKey() != null && value != null && value.isJsonPrimitive()
                        && value.getAsJsonPrimitive().isBoolean())
                {
                    nodes.put(node.getKey(), value.getAsBoolean());
                }
            }
            if (!nodes.isEmpty())
            {
                subjects.put(subject.getKey(), Collections.unmodifiableMap(nodes));
            }
        }
        return Collections.unmodifiableMap(subjects);
    }

    private JsonObject writeSubjects(Map<String, Map<String, Boolean>> decisions)
    {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        JsonObject subjects = new JsonObject();
        Map<String, Map<String, Boolean>> sorted = new TreeMap<>();
        for (Map.Entry<String, Map<String, Boolean>> subject : decisions.entrySet())
        {
            if (subject.getKey() != null && subject.getValue() != null)
            {
                sorted.put(subject.getKey(), subject.getValue());
            }
        }
        for (Map.Entry<String, Map<String, Boolean>> subject : sorted.entrySet())
        {
            String key = subject.getKey();
            Map<String, Boolean> nodes = subject.getValue();
            if (key.isBlank())
            {
                continue;
            }
            JsonObject nodeObject = new JsonObject();
            Map<String, Boolean> sortedNodes = new TreeMap<>();
            for (Map.Entry<String, Boolean> node : nodes.entrySet())
            {
                if (node.getKey() != null && node.getValue() != null)
                {
                    sortedNodes.put(node.getKey(), node.getValue());
                }
            }
            for (Map.Entry<String, Boolean> node : sortedNodes.entrySet())
            {
                nodeObject.addProperty(node.getKey(), node.getValue());
            }
            subjects.add(key, nodeObject);
        }
        root.add("subjects", subjects);
        return root;
    }

    private static String reason(Throwable failure)
    {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static void loaderLog(String message)
    {
        try
        {
            LifecycleHooks.logLine(message);
        }
        catch (Throwable ignored)
        {
        }
    }
}
