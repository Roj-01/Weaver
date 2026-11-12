package net.weaver.core.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.weaver.api.registry.DataPackSnapshot;
import net.weaver.api.registry.RegistryKey;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads an ordered set of data packs into immutable
 * {@link DataPackSnapshot} layers.
 *
 * <p>Pack roots are supplied from lowest to highest priority. The repository
 * assigns the priority {@code basePriority + index}, so the result is
 * deterministic and a higher-priority pack can replace a lower-priority value
 * when the layers are applied with
 * {@link net.weaver.api.registry.DataRegistryManager#loadAll(List)}. The list
 * order is the only precedence input; {@code pack.mcmeta} format metadata is
 * descriptive and never reorders packs.</p>
 *
 * <p>Each pack may contain a vanilla-style {@code pack.mcmeta} and
 * {@code data/<namespace>/...} trees. Only the Weaver-owned paths
 * {@code data/<namespace>/weaver/registry/...} and
 * {@code data/<namespace>/weaver/tags/...} are read. A pack whose
 * {@code pack.mcmeta} is present but malformed is skipped as a whole and
 * reported; a missing {@code pack.mcmeta} is accepted with default metadata so
 * existing Weaver packs keep working. Parse and layout problems inside an
 * accepted pack are collected while the rest of the pack is still read.</p>
 *
 * <p>Pack precedence follows the input order: the first snapshot is the lowest
 * priority and the last snapshot is the highest. Tags keep their own optional
 * {@code priority} and {@code replace} flags, so a higher-priority replacing
 * tag declaration removes lower-priority values exactly like vanilla data
 * packs when the snapshots are applied in order.</p>
 *
 * <p>Files are bounded: at most {@value #MAX_PACKS} packs, at most
 * {@value #MAX_FILES_PER_PACK} JSON files per pack, at most
 * {@value #MAX_FILE_BYTES} bytes per file, and at most
 * {@value #MAX_TOTAL_BYTES_PER_PACK} bytes per pack. Oversized files are
 * skipped; exceeding the total byte or file count stops the rest of that pack
 * and is reported. Absolute entry paths, {@code ..} segments, backslashes, and
 * symbolic links are rejected. This class has no game imports.</p>
 */
public final class DataPackRepository
{
    /** The maximum number of pack roots read in one load. */
    public static final int MAX_PACKS = 64;
    /** The maximum number of JSON files read from one pack. */
    public static final int MAX_FILES_PER_PACK = 4096;
    /** The maximum size of one JSON file in bytes. */
    public static final long MAX_FILE_BYTES = 1_048_576L;
    /** The maximum total JSON bytes read from one pack. */
    public static final long MAX_TOTAL_BYTES_PER_PACK = 16_777_216L;

    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_./-]+");

    /**
     * Metadata read from one accepted pack.
     *
     * @param id           the pack id used as the snapshot source, not
     *                     {@code null}
     * @param root         the absolute, normalized pack root, not {@code null}
     * @param priority     the computed layer priority
     * @param packFormat   the declared {@code pack_format}, or {@code 0} when
     *                     {@code pack.mcmeta} is absent
     * @param description  the declared description, never {@code null}
     * @param minFormat    the lowest supported format
     * @param maxFormat    the highest supported format
     */
    public record PackInfo(String id, Path root, int priority, int packFormat,
                           String description, int minFormat, int maxFormat)
    {
        /** Validates the metadata. */
        public PackInfo
        {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(root, "root");
            Objects.requireNonNull(description, "description");
            if (minFormat > maxFormat)
            {
                throw new IllegalArgumentException("minFormat " + minFormat
                        + " is greater than maxFormat " + maxFormat);
            }
        }

        /**
         * Whether this pack declares support for a format.
         *
         * @param format the format to test
         * @return {@code true} when the format is inside the supported range
         */
        public boolean supports(int format)
        {
            return format >= minFormat && format <= maxFormat;
        }
    }

    /**
     * The result of reading a set of packs.
     *
     * @param snapshots the accepted pack layers in input order, lowest
     *                  priority first, never {@code null}
     * @param packs     metadata for the accepted packs in input order, never
     *                  {@code null}
     * @param problems  human-readable problems with pack-relative paths, never
     *                  {@code null}
     */
    public record Report(List<DataPackSnapshot> snapshots, List<PackInfo> packs,
                         List<String> problems)
    {
        /** Copies the snapshot, pack, and problem lists. */
        public Report
        {
            Objects.requireNonNull(snapshots, "snapshots");
            Objects.requireNonNull(packs, "packs");
            Objects.requireNonNull(problems, "problems");
            snapshots = List.copyOf(snapshots);
            packs = List.copyOf(packs);
            problems = List.copyOf(problems);
        }

        /**
         * Whether no pack was accepted.
         *
         * @return {@code true} when {@link #snapshots()} is empty
         */
        public boolean isEmpty()
        {
            return snapshots.isEmpty();
        }
    }

    private DataPackRepository()
    {
    }

    /**
     * Reads packs with base priority {@code 0}.
     *
     * @param packRoots pack roots from lowest to highest priority, not
     *                  {@code null}
     * @return the report, never {@code null}
     */
    public static Report load(List<Path> packRoots)
    {
        return load(packRoots, 0);
    }

    /**
     * Reads packs and assigns deterministic priorities.
     *
     * <p>Each accepted pack receives {@code basePriority + index}; the input
     * order is therefore the precedence order. A malformed pack is skipped
     * without preventing later packs from loading.</p>
     *
     * @param packRoots    pack roots from lowest to highest priority, not
     *                     {@code null}
     * @param basePriority the priority assigned to the first pack
     * @return the report, never {@code null}
     */
    public static Report load(List<Path> packRoots, int basePriority)
    {
        Objects.requireNonNull(packRoots, "packRoots");
        final List<DataPackSnapshot> snapshots = new ArrayList<>();
        final List<PackInfo> packs = new ArrayList<>();
        final List<String> problems = new ArrayList<>();
        int count = packRoots.size();
        if (count > MAX_PACKS)
        {
            problems.add("packs: count=" + count + " limit=" + MAX_PACKS
                    + " action=skip-remaining");
            count = MAX_PACKS;
        }
        for (int index = 0; index < count; index++)
        {
            final Path root = packRoots.get(index);
            if (root == null)
            {
                problems.add("pack[" + index + "]: null pack root");
                continue;
            }
            final String packId = packId(root, index);
            final int priority = priority(basePriority, index, packId, problems);
            final Path normalized = root.toAbsolutePath().normalize();
            final String prefix = "pack[" + index + "] " + packId + ": ";
            final DataPackSnapshot snapshot = readPack(normalized, packId, priority,
                    prefix, problems, packs);
            if (snapshot != null)
            {
                snapshots.add(snapshot);
            }
        }
        return new Report(snapshots, packs, problems);
    }

    /**
     * Reads one pack with explicit identity and priority.
     *
     * <p>This is the compatibility path used by {@link DataPackLoader#read}.
     * Problem messages are not prefixed with a pack index, so a single pack
     * produces the same diagnostics as the previous reader.</p>
     *
     * @param packRoot the pack root, not {@code null}
     * @param packId   the snapshot source label, not {@code null}
     * @param priority the layer priority
     * @return the report, never {@code null}
     */
    static Report readSingle(Path packRoot, String packId, int priority)
    {
        final List<DataPackSnapshot> snapshots = new ArrayList<>();
        final List<PackInfo> packs = new ArrayList<>();
        final List<String> problems = new ArrayList<>();
        final Path normalized = packRoot.toAbsolutePath().normalize();
        final DataPackSnapshot snapshot = readPack(normalized, packId, priority,
                "", problems, packs);
        if (snapshot != null)
        {
            snapshots.add(snapshot);
        }
        return new Report(snapshots, packs, problems);
    }

    private static DataPackSnapshot readPack(Path root, String packId, int priority,
                                             String prefix, List<String> problems,
                                             List<PackInfo> packs)
    {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
        {
            problems.add(prefix + "pack root is not a directory: " + root);
            return null;
        }
        final Metadata metadata = readMetadata(root, prefix, problems);
        if (metadata == null)
        {
            return null;
        }
        final DataPackSnapshot.Builder builder = DataPackSnapshot.builder(packId, priority);
        final Budget budget = new Budget(prefix, problems);
        final Path dataRoot = root.resolve("data");
        if (!Files.isDirectory(dataRoot, LinkOption.NOFOLLOW_LINKS))
        {
            problems.add(prefix + "data: missing data directory");
            packs.add(new PackInfo(packId, root, priority, metadata.packFormat(),
                    metadata.description(), metadata.minFormat(), metadata.maxFormat()));
            return builder.build();
        }
        for (Path namespaceDir : listDirectories(dataRoot, root, prefix, problems))
        {
            if (budget.exhausted())
            {
                break;
            }
            final String namespace = fileName(namespaceDir);
            if (!NAMESPACE.matcher(namespace).matches() || namespace.contains(".."))
            {
                problems.add(prefix + packPath(root, namespaceDir)
                        + ": invalid data namespace '" + namespace + "'");
                continue;
            }
            readRegistryEntries(root, namespaceDir, builder, budget, problems, prefix);
            readTagFiles(root, namespaceDir, namespace, builder, budget, problems,
                    priority, prefix);
        }
        packs.add(new PackInfo(packId, root, priority, metadata.packFormat(),
                metadata.description(), metadata.minFormat(), metadata.maxFormat()));
        return builder.build();
    }

    private static Metadata readMetadata(Path root, String prefix, List<String> problems)
    {
        final Path file = root.resolve("pack.mcmeta");
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS))
        {
            return Metadata.defaults();
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
        {
            problems.add(prefix + "pack.mcmeta: not a regular file");
            return null;
        }
        final String path = prefix + "pack.mcmeta";
        long size;
        try
        {
            size = Files.size(file);
        }
        catch (IOException | RuntimeException failure)
        {
            problems.add(path + ": " + describe(failure));
            return null;
        }
        if (size > MAX_FILE_BYTES)
        {
            problems.add(path + ": file too large size=" + size
                    + " limit=" + MAX_FILE_BYTES);
            return null;
        }
        final JsonObject rootObject;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
        {
            final JsonElement element = JsonParser.parseReader(reader);
            if (element == null || !element.isJsonObject())
            {
                problems.add(path + ": root must be a JSON object");
                return null;
            }
            rootObject = element.getAsJsonObject();
        }
        catch (IOException | RuntimeException failure)
        {
            problems.add(path + ": " + describe(failure));
            return null;
        }

        final JsonElement packElement = rootObject.get("pack");
        if (packElement == null || !packElement.isJsonObject())
        {
            problems.add(path + ": missing pack object");
            return null;
        }
        final JsonObject pack = packElement.getAsJsonObject();

        final Integer format = integer(pack.get("pack_format"));
        if (format == null || format < 1)
        {
            problems.add(path + ": pack.pack_format must be an integer of at least 1");
            return null;
        }

        String description = "";
        final JsonElement descriptionElement = pack.get("description");
        if (descriptionElement != null && !descriptionElement.isJsonNull())
        {
            if (descriptionElement.isJsonPrimitive()
                    && descriptionElement.getAsJsonPrimitive().isString())
            {
                description = descriptionElement.getAsString();
            }
            else if (descriptionElement.isJsonObject()
                    || descriptionElement.isJsonArray())
            {
                description = descriptionElement.toString();
            }
            else
            {
                problems.add(path
                        + ": pack.description must be a string or a JSON text component");
                return null;
            }
        }

        int minFormat = format;
        int maxFormat = format;
        final JsonElement supportedElement = pack.get("supported_formats");
        if (supportedElement != null && !supportedElement.isJsonNull())
        {
            final int[] supported = supportedFormats(supportedElement, path, problems);
            if (supported == null)
            {
                return null;
            }
            minFormat = supported[0];
            maxFormat = supported[1];
        }
        return new Metadata(format, description, minFormat, maxFormat);
    }

    private static int[] supportedFormats(JsonElement element, String path,
                                          List<String> problems)
    {
        if (element.isJsonPrimitive())
        {
            final Integer format = integer(element);
            if (format == null || format < 1)
            {
                problems.add(path + ": supported_formats must contain positive integers");
                return null;
            }
            return new int[]{format, format};
        }
        if (element.isJsonArray())
        {
            final JsonArray array = element.getAsJsonArray();
            if (array.size() != 2)
            {
                problems.add(path + ": supported_formats array must have two elements");
                return null;
            }
            final Integer min = integer(array.get(0));
            final Integer max = integer(array.get(1));
            return range(min, max, path, problems);
        }
        if (element.isJsonObject())
        {
            final JsonObject object = element.getAsJsonObject();
            final Integer min = integer(object.get("min_inclusive"));
            final Integer max = integer(object.get("max_inclusive"));
            if (min == null || max == null)
            {
                problems.add(path + ": supported_formats object needs min_inclusive"
                        + " and max_inclusive integers");
                return null;
            }
            return range(min, max, path, problems);
        }
        problems.add(path + ": supported_formats must be an integer, an array, or an object");
        return null;
    }

    private static int[] range(Integer min, Integer max, String path,
                               List<String> problems)
    {
        if (min == null || max == null || min < 1 || max < 1)
        {
            problems.add(path + ": supported_formats must contain positive integers");
            return null;
        }
        if (min > max)
        {
            problems.add(path + ": supported_formats min " + min
                    + " is greater than max " + max);
            return null;
        }
        return new int[]{min, max};
    }

    private static void readRegistryEntries(Path root, Path namespaceDir,
                                            DataPackSnapshot.Builder builder,
                                            Budget budget, List<String> problems,
                                            String prefix)
    {
        final Path registryRoot = namespaceDir.resolve("weaver").resolve("registry");
        if (!Files.isDirectory(registryRoot, LinkOption.NOFOLLOW_LINKS))
        {
            return;
        }
        for (Path registryNamespaceDir : listDirectories(registryRoot, root, prefix, problems))
        {
            if (budget.exhausted())
            {
                return;
            }
            final String registryNamespace = fileName(registryNamespaceDir);
            if (!NAMESPACE.matcher(registryNamespace).matches()
                    || registryNamespace.contains(".."))
            {
                problems.add(prefix + packPath(root, registryNamespaceDir)
                        + ": invalid registry namespace '" + registryNamespace + "'");
                continue;
            }
            for (Path registryPathDir : listDirectories(registryNamespaceDir, root, prefix,
                    problems))
            {
                if (budget.exhausted())
                {
                    return;
                }
                final String registryPath = fileName(registryPathDir);
                if (!PATH.matcher(registryPath).matches() || registryPath.indexOf('/') >= 0
                        || registryPath.contains(".."))
                {
                    problems.add(prefix + packPath(root, registryPathDir)
                            + ": invalid registry path '" + registryPath + "'");
                    continue;
                }
                final String registryId = registryNamespace + ":" + registryPath;
                for (Path file : listJsonFiles(registryPathDir, root, prefix, problems))
                {
                    if (budget.exhausted())
                    {
                        return;
                    }
                    final String path = packPath(root, file);
                    final String entryPath = idPath(registryPathDir, file);
                    if (entryPath == null)
                    {
                        problems.add(prefix + path + ": invalid entry path");
                        continue;
                    }
                    if (!PATH.matcher(entryPath).matches())
                    {
                        problems.add(prefix + path
                                + ": invalid entry path '" + entryPath + "'");
                        continue;
                    }
                    if (!budget.allow(file, path))
                    {
                        continue;
                    }
                    final Map<String, Object> data = readObject(file, root, prefix, problems);
                    if (data != null)
                    {
                        builder.entry(registryId, entryPath, data);
                    }
                }
            }
        }
    }

    private static void readTagFiles(Path root, Path namespaceDir, String namespace,
                                     DataPackSnapshot.Builder builder, Budget budget,
                                     List<String> problems, int priority, String prefix)
    {
        final Path tagsRoot = namespaceDir.resolve("weaver").resolve("tags");
        if (!Files.isDirectory(tagsRoot, LinkOption.NOFOLLOW_LINKS))
        {
            return;
        }
        for (Path registryNamespaceDir : listDirectories(tagsRoot, root, prefix, problems))
        {
            if (budget.exhausted())
            {
                return;
            }
            final String registryNamespace = fileName(registryNamespaceDir);
            if (!NAMESPACE.matcher(registryNamespace).matches()
                    || registryNamespace.contains(".."))
            {
                problems.add(prefix + packPath(root, registryNamespaceDir)
                        + ": invalid registry namespace '" + registryNamespace + "'");
                continue;
            }
            for (Path registryPathDir : listDirectories(registryNamespaceDir, root, prefix,
                    problems))
            {
                if (budget.exhausted())
                {
                    return;
                }
                final String registryPath = fileName(registryPathDir);
                if (!PATH.matcher(registryPath).matches() || registryPath.indexOf('/') >= 0
                        || registryPath.contains(".."))
                {
                    problems.add(prefix + packPath(root, registryPathDir)
                            + ": invalid registry path '" + registryPath + "'");
                    continue;
                }
                final String registryId = registryNamespace + ":" + registryPath;
                for (Path file : listJsonFiles(registryPathDir, root, prefix, problems))
                {
                    if (budget.exhausted())
                    {
                        return;
                    }
                    final String path = packPath(root, file);
                    final String tagPath = idPath(registryPathDir, file);
                    if (tagPath == null)
                    {
                        problems.add(prefix + path + ": invalid tag path");
                        continue;
                    }
                    if (!PATH.matcher(tagPath).matches())
                    {
                        problems.add(prefix + path + ": invalid tag path '" + tagPath + "'");
                        continue;
                    }
                    if (!budget.allow(file, path))
                    {
                        continue;
                    }
                    final DataPackSnapshot.TagDeclaration declaration =
                            readTagDeclaration(file, root, prefix, priority, problems);
                    if (declaration != null)
                    {
                        builder.tag(registryId, namespace + ":" + tagPath, declaration);
                    }
                }
            }
        }
    }

    private static DataPackSnapshot.TagDeclaration readTagDeclaration(Path file, Path root,
                                                                     String prefix,
                                                                     int snapshotPriority,
                                                                     List<String> problems)
    {
        final String path = prefix + packPath(root, file);
        final JsonObject object;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
        {
            final JsonElement element = JsonParser.parseReader(reader);
            if (element == null || !element.isJsonObject())
            {
                problems.add(path + ": root must be a JSON object");
                return null;
            }
            object = element.getAsJsonObject();
        }
        catch (IOException | RuntimeException failure)
        {
            problems.add(path + ": " + describe(failure));
            return null;
        }

        int priority = snapshotPriority;
        final JsonElement priorityElement = object.get("priority");
        if (priorityElement != null && !priorityElement.isJsonNull())
        {
            if (!isNumber(priorityElement))
            {
                problems.add(path + ": priority must be an integer");
            }
            else
            {
                final double number = priorityElement.getAsDouble();
                if (number != Math.rint(number) || number < Integer.MIN_VALUE
                        || number > Integer.MAX_VALUE)
                {
                    problems.add(path + ": priority must be an integer");
                }
                else
                {
                    priority = (int) number;
                }
            }
        }

        boolean replace = false;
        final JsonElement replaceElement = object.get("replace");
        if (replaceElement != null && !replaceElement.isJsonNull())
        {
            if (!isBoolean(replaceElement))
            {
                problems.add(path + ": replace must be a boolean");
            }
            else
            {
                replace = replaceElement.getAsBoolean();
            }
        }

        final List<DataPackSnapshot.TagValue> values = new ArrayList<>();
        final JsonElement valuesElement = object.get("values");
        if (valuesElement != null && !valuesElement.isJsonNull())
        {
            if (!valuesElement.isJsonArray())
            {
                problems.add(path + ": values must be an array");
                return null;
            }
            final JsonArray array = valuesElement.getAsJsonArray();
            for (JsonElement element : array)
            {
                final DataPackSnapshot.TagValue value = readTagValue(element, path, problems);
                if (value != null)
                {
                    values.add(value);
                }
            }
        }
        return new DataPackSnapshot.TagDeclaration(values, priority, replace);
    }

    private static DataPackSnapshot.TagValue readTagValue(JsonElement element, String path,
                                                          List<String> problems)
    {
        if (element == null || element.isJsonNull())
        {
            problems.add(path + ": tag value must be a string or object");
            return null;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString())
        {
            return parseTagValue(element.getAsString(), false, path, problems);
        }
        if (!element.isJsonObject())
        {
            problems.add(path + ": tag value must be a string or object");
            return null;
        }
        final JsonObject object = element.getAsJsonObject();
        final JsonElement idElement = object.get("id");
        if (idElement == null || !idElement.isJsonPrimitive()
                || !idElement.getAsJsonPrimitive().isString())
        {
            problems.add(path + ": tag value object needs a string id");
            return null;
        }
        boolean required = false;
        final JsonElement requiredElement = object.get("required");
        if (requiredElement != null && !requiredElement.isJsonNull())
        {
            if (!isBoolean(requiredElement))
            {
                problems.add(path + ": tag value required must be a boolean");
                return null;
            }
            required = requiredElement.getAsBoolean();
        }
        return parseTagValue(idElement.getAsString(), required, path, problems);
    }

    private static DataPackSnapshot.TagValue parseTagValue(String raw, boolean required,
                                                           String path, List<String> problems)
    {
        try
        {
            final boolean reference = raw.startsWith("#");
            final String id = reference ? raw.substring(1) : raw;
            final String canonical = RegistryKey.parse(id).id();
            return reference
                    ? DataPackSnapshot.TagValue.reference(canonical, required)
                    : DataPackSnapshot.TagValue.value(canonical, required);
        }
        catch (IllegalArgumentException failure)
        {
            problems.add(path + ": invalid tag value '" + raw + "': " + describe(failure));
            return null;
        }
    }

    private static Map<String, Object> readObject(Path file, Path root, String prefix,
                                                  List<String> problems)
    {
        final String path = prefix + packPath(root, file);
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
        {
            final JsonElement element = JsonParser.parseReader(reader);
            if (element == null || !element.isJsonObject())
            {
                problems.add(path + ": root must be a JSON object");
                return null;
            }
            return dataObject(element.getAsJsonObject());
        }
        catch (IOException | RuntimeException failure)
        {
            problems.add(path + ": " + describe(failure));
            return null;
        }
    }

    private static Map<String, Object> dataObject(JsonObject object)
    {
        final Map<String, Object> data = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet())
        {
            data.put(entry.getKey(), dataValue(entry.getValue()));
        }
        return data;
    }

    private static Object dataValue(JsonElement element)
    {
        if (element == null || element.isJsonNull())
        {
            return null;
        }
        if (element.isJsonObject())
        {
            return dataObject(element.getAsJsonObject());
        }
        if (element.isJsonArray())
        {
            final List<Object> values = new ArrayList<>();
            for (JsonElement child : element.getAsJsonArray())
            {
                values.add(dataValue(child));
            }
            return values;
        }
        final JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean())
        {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber())
        {
            return primitive.getAsDouble();
        }
        return primitive.getAsString();
    }

    private static List<Path> listDirectories(Path directory, Path root, String prefix,
                                              List<String> problems)
    {
        final List<Path> directories = new ArrayList<>();
        try (Stream<Path> stream = Files.list(directory))
        {
            stream.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> isInside(root, path))
                    .sorted()
                    .forEach(directories::add);
        }
        catch (IOException | UncheckedIOException failure)
        {
            problems.add(prefix + packPath(root, directory) + ": " + describe(failure));
        }
        return directories;
    }

    private static List<Path> listJsonFiles(Path directory, Path root, String prefix,
                                            List<String> problems)
    {
        final List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(directory))
        {
            stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> isInside(root, path))
                    .sorted()
                    .forEach(files::add);
        }
        catch (IOException | UncheckedIOException failure)
        {
            problems.add(prefix + packPath(root, directory) + ": " + describe(failure));
        }
        return files;
    }

    private static boolean isInside(Path root, Path path)
    {
        final Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root))
        {
            return false;
        }
        for (Path segment : root.relativize(normalized))
        {
            final String value = segment.toString();
            if (value.equals("..") || value.indexOf('\\') >= 0 || value.indexOf(':') >= 0)
            {
                return false;
            }
        }
        return true;
    }

    private static String idPath(Path base, Path file)
    {
        final String path = base.relativize(file).toString().replace('\\', '/');
        if (!path.endsWith(".json"))
        {
            return null;
        }
        final String id = path.substring(0, path.length() - 5);
        if (!isSafeRelative(id))
        {
            return null;
        }
        return id;
    }

    /**
     * Whether a path is a safe pack-relative path.
     *
     * <p>A safe path is non-empty, relative, uses {@code /} separators, has no
     * empty, {@code .}, or {@code ..} segment, and contains no backslash or
     * colon.</p>
     *
     * @param path the path to test
     * @return {@code true} when the path is safe
     */
    static boolean isSafeRelative(String path)
    {
        if (path == null || path.isEmpty())
        {
            return false;
        }
        if (path.startsWith("/") || path.startsWith("\\"))
        {
            return false;
        }
        if (path.indexOf('\\') >= 0 || path.indexOf(':') >= 0 || path.contains(".."))
        {
            return false;
        }
        final String[] segments = path.split("/", -1);
        for (String segment : segments)
        {
            if (segment.isEmpty() || segment.equals(".") || segment.equals(".."))
            {
                return false;
            }
        }
        return true;
    }

    private static String packPath(Path root, Path path)
    {
        final Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root))
        {
            return path.toString().replace('\\', '/');
        }
        return root.relativize(normalized).toString().replace('\\', '/');
    }

    private static String fileName(Path path)
    {
        final Path name = path.getFileName();
        return name == null ? "" : name.toString();
    }

    private static String packId(Path root, int index)
    {
        final Path name = root.getFileName();
        return name == null || name.toString().isBlank()
                ? "pack-" + index
                : name.toString();
    }

    private static int priority(int basePriority, int index, String packId,
                                List<String> problems)
    {
        final long value = (long) basePriority + index;
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
        {
            problems.add("pack[" + index + "] " + packId
                    + ": priority overflow; using " + Integer.MAX_VALUE);
            return Integer.MAX_VALUE;
        }
        return (int) value;
    }

    private static boolean isNumber(JsonElement element)
    {
        return element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber();
    }

    private static boolean isBoolean(JsonElement element)
    {
        return element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean();
    }

    private static Integer integer(JsonElement element)
    {
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber())
        {
            return null;
        }
        final double number = element.getAsDouble();
        if (number != Math.rint(number) || number < Integer.MIN_VALUE
                || number > Integer.MAX_VALUE)
        {
            return null;
        }
        return (int) number;
    }

    private static String describe(Throwable failure)
    {
        final String message = failure.getMessage();
        return message != null ? message : failure.getClass().getName();
    }

    private record Metadata(int packFormat, String description, int minFormat, int maxFormat)
    {
        private static Metadata defaults()
        {
            return new Metadata(0, "", 0, 0);
        }
    }

    private static final class Budget
    {
        private final String prefix;
        private final List<String> problems;
        private int files;
        private long bytes;
        private boolean exhausted;

        private Budget(String prefix, List<String> problems)
        {
            this.prefix = prefix;
            this.problems = problems;
        }

        private boolean allow(Path file, String path)
        {
            if (exhausted)
            {
                return false;
            }
            if (files >= MAX_FILES_PER_PACK)
            {
                problems.add(prefix + path + ": file limit exceeded limit="
                        + MAX_FILES_PER_PACK + " action=skip-remaining");
                exhausted = true;
                return false;
            }
            final long size;
            try
            {
                size = Files.size(file);
            }
            catch (IOException | RuntimeException failure)
            {
                problems.add(prefix + path + ": " + describe(failure));
                return false;
            }
            if (size > MAX_FILE_BYTES)
            {
                problems.add(prefix + path + ": file too large size=" + size
                        + " limit=" + MAX_FILE_BYTES);
                return false;
            }
            if (size > MAX_TOTAL_BYTES_PER_PACK - bytes)
            {
                problems.add(prefix + path + ": pack byte limit exceeded limit="
                        + MAX_TOTAL_BYTES_PER_PACK + " action=skip-remaining");
                exhausted = true;
                return false;
            }
            files++;
            bytes += size;
            return true;
        }

        private boolean exhausted()
        {
            return exhausted;
        }
    }
}
