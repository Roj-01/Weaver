package net.weaver.patcher;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Applies the Weaver patch set to one jar and writes a deterministic result.
 *
 * <p>The destination is produced at a temporary path, fully reopened and
 * verified, then moved into place. A failure never leaves a partially written
 * destination.</p>
 */
public final class JarPatcher {
    private static final int COMPRESSION_LEVEL = 6;
    private static final String MANIFEST_ENTRY = "META-INF/MANIFEST.MF";
    private static final String META_INF = "META-INF/";

    private JarPatcher() {
    }

    public static Result patch(Path input,
                               Path output,
                               Path injectDirectory,
                               Path reportPath,
                               int patchLevel,
                               String patchSetId,
                               List<PatchSet.Target> targets) throws IOException, PatchException {
        long started = System.nanoTime();

        Path inputPath = requireInput(input);
        Path outputPath = absolute(output);
        Path reportTarget = absolute(reportPath);
        if (inputPath.equals(outputPath)) {
            throw new PatchException("input and output must be different files: " + inputPath);
        }
        if (Files.exists(outputPath) && Files.isSameFile(inputPath, outputPath)) {
            throw new PatchException("input and output resolve to the same file: " + inputPath);
        }
        if (outputPath.equals(reportTarget)) {
            throw new PatchException("output jar and report must be different files: " + outputPath);
        }
        if (reportTarget.equals(inputPath)
                || (Files.exists(reportTarget) && Files.isSameFile(reportTarget, inputPath))) {
            throw new PatchException("the report must not overwrite the input jar: " + inputPath);
        }
        if (patchLevel != PatchSet.PATCH_LEVEL) {
            throw new PatchException("unsupported patch level " + patchLevel
                    + "; this patcher implements level " + PatchSet.PATCH_LEVEL);
        }
        if (patchSetId == null || patchSetId.isBlank()) {
            throw new PatchException("patch set id is blank");
        }
        if (targets == null || targets.isEmpty()) {
            throw new PatchException("patch set contains no targets");
        }

        assertUniqueTargets(targets);
        List<InjectedClasses.Injection> injections = InjectedClasses.load(injectDirectory);

        String inputSha256 = Hashes.sha256(inputPath);
        Path outputParent = outputPath.getParent();
        if (outputParent == null) {
            throw new PatchException("output has no parent directory: " + outputPath);
        }
        Files.createDirectories(outputParent);
        if (Files.isDirectory(outputPath)) {
            throw new PatchException("output path is a directory: " + outputPath);
        }
        // Fail closed means a failed run cannot leave a stale artifact behind.
        Files.deleteIfExists(outputPath);

        Path temporary = Files.createTempFile(outputParent,
                "." + outputPath.getFileName() + "-", ".tmp");
        try {
            WriteResult writeResult = writeJar(inputPath, temporary, patchLevel, patchSetId,
                    inputSha256, targets, injections);
            String outputSha256 = Hashes.sha256(temporary);
            verifyJar(inputPath, temporary, patchLevel, targets, injections,
                    writeResult.metadataBytes(), writeResult.originalClasses());

            moveReplacing(temporary, outputPath);

            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            List<String> applied = new ArrayList<>(writeResult.applied());
            try {
                writeReport(reportTarget, patchSetId, patchLevel, inputPath, inputSha256,
                        outputPath, outputSha256, elapsedMs, applied, writeResult.skipped());
            } catch (IOException | RuntimeException reportFailure) {
                Files.deleteIfExists(outputPath);
                throw reportFailure;
            }
            return new Result(patchSetId, patchLevel, inputPath, inputSha256, outputPath, outputSha256,
                    elapsedMs, List.copyOf(applied), writeResult.skipped(), applied.size());
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Path requireInput(Path input) throws PatchException {
        Path path = absolute(input);
        if (!Files.isRegularFile(path)) {
            throw new PatchException("input jar does not exist or is not a regular file: " + path);
        }
        return path;
    }

    private static Path absolute(Path path) throws PatchException {
        if (path == null) {
            throw new PatchException("path must not be null");
        }
        return path.toAbsolutePath().normalize();
    }

    private static void assertUniqueTargets(List<PatchSet.Target> targets) throws PatchException {
        Set<String> names = new HashSet<>();
        for (PatchSet.Target target : targets) {
            if (!names.add(target.className())) {
                throw new PatchException("duplicate target in patch set: " + target.className());
            }
        }
    }

    private static WriteResult writeJar(Path inputPath,
                                        Path temporary,
                                        int patchLevel,
                                        String patchSetId,
                                        String inputSha256,
                                        List<PatchSet.Target> targets,
                                        List<InjectedClasses.Injection> injections)
            throws IOException, PatchException {
        try (ZipFile zip = openZip(inputPath)) {
            Map<String, ZipEntry> originalEntries = new TreeMap<>();
            Enumeration<? extends ZipEntry> enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                if (originalEntries.putIfAbsent(entry.getName(), entry) != null) {
                    throw new PatchException("input jar contains duplicate entry: " + entry.getName());
                }
            }

            Map<String, byte[]> replacements = new TreeMap<>();
            Map<String, ClassNode> originals = new TreeMap<>();
            List<String> applied = new ArrayList<>();
            List<String> skipped = new ArrayList<>();

            for (PatchSet.Target target : targets) {
                ZipEntry entry = originalEntries.get(target.entryName());
                if (entry == null) {
                    throw new PatchException("missing target class in input jar: " + target.entryName());
                }
                if (entry.isDirectory()) {
                    throw new PatchException("target class entry is a directory: " + target.entryName());
                }
                byte[] classBytes = readAll(zip.getInputStream(entry));
                ClassNode original = readClassNode(classBytes, target);
                ClassPatcher.PatchedClass patched = ClassPatcher.patch(classBytes, target);
                replacements.put(target.entryName(), patched.bytes());
                originals.put(target.entryName(), original);
                applied.addAll(patched.operations());
            }

            for (InjectedClasses.Injection injection : injections) {
                if (originalEntries.containsKey(injection.entryName())) {
                    throw new PatchException("injected entry conflicts with an input entry: "
                            + injection.entryName());
                }
            }
            if (originalEntries.containsKey(PatchSet.METADATA_ENTRY)) {
                throw new PatchException("input jar already contains " + PatchSet.METADATA_ENTRY);
            }

            int operationCount = applied.size() + injections.size() + 1;
            byte[] metadataBytes = metadata(patchSetId, patchLevel, inputSha256, operationCount);
            for (InjectedClasses.Injection injection : injections) {
                applied.add("inject " + injection.entryName());
            }
            applied.add("write-metadata " + PatchSet.METADATA_ENTRY);

            Set<String> outputNames = new TreeSet<>();
            for (String name : originalEntries.keySet()) {
                if (!isSignatureEntry(name)) {
                    outputNames.add(name);
                }
            }
            for (InjectedClasses.Injection injection : injections) {
                outputNames.add(injection.entryName());
            }
            outputNames.add(PatchSet.METADATA_ENTRY);

            try (ZipOutputStream output = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
                output.setLevel(COMPRESSION_LEVEL);
                byte[] buffer = new byte[65536];
                for (String name : outputNames) {
                    ZipEntry original = originalEntries.get(name);
                    boolean directory = original != null && (original.isDirectory() || name.endsWith("/"));
                    byte[] replacement = replacements.get(name);

                    ZipEntry entry = new ZipEntry(name);
                    entry.setTime(0L);
                    if (directory) {
                        entry.setMethod(ZipEntry.STORED);
                        entry.setSize(0L);
                        entry.setCompressedSize(0L);
                        entry.setCrc(0L);
                    } else {
                        entry.setMethod(ZipEntry.DEFLATED);
                    }
                    if (original != null && original.getComment() != null) {
                        entry.setComment(original.getComment());
                    }
                    output.putNextEntry(entry);

                    if (directory) {
                        // Directory entries carry no payload.
                    } else if (name.equals(MANIFEST_ENTRY) && original != null) {
                        // A modified signed jar cannot keep the original digests or
                        // signature files: the JVM would reject every patched class.
                        // Mirror NeoForm's StripManifestDigestContentFilter.
                        byte[] manifestBytes;
                        try (InputStream in = zip.getInputStream(original)) {
                            manifestBytes = stripSignatures(readAll(in));
                        }
                        output.write(manifestBytes);
                    } else if (replacement != null) {
                        output.write(replacement);
                    } else if (original != null) {
                        try (InputStream in = zip.getInputStream(original)) {
                            copy(in, output, buffer);
                        }
                    } else {
                        byte[] injected = findInjection(injections, name, metadataBytes);
                        output.write(injected);
                    }
                    output.closeEntry();
                }
            }

            return new WriteResult(List.copyOf(applied), List.copyOf(skipped), metadataBytes, originals);
        }
    }

    private static boolean isSignatureEntry(String name) {
        String upper = name.toUpperCase(java.util.Locale.ROOT);
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

    private static byte[] stripSignatures(byte[] manifestBytes) throws IOException {
        Manifest manifest = new Manifest(new ByteArrayInputStream(manifestBytes));
        stripSection(manifest.getMainAttributes());
        var iterator = manifest.getEntries().entrySet().iterator();
        while (iterator.hasNext()) {
            Attributes attributes = iterator.next().getValue();
            stripSection(attributes);
            if (attributes.isEmpty()) {
                iterator.remove();
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(manifestBytes.length);
        manifest.write(out);
        return out.toByteArray();
    }

    private static void stripSection(Attributes attributes) {
        attributes.keySet().removeIf(key -> {
            String name = key.toString();
            return name.equalsIgnoreCase("Magic")
                    || name.equalsIgnoreCase("Signature-Version")
                    || name.toLowerCase(java.util.Locale.ROOT).endsWith("-digest")
                    || name.equalsIgnoreCase("Digest-Algorithms");
        });
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
            String name = key.toString();
            if (name.equalsIgnoreCase("Magic")
                    || name.equalsIgnoreCase("Signature-Version")
                    || name.toLowerCase(java.util.Locale.ROOT).endsWith("-digest")
                    || name.equalsIgnoreCase("Digest-Algorithms")) {
                return true;
            }
        }
        return false;
    }

    private static byte[] findInjection(List<InjectedClasses.Injection> injections,
                                        String name,
                                        byte[] metadataBytes) throws PatchException {
        if (name.equals(PatchSet.METADATA_ENTRY)) {
            return metadataBytes;
        }
        for (InjectedClasses.Injection injection : injections) {
            if (injection.entryName().equals(name)) {
                return injection.bytes();
            }
        }
        throw new PatchException("internal error: no bytes for output entry " + name);
    }

    private static ClassNode readClassNode(byte[] classBytes, PatchSet.Target target) throws PatchException {
        ClassNode node = new ClassNode();
        try {
            new ClassReader(classBytes).accept(node, 0);
        } catch (RuntimeException e) {
            throw new PatchException("cannot read target class " + target.className(), e);
        }
        if (!node.name.equals(target.className())) {
            throw new PatchException("target entry " + target.entryName() + " contains " + node.name);
        }
        return node;
    }

    private static ZipFile openZip(Path path) throws PatchException {
        try {
            return new ZipFile(path.toFile());
        } catch (ZipException e) {
            throw new PatchException("not a readable jar/zip: " + path, e);
        } catch (IOException e) {
            throw new PatchException("cannot open jar: " + path + " (" + e.getMessage() + ")", e);
        }
    }

    private static void verifyJar(Path inputPath,
                                  Path outputPath,
                                  int patchLevel,
                                  List<PatchSet.Target> targets,
                                  List<InjectedClasses.Injection> injections,
                                  byte[] metadataBytes,
                                  Map<String, ClassNode> originals) throws IOException, PatchException {
        try (ZipFile input = openZip(inputPath);
             ZipFile output = openZip(outputPath)) {
            Map<String, ZipEntry> inEntries = entriesWithDuplicateCheck(input, "input");
            Map<String, ZipEntry> outEntries = entriesWithDuplicateCheck(output, "output");

            Set<String> expectedNames = new TreeSet<>();
            for (String name : inEntries.keySet()) {
                if (!isSignatureEntry(name)) {
                    expectedNames.add(name);
                }
            }
            for (InjectedClasses.Injection injection : injections) {
                expectedNames.add(injection.entryName());
            }
            expectedNames.add(PatchSet.METADATA_ENTRY);
            for (String name : outEntries.keySet()) {
                if (isSignatureEntry(name)) {
                    throw new PatchException("post-verification kept signature entry: " + name);
                }
            }
            if (!outEntries.keySet().equals(expectedNames)) {
                Set<String> missing = new TreeSet<>(expectedNames);
                missing.removeAll(outEntries.keySet());
                Set<String> extra = new TreeSet<>(outEntries.keySet());
                extra.removeAll(expectedNames);
                throw new PatchException("post-verification entry mismatch; missing=" + missing
                        + " extra=" + extra);
            }

            for (Map.Entry<String, ZipEntry> original : inEntries.entrySet()) {
                if (isSignatureEntry(original.getKey())) {
                    continue;
                }
                ZipEntry outputEntry = outEntries.get(original.getKey());
                if (outputEntry == null) {
                    throw new PatchException("post-verification lost original entry: " + original.getKey());
                }
                if (originals.containsKey(original.getKey())
                        || original.getKey().equals(MANIFEST_ENTRY)) {
                    continue;
                }
                if (outputEntry.getSize() != original.getValue().getSize()
                        || outputEntry.getCrc() != original.getValue().getCrc()) {
                    throw new PatchException("post-verification changed original entry: " + original.getKey());
                }
            }

            for (PatchSet.Target target : targets) {
                ZipEntry outputEntry = outEntries.get(target.entryName());
                if (outputEntry == null) {
                    throw new PatchException("post-verification missing target: " + target.entryName());
                }
                byte[] bytes;
                try (InputStream in = output.getInputStream(outputEntry)) {
                    bytes = readAll(in);
                }
                verifyPatchedClass(bytes, target, patchLevel, originals.get(target.entryName()));
            }

            for (InjectedClasses.Injection injection : injections) {
                ZipEntry outputEntry = outEntries.get(injection.entryName());
                if (outputEntry == null) {
                    throw new PatchException("post-verification missing injected entry: " + injection.entryName());
                }
                byte[] bytes;
                try (InputStream in = output.getInputStream(outputEntry)) {
                    bytes = readAll(in);
                }
                if (!java.util.Arrays.equals(bytes, injection.bytes())) {
                    throw new PatchException("post-verification injected entry differs: " + injection.entryName());
                }
            }

            ZipEntry manifestEntry = outEntries.get(MANIFEST_ENTRY);
            if (manifestEntry == null) {
                throw new PatchException("post-verification lost " + MANIFEST_ENTRY);
            }
            byte[] manifestBytes;
            try (InputStream in = output.getInputStream(manifestEntry)) {
                manifestBytes = readAll(in);
            }
            if (hasSignatureAttributes(manifestBytes)) {
                throw new PatchException("post-verification manifest still contains signature attributes");
            }

            ZipEntry metadataEntry = outEntries.get(PatchSet.METADATA_ENTRY);
            if (metadataEntry == null) {
                throw new PatchException("post-verification missing " + PatchSet.METADATA_ENTRY);
            }
            byte[] actualMetadata;
            try (InputStream in = output.getInputStream(metadataEntry)) {
                actualMetadata = readAll(in);
            }
            if (!java.util.Arrays.equals(actualMetadata, metadataBytes)) {
                throw new PatchException("post-verification metadata differs from expected bytes");
            }
        }
    }

    private static Map<String, ZipEntry> entriesWithDuplicateCheck(ZipFile zip, String label)
            throws PatchException {
        Map<String, ZipEntry> entries = new TreeMap<>();
        Enumeration<? extends ZipEntry> enumeration = zip.entries();
        while (enumeration.hasMoreElements()) {
            ZipEntry entry = enumeration.nextElement();
            if (entries.putIfAbsent(entry.getName(), entry) != null) {
                throw new PatchException(label + " jar contains duplicate entry: " + entry.getName());
            }
        }
        return entries;
    }

    private static void verifyPatchedClass(byte[] bytes,
                                           PatchSet.Target target,
                                           int patchLevel,
                                           ClassNode original) throws PatchException {
        if (original == null) {
            throw new PatchException("internal error: no original metadata for " + target.className());
        }
        ClassNode patched = new ClassNode();
        try {
            new ClassReader(bytes).accept(patched, 0);
        } catch (RuntimeException e) {
            throw new PatchException("post-verification cannot read " + target.className(), e);
        }

        String expectedSignature = original.signature;
        if (expectedSignature != null) {
            expectedSignature = expectedSignature
                    + "L" + PatchSet.HOLDER_INTERFACE + ";"
                    + (target.patchLevel() ? "L" + PatchSet.PATCHED_GAME_INTERFACE + ";" : "");
        }
        if (patched.access != original.access
                || !patched.name.equals(original.name)
                || !java.util.Objects.equals(patched.signature, expectedSignature)
                || !java.util.Objects.equals(patched.superName, original.superName)) {
            throw new PatchException("post-verification class header changed: " + target.className());
        }

        List<String> expectedInterfaces = new ArrayList<>(original.interfaces);
        expectedInterfaces.add(PatchSet.HOLDER_INTERFACE);
        if (target.patchLevel()) {
            expectedInterfaces.add(PatchSet.PATCHED_GAME_INTERFACE);
        }
        if (!patched.interfaces.equals(expectedInterfaces)) {
            throw new PatchException("post-verification interface list mismatch for " + target.className()
                    + ": expected " + expectedInterfaces + " got " + patched.interfaces);
        }

        if (patched.fields.size() != original.fields.size()) {
            throw new PatchException("post-verification field count changed for " + target.className());
        }
        for (int i = 0; i < original.fields.size(); i++) {
            FieldNode before = original.fields.get(i);
            FieldNode after = patched.fields.get(i);
            if (!before.name.equals(after.name) || !before.desc.equals(after.desc)
                    || before.access != after.access) {
                throw new PatchException("post-verification field changed in " + target.className()
                        + ": " + before.name);
            }
        }

        int addedMethods = 4 + (target.patchLevel() ? 1 : 0);
        if (patched.methods.size() != original.methods.size() + addedMethods) {
            throw new PatchException("post-verification method count changed for " + target.className()
                    + ": expected " + (original.methods.size() + addedMethods)
                    + " got " + patched.methods.size());
        }
        for (int i = 0; i < original.methods.size(); i++) {
            MethodNode before = original.methods.get(i);
            MethodNode after = patched.methods.get(i);
            if (!before.name.equals(after.name) || !before.desc.equals(after.desc)
                    || before.access != after.access) {
                throw new PatchException("post-verification existing method changed in "
                        + target.className() + ": " + before.name + before.desc);
            }
        }

        requireDelegatingMethod(patched, PatchSet.GET, PatchSet.GET_DESC, PatchSet.STORE_GET,
                PatchSet.STORE_GET_DESC, 1, false);
        requireDelegatingMethod(patched, PatchSet.SET, PatchSet.SET_DESC, PatchSet.STORE_SET,
                PatchSet.STORE_SET_DESC, 2, false);
        requireDelegatingMethod(patched, PatchSet.REMOVE, PatchSet.REMOVE_DESC, PatchSet.STORE_REMOVE,
                PatchSet.STORE_REMOVE_DESC, 1, false);
        requireDelegatingMethod(patched, PatchSet.HAS, PatchSet.HAS_DESC, PatchSet.STORE_HAS,
                PatchSet.STORE_HAS_DESC, 1, true);
        if (target.patchLevel()) {
            requirePatchLevelMethod(patched);
        }
    }

    private static void requireDelegatingMethod(ClassNode owner,
                                                String name,
                                                String descriptor,
                                                String storeName,
                                                String storeDescriptor,
                                                int argumentCount,
                                                boolean returnsBoolean) throws PatchException {
        MethodNode method = findMethod(owner, name, descriptor);
        if (method == null) {
            throw new PatchException("post-verification missing method " + owner.name + "." + name + descriptor);
        }
        if (method.access != Opcodes.ACC_PUBLIC) {
            throw new PatchException("post-verification method is not public: " + owner.name + "." + name + descriptor);
        }
        List<AbstractInsnNode> instructions = realInstructions(method);
        int expectedSize = argumentCount == 2 ? 5 : 4;
        if (instructions.size() != expectedSize) {
            throw new PatchException("post-verification method body is not straight-line: "
                    + owner.name + "." + name + descriptor);
        }
        expectVarInsn(instructions.get(0), Opcodes.ALOAD, 0, owner, name);
        expectVarInsn(instructions.get(1), Opcodes.ALOAD, 1, owner, name);
        int index = 2;
        if (argumentCount == 2) {
            expectVarInsn(instructions.get(index++), Opcodes.ALOAD, 2, owner, name);
        }
        AbstractInsnNode invocation = instructions.get(index++);
        if (!(invocation instanceof MethodInsnNode methodInsn)
                || methodInsn.getOpcode() != Opcodes.INVOKESTATIC
                || !methodInsn.owner.equals(PatchSet.STORE_CLASS)
                || !methodInsn.name.equals(storeName)
                || !methodInsn.desc.equals(storeDescriptor)
                || methodInsn.itf) {
            throw new PatchException("post-verification delegation mismatch for "
                    + owner.name + "." + name + descriptor);
        }
        AbstractInsnNode returnInstruction = instructions.get(index);
        int expectedReturn = returnsBoolean ? Opcodes.IRETURN : Opcodes.ARETURN;
        if (!(returnInstruction instanceof InsnNode) || returnInstruction.getOpcode() != expectedReturn) {
            throw new PatchException("post-verification return mismatch for "
                    + owner.name + "." + name + descriptor);
        }
    }

    private static void requirePatchLevelMethod(ClassNode owner) throws PatchException {
        MethodNode method = findMethod(owner, PatchSet.PATCH_LEVEL_METHOD, PatchSet.PATCH_LEVEL_DESC);
        if (method == null) {
            throw new PatchException("post-verification missing method " + owner.name + "."
                    + PatchSet.PATCH_LEVEL_METHOD + PatchSet.PATCH_LEVEL_DESC);
        }
        if (method.access != Opcodes.ACC_PUBLIC) {
            throw new PatchException("post-verification patch-level method is not public: " + owner.name);
        }
        List<AbstractInsnNode> instructions = realInstructions(method);
        if (instructions.size() != 2
                || !(instructions.get(0) instanceof InsnNode first)
                || first.getOpcode() != Opcodes.ICONST_1
                || !(instructions.get(1) instanceof InsnNode second)
                || second.getOpcode() != Opcodes.IRETURN) {
            throw new PatchException("post-verification patch-level body mismatch: " + owner.name);
        }
    }

    private static void expectVarInsn(AbstractInsnNode instruction, int opcode, int variable,
                                      ClassNode owner, String methodName) throws PatchException {
        if (!(instruction instanceof VarInsnNode varInsn)
                || varInsn.getOpcode() != opcode
                || varInsn.var != variable) {
            throw new PatchException("post-verification stack shape mismatch for "
                    + owner.name + "." + methodName);
        }
    }

    private static List<AbstractInsnNode> realInstructions(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            int type = instruction.getType();
            if (type == AbstractInsnNode.LABEL
                    || type == AbstractInsnNode.LINE
                    || type == AbstractInsnNode.FRAME) {
                continue;
            }
            result.add(instruction);
        }
        return result;
    }

    private static MethodNode findMethod(ClassNode owner, String name, String descriptor) {
        for (MethodNode method : owner.methods) {
            if (method.name.equals(name) && method.desc.equals(descriptor)) {
                return method;
            }
        }
        return null;
    }

    private static byte[] metadata(String patchSetId,
                                   int patchLevel,
                                   String sourceSha256,
                                   int operationCount) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"operationCount\": ").append(operationCount).append(",\n");
        json.append("  \"patchLevel\": ").append(patchLevel).append(",\n");
        json.append("  \"patchSet\": ").append(Json.quote(patchSetId)).append(",\n");
        json.append("  \"sourceSha256\": ").append(Json.quote(sourceSha256)).append("\n");
        json.append("}\n");
        return Json.bytes(json.toString());
    }

