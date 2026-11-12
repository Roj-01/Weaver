package net.weaver.core.api;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * The frozen API: every public and protected type, method, constructor, and field
 * of {@code api} and {@code api-game} that mods can link against, compared with
 * the recorded baseline {@code api-baseline/weaver-api.txt}.
 *
 * <ul>
 *   <li>A line in the baseline that is gone is a breaking change (a removed or
 *   changed member): the test fails.</li>
 *   <li>A new line is an addition: allowed in a minor release, but it must be
 *   recorded, so the test fails until the baseline is updated with
 *   {@code gradlew :core:test --tests '*ApiSurfaceTest' -PrecordApi}.</li>
 * </ul>
 *
 * <p>Not API, and left out: packages with {@code mixin} in their name,
 * {@code net.weaver.api.game.event.support}, and anything marked
 * {@code @Internal}. Without the game ({@code -PskipGameApi}) only the
 * {@code api} lines are compared.</p>
 */
class ApiSurfaceTest {
    private static final String INTERNAL = "Lnet/weaver/api/annotation/Internal;";

    @Test
    void publicApiMatchesTheBaseline() throws Exception {
        Path baseline = repoRoot().resolve("api-baseline").resolve("weaver-api.txt");
        TreeSet<String> current = new TreeSet<>();
        Path apiRoot = root("net/weaver/api/WeaverApi.class");
        Path gameRoot = root("net/weaver/api/game/WeaverGameApi.class");
        current.addAll(surface("api", apiRoot));
        if (gameRoot != null) {
            current.addAll(surface("api-game", gameRoot));
        }
        if (Boolean.getBoolean("weaver.api.record")) {
            Files.createDirectories(baseline.getParent());
            Files.write(baseline, current, StandardCharsets.UTF_8);
            System.out.println("[weaver] API BASELINE recorded lines=" + current.size() + " file=" + baseline);
            return;
        }
        if (!Files.exists(baseline)) {
            fail("no API baseline at " + baseline + "; record it with -PrecordApi");
        }
        TreeSet<String> recorded = new TreeSet<>(Files.readAllLines(baseline, StandardCharsets.UTF_8));
        recorded.removeIf(String::isBlank);
        if (gameRoot == null) {
            recorded.removeIf(line -> line.startsWith("api-game "));
        }
        List<String> removed = new ArrayList<>(recorded);
        removed.removeAll(current);
        List<String> added = new ArrayList<>(current);
        added.removeAll(recorded);
        if (!removed.isEmpty() || !added.isEmpty()) {
            StringBuilder message = new StringBuilder();
            if (!removed.isEmpty()) {
                message.append("BREAKING API CHANGE: ").append(removed.size())
                        .append(" frozen members are gone or changed:\n");
                removed.stream().limit(40).forEach(line -> message.append("  - ").append(line).append('\n'));
            }
            if (!added.isEmpty()) {
                message.append("API ADDITIONS not recorded (").append(added.size())
                        .append("); record them with -PrecordApi:\n");
                added.stream().limit(40).forEach(line -> message.append("  + ").append(line).append('\n'));
            }
            fail(message.toString());
        }
    }

    /** The jar or class directory that holds {@code resource}, or null when it is not on the class path. */
    private static Path root(String resource) throws URISyntaxException {
        URL url = ApiSurfaceTest.class.getClassLoader().getResource(resource);
        if (url == null) {
            return null;
        }
        String text = url.toString();
        if (text.startsWith("jar:")) {
            String jar = text.substring("jar:".length(), text.indexOf("!/"));
            return Path.of(new java.net.URI(jar));
        }
        Path file = Path.of(url.toURI());
        Path rootPath = file;
        for (int i = 0; i < resource.split("/").length; i++) {
            rootPath = rootPath.getParent();
        }
        return rootPath;
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("settings.gradle.kts"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("repository root not found from " + Path.of("").toAbsolutePath());
        }
        return dir;
    }

