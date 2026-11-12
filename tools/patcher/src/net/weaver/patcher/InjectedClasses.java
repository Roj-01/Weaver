package net.weaver.patcher;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Loads the classes copied unchanged into the patched jar and validates the
 * exact public contract the patch set delegates to.
 */
final class InjectedClasses {
    private static final Set<String> REQUIRED = Set.of(
            PatchSet.HOLDER_INTERFACE + ".class",
            PatchSet.PATCHED_GAME_INTERFACE + ".class",
            PatchSet.STORE_CLASS + ".class"
    );

    private InjectedClasses() {
    }

    static List<Injection> load(Path directory) throws IOException, PatchException {
        if (directory == null) {
            throw new PatchException("inject directory is required");
        }
        if (!Files.isDirectory(directory)) {
            throw new PatchException("inject directory does not exist: " + directory);
        }

        List<Injection> injections = new ArrayList<>();
        Set<String> names = new HashSet<>();
        try (Stream<Path> stream = Files.walk(directory)) {
            List<Path> files = stream.filter(Files::isRegularFile).sorted().toList();
            for (Path file : files) {
                String entryName = directory.relativize(file).toString().replace('\\', '/');
                if (entryName.isEmpty() || entryName.startsWith("/") || entryName.contains("../")) {
                    throw new PatchException("invalid injected entry name: " + entryName);
                }
                if (!names.add(entryName)) {
                    throw new PatchException("duplicate injected entry: " + entryName);
                }
                byte[] bytes = Files.readAllBytes(file);
                if (entryName.endsWith(".class")) {
                    validateClassEntry(entryName, bytes);
                }
                injections.add(new Injection(entryName, file.toAbsolutePath().normalize(), bytes));
            }
        }

        if (injections.isEmpty()) {
            throw new PatchException("inject directory is empty: " + directory);
        }
        for (String required : REQUIRED) {
            if (!names.contains(required)) {
                throw new PatchException("inject directory is missing required class: " + required);
            }
        }
        validateContract(injections);
        return List.copyOf(injections);
    }

    private static void validateClassEntry(String entryName, byte[] bytes) throws PatchException {
        ClassReader reader;
        try {
            reader = new ClassReader(bytes);
        } catch (RuntimeException e) {
            throw new PatchException("injected entry is not a readable class file: " + entryName, e);
        }
        int version = reader.readUnsignedShort(6);
        if (version > 69) {
            throw new PatchException("injected class " + entryName + " has class-file version " + version
                    + "; compile injected classes with --release 25 (version 69)");
        }
        String expected = entryName.substring(0, entryName.length() - ".class".length());
        if (!reader.getClassName().equals(expected)) {
            throw new PatchException("injected entry " + entryName + " contains class "
                    + reader.getClassName() + "; expected " + expected);
        }
    }

    private static void validateContract(List<Injection> injections) throws PatchException {
        Map<String, byte[]> classes = new HashMap<>();
        for (Injection injection : injections) {
            if (injection.entryName().endsWith(".class")) {
                classes.put(injection.entryName(), injection.bytes());
            }
        }

        ClassNode patchedGame = read(classes.get(PatchSet.PATCHED_GAME_INTERFACE + ".class"),
                PatchSet.PATCHED_GAME_INTERFACE);
        requireInterface(patchedGame);
        requireMethod(patchedGame, PatchSet.PATCH_LEVEL_METHOD, PatchSet.PATCH_LEVEL_DESC, true);

        ClassNode holder = read(classes.get(PatchSet.HOLDER_INTERFACE + ".class"), PatchSet.HOLDER_INTERFACE);
        requireInterface(holder);
        requireMethod(holder, PatchSet.GET, PatchSet.GET_DESC, true);
        requireMethod(holder, PatchSet.SET, PatchSet.SET_DESC, true);
        requireMethod(holder, PatchSet.REMOVE, PatchSet.REMOVE_DESC, true);
        requireMethod(holder, PatchSet.HAS, PatchSet.HAS_DESC, true);

        ClassNode store = read(classes.get(PatchSet.STORE_CLASS + ".class"), PatchSet.STORE_CLASS);
        if ((store.access & Opcodes.ACC_INTERFACE) != 0) {
            throw new PatchException(PatchSet.STORE_CLASS + " must be a class, not an interface");
        }
        if ((store.access & Opcodes.ACC_FINAL) == 0) {
            throw new PatchException(PatchSet.STORE_CLASS + " must be final");
        }
        if ((store.access & Opcodes.ACC_PUBLIC) == 0) {
            throw new PatchException(PatchSet.STORE_CLASS + " must be public");
        }
        requireStaticMethod(store, "get", PatchSet.STORE_GET_DESC);
        requireStaticMethod(store, "set", PatchSet.STORE_SET_DESC);
        requireStaticMethod(store, "remove", PatchSet.STORE_REMOVE_DESC);
        requireStaticMethod(store, "has", PatchSet.STORE_HAS_DESC);
        requireStaticMethod(store, "clear", PatchSet.STORE_CLEAR_DESC);
        requireStaticMethod(store, "size", PatchSet.STORE_SIZE_DESC);
    }

    private static ClassNode read(byte[] bytes, String name) throws PatchException {
        if (bytes == null) {
            throw new PatchException("missing injected class: " + name);
        }
        ClassNode node = new ClassNode();
        try {
            new ClassReader(bytes).accept(node, 0);
        } catch (RuntimeException e) {
            throw new PatchException("cannot read injected class " + name, e);
        }
        if (!node.name.equals(name)) {
            throw new PatchException("injected class " + name + " declares " + node.name);
        }
        return node;
    }

    private static void requireInterface(ClassNode node) throws PatchException {
        if ((node.access & Opcodes.ACC_INTERFACE) == 0) {
            throw new PatchException("injected type " + node.name + " must be an interface");
        }
        if ((node.access & Opcodes.ACC_PUBLIC) == 0) {
            throw new PatchException("injected type " + node.name + " must be public");
        }
    }

    private static void requireMethod(ClassNode node, String name, String descriptor, boolean abstractMethod)
            throws PatchException {
        for (MethodNode method : node.methods) {
            if (method.name.equals(name) && method.desc.equals(descriptor)) {
                if ((method.access & Opcodes.ACC_PUBLIC) == 0) {
                    throw new PatchException("injected method " + node.name + "." + name + descriptor
                            + " must be public");
                }
                if (abstractMethod && (method.access & Opcodes.ACC_ABSTRACT) == 0) {
                    throw new PatchException("injected interface method " + node.name + "." + name + descriptor
                            + " must be abstract");
                }
                return;
            }
        }
        throw new PatchException("injected type " + node.name + " is missing method " + name + descriptor);
    }

    private static void requireStaticMethod(ClassNode node, String name, String descriptor) throws PatchException {
        for (MethodNode method : node.methods) {
            if (method.name.equals(name) && method.desc.equals(descriptor)) {
                if ((method.access & Opcodes.ACC_PUBLIC) == 0) {
                    throw new PatchException("injected store method " + name + descriptor + " must be public");
                }
                if ((method.access & Opcodes.ACC_STATIC) == 0) {
                    throw new PatchException("injected store method " + name + descriptor + " must be static");
                }
                return;
            }
        }
        throw new PatchException("injected store " + node.name + " is missing method " + name + descriptor);
    }

    record Injection(String entryName, Path source, byte[] bytes) {
    }
}
