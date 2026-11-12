package net.weaver.core.version;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/**
 * Test-only class loaders that simulate the optional typed game API without a
 * dependency on the {@code api-game} artifact.
 */
final class TestGameApiClassLoaders {

    static final String GAME_API_CLASS = "net.weaver.api.game.WeaverGameApi";

    private TestGameApiClassLoaders() {
    }

    static ClassLoader missing() {
        return new ClassLoader(ClassLoader.getPlatformClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve)
                    throws ClassNotFoundException {
                if (GAME_API_CLASS.equals(name)) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        };
    }

    static ClassLoader withVersion(String version) {
        byte[] bytes = gameApiBytes(version);
        return new ClassLoader(ClassLoader.getPlatformClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve)
                    throws ClassNotFoundException {
                if (!GAME_API_CLASS.equals(name)) {
                    return super.loadClass(name, resolve);
                }
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    loaded = defineClass(name, bytes, 0, bytes.length);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        };
    }

    private static byte[] gameApiBytes(String version) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
                "net/weaver/api/game/WeaverGameApi", null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "VERSION", "Ljava/lang/String;", null, version).visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