    /** Every exported line of one module. */
    private static List<String> surface(String module, Path root) throws IOException {
        Map<String, byte[]> classes = new HashMap<>();
        if (Files.isDirectory(root)) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : (Iterable<Path>) files::iterator) {
                    String name = root.relativize(file).toString().replace('\\', '/');
                    if (name.startsWith("net/weaver/api/") && name.endsWith(".class")) {
                        classes.put(name.substring(0, name.length() - 6), Files.readAllBytes(file));
                    }
                }
            }
        } else {
            try (JarFile jar = new JarFile(root.toFile())) {
                for (JarEntry entry : java.util.Collections.list(jar.entries())) {
                    String name = entry.getName();
                    if (name.startsWith("net/weaver/api/") && name.endsWith(".class")) {
                        try (InputStream in = jar.getInputStream(entry)) {
                            classes.put(name.substring(0, name.length() - 6), in.readAllBytes());
                        }
                    }
                }
            }
        }
        Map<String, ClassInfo> infos = new HashMap<>();
        for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
            ClassInfo info = new ClassInfo();
            new ClassReader(entry.getValue()).accept(info, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            infos.put(info.name, info);
        }
        List<String> lines = new ArrayList<>();
        for (ClassInfo info : infos.values()) {
            if (!exported(info, infos)) {
                continue;
            }
            lines.add(module + " " + info.header());
            for (String member : info.members) {
                lines.add(module + " " + info.name + " " + member);
            }
        }
        return lines;
    }

    private static boolean exported(ClassInfo info, Map<String, ClassInfo> infos) {
        if (info.name.contains("/mixin/") || info.name.startsWith("net/weaver/api/game/event/support/")
                || info.internal || (info.access & Opcodes.ACC_SYNTHETIC) != 0 || info.name.endsWith("package-info")) {
            return false;
        }
        int access = info.innerAccess >= 0 ? info.innerAccess : info.access;
        if ((access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) == 0) {
            return false;
        }
        if (info.outer != null) {
            ClassInfo outer = infos.get(info.outer);
            return outer != null && exported(outer, infos);
        }
        return true;
    }

    /** One class: its header and its exported members. */
    private static final class ClassInfo extends ClassVisitor {
        String name;
        int access;
        int innerAccess = -1;
        String outer;
        boolean anonymousOrLocal;
        boolean internal;
        String superName;
        String[] interfaces;
        final Set<String> members = new TreeSet<>();

        ClassInfo() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.name = name;
            this.access = access;
            this.superName = superName;
            this.interfaces = interfaces == null ? new String[0] : interfaces.clone();
            Arrays.sort(this.interfaces);
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            if (name.equals(this.name)) {
                this.innerAccess = access;
                this.outer = outerName;
                this.anonymousOrLocal = outerName == null || innerName == null;
                if (this.anonymousOrLocal && this.outer == null) {
                    // A local or anonymous class: never API.
                    this.internal = true;
                }
            }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (INTERNAL.equals(descriptor)) {
                internal = true;
            }
            return null;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (!exportedMember(access)) {
                return null;
            }
            String line = "field " + name + " " + descriptor + flags(access)
                    + (value != null && (access & Opcodes.ACC_FINAL) != 0 ? " = " + value : "");
            return new FieldVisitor(Opcodes.ASM9) {
                private boolean memberInternal;

                @Override
                public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                    if (INTERNAL.equals(annotation)) {
                        memberInternal = true;
                    }
                    return null;
                }

                @Override
                public void visitEnd() {
                    if (!memberInternal) {
                        members.add(line);
                    }
                }
            };
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if (!exportedMember(access) || (access & Opcodes.ACC_BRIDGE) != 0 || name.equals("<clinit>")) {
                return null;
            }
            String line = "method " + name + descriptor + flags(access);
            return new MethodVisitor(Opcodes.ASM9) {
                private boolean memberInternal;

                @Override
                public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                    if (INTERNAL.equals(annotation)) {
                        memberInternal = true;
                    }
                    return null;
                }

                @Override
                public void visitEnd() {
                    if (!memberInternal) {
                        members.add(line);
                    }
                }
            };
        }

        private static boolean exportedMember(int access) {
            return (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0 && (access & Opcodes.ACC_SYNTHETIC) == 0;
        }

        private static String flags(int access) {
            StringBuilder flags = new StringBuilder();
            if ((access & Opcodes.ACC_PROTECTED) != 0) {
                flags.append(" protected");
            }
            if ((access & Opcodes.ACC_STATIC) != 0) {
                flags.append(" static");
            }
            if ((access & Opcodes.ACC_FINAL) != 0) {
                flags.append(" final");
            }
            if ((access & Opcodes.ACC_ABSTRACT) != 0) {
                flags.append(" abstract");
            }
            return flags.toString();
        }

        String header() {
            int flagsAccess = innerAccess >= 0 ? innerAccess : access;
            String kind = (flagsAccess & Opcodes.ACC_ANNOTATION) != 0 ? "annotation"
                    : (flagsAccess & Opcodes.ACC_INTERFACE) != 0 ? "interface"
                    : (flagsAccess & Opcodes.ACC_ENUM) != 0 ? "enum" : "class";
            return name + " " + kind + flags(flagsAccess)
                    + " extends " + superName + (interfaces.length == 0 ? "" : " implements " + String.join(",", interfaces));
        }
    }
}