    private static void writeReport(Path reportPath,
                                    String patchSetId,
                                    int patchLevel,
                                    Path inputPath,
                                    String inputSha256,
                                    Path outputPath,
                                    String outputSha256,
                                    long elapsedMs,
                                    List<String> applied,
                                    List<String> skipped) throws IOException {
        Path parent = reportPath.getParent();
        if (parent == null) {
            throw new IOException("report has no parent directory: " + reportPath);
        }
        Files.createDirectories(parent);

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"patchSet\": ").append(Json.quote(patchSetId)).append(",\n");
        json.append("  \"patchLevel\": ").append(patchLevel).append(",\n");
        json.append("  \"input\": ").append(Json.quote(inputPath.toString())).append(",\n");
        json.append("  \"inputSha256\": ").append(Json.quote(inputSha256)).append(",\n");
        json.append("  \"output\": ").append(Json.quote(outputPath.toString())).append(",\n");
        json.append("  \"outputSha256\": ").append(Json.quote(outputSha256)).append(",\n");
        json.append("  \"elapsedMs\": ").append(elapsedMs).append(",\n");
        json.append("  \"operationCount\": ").append(applied.size()).append(",\n");
        json.append("  \"applied\": [\n");
        for (int i = 0; i < applied.size(); i++) {
            json.append("    ").append(Json.quote(applied.get(i)));
            json.append(i + 1 < applied.size() ? ",\n" : "\n");
        }
        json.append("  ],\n");
        json.append("  \"skipped\": [\n");
        for (int i = 0; i < skipped.size(); i++) {
            json.append("    ").append(Json.quote(skipped.get(i)));
            json.append(i + 1 < skipped.size() ? ",\n" : "\n");
        }
        json.append("  ]\n");
        json.append("}\n");

        Path temporary = Files.createTempFile(parent, "." + reportPath.getFileName() + "-", ".tmp");
        try {
            Files.writeString(temporary, json.toString(), StandardCharsets.UTF_8);
            moveReplacing(temporary, reportPath);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void moveReplacing(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        return input.readAllBytes();
    }

    private static void copy(InputStream input, OutputStream output, byte[] buffer) throws IOException {
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
    }

    private record WriteResult(List<String> applied,
                               List<String> skipped,
                               byte[] metadataBytes,
                               Map<String, ClassNode> originalClasses) {
    }

    public record Result(String patchSetId,
                         int patchLevel,
                         Path input,
                         String inputSha256,
                         Path output,
                         String outputSha256,
                         long elapsedMs,
                         List<String> applied,
                         List<String> skipped,
                         int operationCount) {
    }
}
