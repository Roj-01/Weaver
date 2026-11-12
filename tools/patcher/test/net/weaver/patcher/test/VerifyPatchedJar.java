package net.weaver.patcher.test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Loads the five patched classes through a normal {@link URLClassLoader} and
 * checks the reflected contract.
 *
 * <p>The patcher removes the original Mojang signature files and manifest
 * digests, exactly like NeoForm's digest strip. A patched jar is therefore
 * accepted by the same class-loading path the game uses; this probe proves it
 * instead of bypassing verification with a custom zip loader.</p>
 */
public final class VerifyPatchedJar {
    private static final String HOLDER = "net.weaver.patched.WeaverAttachmentHolder";
    private static final String PATCHED_GAME = "net.weaver.patched.PatchedGame";

    private VerifyPatchedJar() {
    }

    public static void main(String[] args) {
        try {
            if (args.length < 1) {
                throw new IllegalArgumentException("usage: VerifyPatchedJar <jar> [classpath-file]");
            }
            Path jar = Path.of(args[0]).toAbsolutePath().normalize();
            try (URLClassLoader loader = gameLoader(args.length > 1 ? Path.of(args[1]) : null, jar)) {
                check(loader, "net.minecraft.world.item.ItemStack", true);
                check(loader, "net.minecraft.world.entity.Entity", true);
                check(loader, "net.minecraft.world.level.block.entity.BlockEntity", true);
                check(loader, "net.minecraft.world.level.Level", true);
                check(loader, "net.minecraft.world.level.chunk.LevelChunk", true);
            }
            System.out.println("PATCHER PROBE ok=true jar=" + jar + " classes=5 loader=url");
        } catch (Throwable throwable) {
            System.err.println("PATCHER PROBE ok=false: " + throwable);
            throwable.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void check(ClassLoader loader, String binaryName, boolean patchedGame) throws Exception {
        Class<?> owner = Class.forName(binaryName, false, loader);
        Set<String> interfaces = new TreeSet<>();
        for (Class<?> type : owner.getInterfaces()) {
            interfaces.add(type.getName());
        }
        require(interfaces.contains(HOLDER), binaryName + " is missing " + HOLDER + ": " + interfaces);
        if (patchedGame) {
            require(interfaces.contains(PATCHED_GAME), binaryName + " is missing " + PATCHED_GAME);
        }

        requireMethod(owner, "weaver$getAttachment", Object.class, String.class);
        requireMethod(owner, "weaver$setAttachment", Object.class, String.class, Object.class);
        requireMethod(owner, "weaver$removeAttachment", Object.class, String.class);
        requireMethod(owner, "weaver$hasAttachment", boolean.class, String.class);
        if (patchedGame) {
            requireMethod(owner, "weaver$patchLevel", int.class);
        }
    }

    private static void requireMethod(Class<?> owner, String name, Class<?> returnType,
                                      Class<?>... parameters) throws Exception {
        Method method = owner.getDeclaredMethod(name, parameters);
        require(Modifier.isPublic(method.getModifiers()), owner.getName() + "." + name + " is not public");
        require(!Modifier.isStatic(method.getModifiers()), owner.getName() + "." + name + " is static");
        require(method.getReturnType().equals(returnType),
                owner.getName() + "." + name + " has return type " + method.getReturnType());
    }

    private static URLClassLoader gameLoader(Path classpathFile, Path gameJar) throws IOException {
        List<URL> urls = new ArrayList<>();
        urls.add(gameJar.toUri().toURL());
        if (classpathFile != null && Files.isRegularFile(classpathFile)) {
            String text = Files.readString(classpathFile);
            for (String entry : text.split("[;\\r\\n]+")) {
                String value = entry.trim();
                if (value.isEmpty()) {
                    continue;
                }
                Path path = Path.of(value).toAbsolutePath().normalize();
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                String normalized = path.toString().replace('\\', '/');
                if (!normalized.contains("/libraries/")) {
                    continue;
                }
                if (path.equals(gameJar)) {
                    continue;
                }
                urls.add(path.toUri().toURL());
            }
        }
        return new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
