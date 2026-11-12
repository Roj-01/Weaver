package net.weaver.sourcepatcher;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class ReplaceJar {
    private static final int COMPRESSION_LEVEL = 6;
    private static final String MANIFEST_ENTRY = "META-INF/MANIFEST.MF";
    private static final String META_INF = "META-INF/";
    private static final String METADATA_ENTRY = "META-INF/weaver/source-patch.json";
    private static final Pattern PATCH_SET = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern CLASS_NAME = Pattern.compile(
            "[A-Za-z_$][A-Za-z0-9_$]*([./][A-Za-z_$][A-Za-z0-9_$]*)*");
    private static final Pattern PATCH_NAME = Pattern.compile("[A-Za-z0-9._-]+\\.patch");

    private ReplaceJar() {
    }

    public static void main(String[] args) {
        try {
            Arguments arguments = Arguments.parse(args);
            Result result = replace(arguments.input(), arguments.output(),
                    arguments.classes(), arguments.metadata());
            System.out.println("SOURCE PATCHER REPLACE ok=true jar="
                    + result.output().getFileName()
                    + " entries=" + result.entryCount()
                    + " replaced=" + result.replacedCount()
                    + " classes=" + result.compiledCount());
        } catch (Throwable failure) {
            System.err.println("SOURCE PATCHER REPLACE ok=false: " + failure.getMessage());
            failure.printStackTrace(System.err);
            System.exit(1);
        }
    }

    public static Result replace(Path input,
                                 Path output,
                                 Path classesDirectory,
                                 Path metadataFile) throws IOException {
        Path inputPath = absolute(input);
        Path outputPath = absolute(output);
        Path classesPath = absolute(classesDirectory);
        Path metadataPath = absolute(metadataFile);

        if (inputPath.equals(outputPath)) {
            throw new IOException("input and output must be different files: " + inputPath);
        }
        if (Files.exists(inputPath) && Files.exists(outputPath)
                && Files.isSameFile(inputPath, outputPath)) {
            throw new IOException("input and output resolve to the same file: " + inputPath);
        }
        if (outputPath.equals(metadataPath) || outputPath.equals(classesPath)) {
            throw new IOException("output must not overwrite the metadata or classes path");
        }
        Path outputParent = outputPath.getParent();
        if (outputParent == null) {
            throw new IOException("output has no parent directory: " + outputPath);
        }
        Files.createDirectories(outputParent);
        if (Files.isDirectory(outputPath)) {
            throw new IOException("output path is a directory: " + outputPath);
        }
        Files.deleteIfExists(outputPath);

        if (!Files.isRegularFile(inputPath)) {
            throw new IOException("input jar does not exist: " + inputPath);
        }
        if (!Files.isDirectory(classesPath)) {
            throw new IOException("compiled classes directory does not exist: " + classesPath);
        }
        if (!Files.isRegularFile(metadataPath)) {
            throw new IOException("metadata file does not exist: " + metadataPath);
        }

        ParsedMetadata metadata = parseMetadata(metadataPath);
        Map<String, byte[]> compiled = loadCompiledClasses(classesPath);
        for (String target : metadata.classes()) {
            String entryName = target.replace('.', '/') + ".class";
            if (!compiled.containsKey(entryName)) {
                throw new IOException("metadata target was not compiled: " + target);
            }
        }

        Map<String, ZipEntry> sourceEntries = readEntries(inputPath);
        for (String name : sourceEntries.keySet()) {
            if (name.equalsIgnoreCase(METADATA_ENTRY)) {
                throw new IOException("input jar already contains " + METADATA_ENTRY);
            }
        }

        Set<String> expectedNames = new TreeSet<>();
        for (String name : sourceEntries.keySet()) {
            if (!isSignatureEntry(name)) {
                expectedNames.add(name);
            }
        }
        for (String name : compiled.keySet()) {
            if (!expectedNames.contains(name)) {
                throw new IOException("compiled class has no matching jar entry: " + name);
            }
        }
        if (!expectedNames.add(METADATA_ENTRY)) {
            throw new IOException("output already contains " + METADATA_ENTRY);
        }

        Path temporary = Files.createTempFile(outputParent,
                "." + outputPath.getFileName() + "-", ".tmp");
        try {
            int replaced = writeJar(inputPath, temporary, sourceEntries, compiled, metadata);
            verifyJar(inputPath, temporary, sourceEntries, compiled, metadata, expectedNames);
            moveReplacing(temporary, outputPath);
            return new Result(outputPath, expectedNames.size(), replaced, compiled.size());
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static int writeJar(Path inputPath,
                                Path temporary,
                                Map<String, ZipEntry> sourceEntries,
                                Map<String, byte[]> compiled,
                                ParsedMetadata metadata) throws IOException {
        int replaced = 0;
        byte[] buffer = new byte[65536];
        Set<String> outputNames = new TreeSet<>();
        for (String name : sourceEntries.keySet()) {
            if (!isSignatureEntry(name)) {
                outputNames.add(name);
            }
        }
        outputNames.add(METADATA_ENTRY);

        try (ZipFile zip = openZip(inputPath);
             ZipOutputStream output = new ZipOutputStream(
                     new BufferedOutputStream(Files.newOutputStream(temporary)))) {
            output.setLevel(COMPRESSION_LEVEL);
            for (String name : outputNames) {
                if (name.equals(METADATA_ENTRY)) {
                    writeBytes(output, name, metadata.bytes(), null);
                    continue;
                }
                ZipEntry original = zip.getEntry(name);
                if (original == null) {
                    throw new IOException("input entry disappeared while writing: " + name);
                }
                boolean directory = original.isDirectory() || name.endsWith("/");
                if (directory) {
                    writeDirectory(output, name, original.getComment());
                    continue;
                }
                byte[] replacement = compiled.get(name);
                if (replacement != null) {
                    writeBytes(output, name, replacement, original.getComment());
                    replaced++;
                    continue;
                }
                if (name.equals(MANIFEST_ENTRY)) {
                    byte[] manifest = stripManifest(readAll(zip.getInputStream(original)));
                    writeBytes(output, name, manifest, original.getComment());
                    continue;
                }
                writeStream(output, name, zip.getInputStream(original), buffer,
                        original.getComment());
            }
        }
        return replaced;
    }

    private static void verifyJar(Path inputPath,
                                  Path outputPath,
                                  Map<String, ZipEntry> sourceEntries,
                                  Map<String, byte[]> compiled,
                                  ParsedMetadata metadata,
                                  Set<String> expectedNames) throws IOException {
        try (ZipFile input = openZip(inputPath);
             ZipFile output = openZip(outputPath)) {
            Map<String, ZipEntry> inputEntries = readEntries(input);
            if (!inputEntries.keySet().equals(sourceEntries.keySet())) {
                throw new IOException("input jar changed while the output was being built");
            }
            Map<String, ZipEntry> outputEntries = readEntries(output);
            List<String> actualOrder = new ArrayList<>();
            Enumeration<? extends ZipEntry> enumeration = output.entries();
            while (enumeration.hasMoreElements()) {
                actualOrder.add(enumeration.nextElement().getName());
            }
            List<String> expectedOrder = new ArrayList<>(expectedNames);
            if (!actualOrder.equals(expectedOrder)) {
                throw new IOException("output entries are not sorted deterministically");
            }
            if (!outputEntries.keySet().equals(expectedNames)) {
                throw new IOException("output entry set differs from the expected set");
            }
            for (String name : outputEntries.keySet()) {
                if (isSignatureEntry(name)) {
                    throw new IOException("output retained signature entry: " + name);
                }
            }
            for (Map.Entry<String, ZipEntry> entry : sourceEntries.entrySet()) {
                String name = entry.getKey();
                if (isSignatureEntry(name)) {
                    continue;
                }
                ZipEntry outputEntry = outputEntries.get(name);
                if (outputEntry == null) {
                    throw new IOException("output lost entry: " + name);
                }
                if (compiled.containsKey(name)
                        || name.equals(MANIFEST_ENTRY)
                        || name.equals(METADATA_ENTRY)) {
                    continue;
                }
                ZipEntry inputEntry = entry.getValue();
                if (inputEntry.isDirectory() != outputEntry.isDirectory()
                        || inputEntry.getSize() != outputEntry.getSize()
                        || inputEntry.getCrc() != outputEntry.getCrc()) {
                    throw new IOException("output changed entry content: " + name);
                }
            }
            for (Map.Entry<String, byte[]> entry : compiled.entrySet()) {
                byte[] actual = readEntryBytes(output, outputEntries.get(entry.getKey()));
                if (!Arrays.equals(actual, entry.getValue())) {
                    throw new IOException("output replacement differs: " + entry.getKey());
                }
            }
            byte[] actualMetadata = readEntryBytes(output, outputEntries.get(METADATA_ENTRY));
            if (!Arrays.equals(actualMetadata, metadata.bytes())) {
                throw new IOException("output metadata differs from the canonical bytes");
            }
            ZipEntry manifestEntry = outputEntries.get(MANIFEST_ENTRY);
            if (manifestEntry != null && hasSignatureAttributes(readEntryBytes(output, manifestEntry))) {
                throw new IOException("output manifest still contains signature attributes");
            }
        }
    }

    private static ParsedMetadata parseMetadata(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        Object root;
        try {
            root = new JsonReader(text).parse();
        } catch (IllegalArgumentException failure) {
            throw new IOException("invalid metadata JSON: " + failure.getMessage(), failure);
        }
        if (!(root instanceof Map<?, ?> rawMap)) {
            throw new IOException("metadata must be a JSON object");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IOException("metadata contains a non-string key");
            }
            map.put(key, entry.getValue());
        }
        Set<String> requiredKeys = Set.of("patchSet", "sourceSha256", "classes", "patches");
        Set<String> optionalKeys = Set.of("decompiler", "decompilerVersion",
                "decompilerSha256", "javaRelease", "patchCount", "targets");
        for (String key : map.keySet()) {
            if (!requiredKeys.contains(key) && !optionalKeys.contains(key)) {
                throw new IOException("unknown metadata key: " + key);
            }
        }
        for (String key : requiredKeys) {
            if (!map.containsKey(key)) {
                throw new IOException("metadata is missing required key: " + key);
            }
        }

        String patchSet = requireString(map, "patchSet");
        String sourceSha256 = requireString(map, "sourceSha256").toLowerCase(Locale.ROOT);
        List<String> classes = validatedClassNames(requireStringArray(map, "classes"), "class");
        List<String> patches = validatedSortedUnique(requireStringArray(map, "patches"),
                PATCH_NAME, "patch");
        String decompiler = optionalString(map, "decompiler");
        String decompilerVersion = optionalString(map, "decompilerVersion");
        String decompilerSha256 = optionalString(map, "decompilerSha256");
        if (decompilerSha256 != null) {
            decompilerSha256 = decompilerSha256.toLowerCase(Locale.ROOT);
            if (!SHA256.matcher(decompilerSha256).matches()) {
                throw new IOException("decompilerSha256 must be 64 hexadecimal characters");
            }
        }
        Long javaRelease = optionalInteger(map, "javaRelease");
        Long patchCount = optionalInteger(map, "patchCount");
        List<String> targets = null;
        if (map.containsKey("targets")) {
            targets = validatedClassNames(requireStringArray(map, "targets"), "target");
            if (targets.isEmpty()) {
                throw new IOException("metadata targets list is empty");
            }
        }
        if (!PATCH_SET.matcher(patchSet).matches()) {
            throw new IOException("invalid patchSet value: " + patchSet);
        }
        if (!SHA256.matcher(sourceSha256).matches()) {
            throw new IOException("sourceSha256 must be 64 lowercase hexadecimal characters");
        }
        if (decompiler != null && decompiler.isEmpty()) {
            throw new IOException("metadata decompiler must not be empty");
        }
        if (decompilerVersion != null && decompilerVersion.isEmpty()) {
            throw new IOException("metadata decompilerVersion must not be empty");
        }
        if (javaRelease != null && (javaRelease < 1 || javaRelease > 100)) {
            throw new IOException("metadata javaRelease is out of range: " + javaRelease);
        }
        if (patchCount != null && patchCount != patches.size()) {
            throw new IOException("metadata patchCount does not match the patches list");
        }
        if (classes.isEmpty()) {
            throw new IOException("metadata classes list is empty");
        }
        if (patches.isEmpty()) {
            throw new IOException("metadata patches list is empty");
        }
        byte[] canonical = canonicalMetadata(patchSet, sourceSha256, classes, patches,
                decompiler, decompilerVersion, decompilerSha256, javaRelease, patchCount, targets);
        return new ParsedMetadata(patchSet, sourceSha256, classes, patches,
                decompiler, decompilerVersion, decompilerSha256, javaRelease, patchCount,
                targets, canonical);
    }

    private static String requireString(Map<String, Object> map, String key) throws IOException {
        Object value = map.get(key);
        if (!(value instanceof String string)) {
            throw new IOException("metadata field " + key + " must be a string");
        }
        return string;
    }

    private static List<String> requireStringArray(Map<String, Object> map, String key)
            throws IOException {
        Object value = map.get(key);
        if (!(value instanceof List<?> list)) {
            throw new IOException("metadata field " + key + " must be an array");
        }
        List<String> result = new ArrayList<>(list.size());
        for (Object element : list) {
            if (!(element instanceof String string)) {
                throw new IOException("metadata field " + key + " must contain only strings");
            }
            result.add(string);
        }
        return result;
    }

    private static String optionalString(Map<String, Object> map, String key)
            throws IOException {
        if (!map.containsKey(key)) {
            return null;
        }
        return requireString(map, key);
    }

    private static Long optionalInteger(Map<String, Object> map, String key)
            throws IOException {
        if (!map.containsKey(key)) {
            return null;
        }
        Object value = map.get(key);
        if (!(value instanceof Long number)) {
            throw new IOException("metadata field " + key + " must be an integer");
        }
        return number;
    }

    private static List<String> validatedClassNames(List<String> values, String label)
            throws IOException {
        List<String> normalized = new ArrayList<>(values.size());
        for (String value : values) {
            normalized.add(value.replace('.', '/'));
        }
        return validatedSortedUnique(normalized, CLASS_NAME, label);
    }

    private static List<String> validatedSortedUnique(List<String> values,
                                                      Pattern pattern,
                                                      String label) throws IOException {
        List<String> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        for (int index = 0; index < sorted.size(); index++) {
            String value = sorted.get(index);
            if (!pattern.matcher(value).matches()) {
                throw new IOException("invalid " + label + " in metadata: " + value);
            }
            if (index > 0 && sorted.get(index - 1).equals(value)) {
                throw new IOException("duplicate " + label + " in metadata: " + value);
            }
        }
        return List.copyOf(sorted);
    }

    private static byte[] canonicalMetadata(String patchSet,
                                            String sourceSha256,
                                            List<String> classes,
                                            List<String> patches,
                                            String decompiler,
                                            String decompilerVersion,
                                            String decompilerSha256,
                                            Long javaRelease,
                                            Long patchCount,
                                            List<String> targets) {
        List<String> fields = new ArrayList<>();
        fields.add("  \"patchSet\": " + quote(patchSet));
        if (decompiler != null) {
            fields.add("  \"decompiler\": " + quote(decompiler));
        }
        if (decompilerVersion != null) {
            fields.add("  \"decompilerVersion\": " + quote(decompilerVersion));
        }
        if (decompilerSha256 != null) {
            fields.add("  \"decompilerSha256\": " + quote(decompilerSha256));
        }
        if (javaRelease != null) {
            fields.add("  \"javaRelease\": " + javaRelease);
        }
        fields.add("  \"sourceSha256\": " + quote(sourceSha256));
        fields.add("  \"classes\": " + arrayJson(classes));
        fields.add("  \"patches\": " + arrayJson(patches));
        if (patchCount != null) {
            fields.add("  \"patchCount\": " + patchCount);
        }
        if (targets != null) {
            fields.add("  \"targets\": " + arrayJson(targets));
        }
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append(String.join(",\n", fields));
        json.append("\n}\n");
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String arrayJson(List<String> values) {
        StringBuilder builder = new StringBuilder("[\n");
        for (int index = 0; index < values.size(); index++) {
            builder.append("    ").append(quote(values.get(index)));
            builder.append(index + 1 < values.size() ? ",\n" : "\n");
        }
        builder.append("  ]");
        return builder.toString();
    }

    private static String quote(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 2);
        builder.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        builder.append('"');
        return builder.toString();
    }

    private static Map<String, byte[]> loadCompiledClasses(Path classesPath) throws IOException {
        Map<String, byte[]> result = new TreeMap<>();
        List<Path> files;
        try (Stream<Path> stream = Files.walk(classesPath)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .sorted()
                    .toList();
        }
        for (Path file : files) {
            String relative = classesPath.relativize(file).toString()
                    .replace(File.separatorChar, '/');
            if (relative.startsWith(META_INF) || relative.startsWith("/")
                    || relative.contains("../")) {
                throw new IOException("unsafe compiled class path: " + relative);
            }
            byte[] previous = result.putIfAbsent(relative, Files.readAllBytes(file));
            if (previous != null) {
                throw new IOException("duplicate compiled class entry: " + relative);
            }
        }
        if (result.isEmpty()) {
            throw new IOException("no compiled classes found in " + classesPath);
        }
        return result;
    }

    private static byte[] stripManifest(byte[] manifestBytes) throws IOException {
        Manifest manifest = new Manifest(new ByteArrayInputStream(manifestBytes));
        stripSection(manifest.getMainAttributes());
        Iterator<Map.Entry<String, Attributes>> iterator =
                manifest.getEntries().entrySet().iterator();
        while (iterator.hasNext()) {
            Attributes attributes = iterator.next().getValue();
            stripSection(attributes);
            if (attributes.isEmpty()) {
                iterator.remove();
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(manifestBytes.length);
        manifest.write(output);
        return output.toByteArray();
    }

    private static void stripSection(Attributes attributes) {
        attributes.keySet().removeIf(key -> isSignatureAttribute(key.toString()));
    }

    private static boolean hasSignatureAttributes(byte[] manifestBytes) throws IOException {
        Manifest manifest = new Manifest(new ByteArrayInputStream(manifestBytes));
        if (hasSignatureAttributes(manifest.getMainAttributes())) {
            return true;
        }
        for (Attributes attributes : manifest.getEntries().values()) {
            if (hasSignatureAttributes(attributes)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSignatureAttributes(Attributes attributes) {
        for (Object key : attributes.keySet()) {
            if (isSignatureAttribute(key.toString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSignatureAttribute(String name) {
        return name.equalsIgnoreCase("Magic")
                || name.equalsIgnoreCase("Signature-Version")
                || name.equalsIgnoreCase("Digest-Algorithms")
                || name.toLowerCase(Locale.ROOT).endsWith("-digest");
    }

    private static boolean isSignatureEntry(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        if (!upper.startsWith(META_INF)) {
            return false;
        }
        String simple = upper.substring(META_INF.length());
        if (simple.isEmpty() || simple.indexOf('/') >= 0) {
            return false;
        }
        return simple.endsWith(".SF")
                || simple.endsWith(".RSA")
                || simple.endsWith(".DSA")
                || simple.endsWith(".EC")
                || simple.startsWith("SIG-");
    }

    private static void writeBytes(ZipOutputStream output,
                                   String name,
                                   byte[] bytes,
                                   String comment) throws IOException {
        ZipEntry entry = baseEntry(name, comment);
        output.putNextEntry(entry);
        output.write(bytes);
        output.closeEntry();
    }

    private static void writeStream(ZipOutputStream output,
                                    String name,
                                    InputStream source,
                                    byte[] buffer,
                                    String comment) throws IOException {
        ZipEntry entry = baseEntry(name, comment);
        output.putNextEntry(entry);
        try (InputStream input = source) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
        output.closeEntry();
    }

    private static void writeDirectory(ZipOutputStream output,
                                       String name,
                                       String comment) throws IOException {
        ZipEntry entry = baseEntry(name, comment);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(0L);
        entry.setCompressedSize(0L);
        entry.setCrc(0L);
        output.putNextEntry(entry);
        output.closeEntry();
    }

    private static ZipEntry baseEntry(String name, String comment) {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L);
        if (comment != null) {
            entry.setComment(comment);
        }
        return entry;
    }

    private static Map<String, ZipEntry> readEntries(Path path) throws IOException {
        try (ZipFile zip = openZip(path)) {
            return readEntries(zip);
        }
    }

    private static Map<String, ZipEntry> readEntries(ZipFile zip) throws IOException {
        Map<String, ZipEntry> entries = new TreeMap<>();
        Enumeration<? extends ZipEntry> enumeration = zip.entries();
        while (enumeration.hasMoreElements()) {
            ZipEntry entry = enumeration.nextElement();
            if (entries.putIfAbsent(entry.getName(), entry) != null) {
                throw new IOException("duplicate jar entry: " + entry.getName());
            }
        }
        return entries;
    }

    private static byte[] readEntryBytes(ZipFile zip, ZipEntry entry) throws IOException {
        if (entry == null) {
            throw new IOException("missing jar entry");
        }
        try (InputStream input = zip.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    private static ZipFile openZip(Path path) throws IOException {
        try {
            return new ZipFile(path.toFile());
        } catch (ZipException failure) {
            throw new IOException("not a readable jar/zip: " + path, failure);
        } catch (IOException failure) {
            throw new IOException("cannot open jar: " + path + " (" + failure.getMessage() + ")",
                    failure);
        }
    }

    private static void moveReplacing(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException failure) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        return input.readAllBytes();
    }

    private static Path absolute(Path path) throws IOException {
        if (path == null) {
            throw new IOException("path must not be null");
        }
        return path.toAbsolutePath().normalize();
    }

    private record ParsedMetadata(String patchSet,
                                  String sourceSha256,
                                  List<String> classes,
                                  List<String> patches,
                                  String decompiler,
                                  String decompilerVersion,
                                  String decompilerSha256,
                                  Long javaRelease,
                                  Long patchCount,
                                  List<String> targets,
                                  byte[] bytes) {
    }

    public record Result(Path output, int entryCount, int replacedCount, int compiledCount) {
    }

    private record Arguments(Path input, Path output, Path classes, Path metadata) {
        static Arguments parse(String[] args) {
            Path input = null;
            Path output = null;
            Path classes = null;
            Path metadata = null;
            for (int index = 0; index < args.length; index++) {
                String name = args[index];
                switch (name) {
                    case "--input" -> input = Path.of(value(args, ++index, name));
                    case "--output" -> output = Path.of(value(args, ++index, name));
                    case "--classes" -> classes = Path.of(value(args, ++index, name));
                    case "--metadata" -> metadata = Path.of(value(args, ++index, name));
                    default -> throw new IllegalArgumentException("unknown argument: " + name);
                }
            }
            if (input == null || output == null || classes == null || metadata == null) {
                throw new IllegalArgumentException("usage: ReplaceJar --input <jar> --output <jar> "
                        + "--classes <dir> --metadata <json>");
            }
            return new Arguments(input, output, classes, metadata);
        }

        private static String value(String[] args, int index, String name) {
            if (index >= args.length) {
                throw new IllegalArgumentException("missing value for " + name);
            }
            return args[index];
        }
    }

    private static final class JsonReader {
        private final String text;
        private int position;

        JsonReader(String text) {
            this.text = Objects.requireNonNull(text);
        }

        Object parse() {
            skipWhitespace();
            Object value = parseValue();
            skipWhitespace();
            if (position != text.length()) {
                throw error("trailing data");
            }
            return value;
        }

        private Object parseValue() {
            if (position >= text.length()) {
                throw error("unexpected end of input");
            }
            char character = text.charAt(position);
            if (character == '{') {
                return parseObject();
            }
            if (character == '[') {
                return parseArray();
            }
            if (character == '"') {
                return parseString();
            }
            if (character == '-' || (character >= '0' && character <= '9')) {
                return parseNumber();
            }
            throw error("expected a string, object, array, or integer");
        }

        private Long parseNumber() {
            int start = position;
            if (position < text.length() && text.charAt(position) == '-') {
                position++;
            }
            int digitsStart = position;
            while (position < text.length() && text.charAt(position) >= '0'
                    && text.charAt(position) <= '9') {
                position++;
            }
            if (position == digitsStart) {
                throw error("invalid integer");
            }
            if (position < text.length()) {
                char next = text.charAt(position);
                if (next == '.' || next == 'e' || next == 'E') {
                    throw error("only integer metadata values are supported");
                }
            }
            try {
                return Long.parseLong(text.substring(start, position));
            } catch (NumberFormatException failure) {
                throw error("integer out of range");
            }
        }

        private Map<String, Object> parseObject() {
            expect('{');
            skipWhitespace();
            Map<String, Object> result = new LinkedHashMap<>();
            if (peek('}')) {
                position++;
                return result;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                Object value = parseValue();
                if (result.putIfAbsent(key, value) != null) {
                    throw error("duplicate key: " + key);
                }
                skipWhitespace();
                char character = next();
                if (character == '}') {
                    return result;
                }
                if (character != ',') {
                    throw error("expected ',' or '}'");
                }
            }
        }

        private List<Object> parseArray() {
            expect('[');
            skipWhitespace();
            List<Object> result = new ArrayList<>();
            if (peek(']')) {
                position++;
                return result;
            }
            while (true) {
                skipWhitespace();
                result.add(parseValue());
                skipWhitespace();
                char character = next();
                if (character == ']') {
                    return result;
                }
                if (character != ',') {
                    throw error("expected ',' or ']'");
                }
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (true) {
                if (position >= text.length()) {
                    throw error("unterminated string");
                }
                char character = text.charAt(position++);
                if (character == '"') {
                    return builder.toString();
                }
                if (character == '\\') {
                    if (position >= text.length()) {
                        throw error("unterminated escape");
                    }
                    char escape = text.charAt(position++);
                    switch (escape) {
                        case '"' -> builder.append('"');
                        case '\\' -> builder.append('\\');
                        case '/' -> builder.append('/');
                        case 'b' -> builder.append('\b');
                        case 'f' -> builder.append('\f');
                        case 'n' -> builder.append('\n');
                        case 'r' -> builder.append('\r');
                        case 't' -> builder.append('\t');
                        case 'u' -> appendUnicodeEscape(builder);
                        default -> throw error("invalid escape");
                    }
                } else {
                    if (character < 0x20) {
                        throw error("control character in string");
                    }
                    builder.append(character);
                }
            }
        }

        private void appendUnicodeEscape(StringBuilder builder) {
            if (position + 4 > text.length()) {
                throw error("short unicode escape");
            }
            String hex = text.substring(position, position + 4);
            position += 4;
            try {
                builder.append((char) Integer.parseInt(hex, 16));
            } catch (NumberFormatException failure) {
                throw error("invalid unicode escape");
            }
        }

        private void skipWhitespace() {
            while (position < text.length()) {
                char character = text.charAt(position);
                if (character == ' ' || character == '\t'
                        || character == '\r' || character == '\n') {
                    position++;
                } else {
                    return;
                }
            }
        }

        private boolean peek(char expected) {
            return position < text.length() && text.charAt(position) == expected;
        }

        private void expect(char expected) {
            if (position >= text.length() || text.charAt(position) != expected) {
                throw error("expected '" + expected + "'");
            }
            position++;
        }

        private char next() {
            if (position >= text.length()) {
                throw error("unexpected end of input");
            }
            return text.charAt(position++);
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at offset " + position);
        }
    }
}
