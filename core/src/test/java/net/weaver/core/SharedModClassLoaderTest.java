package net.weaver.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertSame;

/** Delegation rules of {@link SharedModClassLoader}. */
class SharedModClassLoaderTest {

    @TempDir
    Path temp;

    @Test
    void gameApiClassesAreDefinedChildFirstTogetherWithTheirSupertypes() throws Exception {
        Path shared = jar("shared.jar",
                "net/weaver/api/game/ProbeBase", "java/lang/Object",
                "net/weaver/api/game/ProbeChild", "net/weaver/api/game/ProbeBase",
                "net/weaver/other/Plain", "java/lang/Object");
        Path parentOnly = jar("parent-only.jar",
                "net/weaver/api/game/OnlyInParent", "java/lang/Object");
        try (URLClassLoader parent = new URLClassLoader(
                     new URL[]{shared.toUri().toURL(), parentOnly.toUri().toURL()},
                     ClassLoader.getPlatformClassLoader());
             SharedModClassLoader loader = new SharedModClassLoader(
                     new URL[]{shared.toUri().toURL()}, parent)) {

            Class<?> child = Class.forName("net.weaver.api.game.ProbeChild", false, loader);
            assertSame(loader, child.getClassLoader(), "api-game class must be child-first");
            assertSame(loader, child.getSuperclass().getClassLoader(),
                    "an api-game supertype must come from the same loader as its subtype");
            assertSame(child, loader.loadClass("net.weaver.api.game.ProbeChild"),
                    "a second lookup returns the already defined class");

            assertSame(parent, loader.loadClass("net.weaver.other.Plain").getClassLoader(),
                    "other classes stay parent-first");
            assertSame(parent, loader.loadClass("net.weaver.api.game.OnlyInParent").getClassLoader(),
                    "an api-game class the shared loader cannot see falls back to the parent");
        }
    }

    @Test
    void nfLibraryClassesAreDefinedChildFirst() throws Exception {
        Path shared = jar("nf.jar",
                "net/weaver/nf/ProbeHolder", "java/lang/Object",
                "net/weaver/nfx/NotTheLibrary", "java/lang/Object");
        try (URLClassLoader parent = new URLClassLoader(new URL[]{shared.toUri().toURL()},
                     ClassLoader.getPlatformClassLoader());
             SharedModClassLoader loader = new SharedModClassLoader(
                     new URL[]{shared.toUri().toURL()}, parent)) {
            assertSame(loader, loader.loadClass("net.weaver.nf.ProbeHolder").getClassLoader(),
                    "weaver-nf classes reference game types, so they must be child-first");
            assertSame(parent, loader.loadClass("net.weaver.nfx.NotTheLibrary").getClassLoader(),
                    "only the net.weaver.nf package is child-first");
        }
    }

    /** Writes a jar of empty public classes given as (internal name, super name) pairs. */
    private Path jar(String file, String... namesAndSupers) throws IOException {
        Path path = temp.resolve(file);
        try (OutputStream raw = Files.newOutputStream(path);
             JarOutputStream out = new JarOutputStream(raw)) {
            for (int i = 0; i < namesAndSupers.length; i += 2) {
                out.putNextEntry(new JarEntry(namesAndSupers[i] + ".class"));
                out.write(type(namesAndSupers[i], namesAndSupers[i + 1]));
                out.closeEntry();
            }
        }
        return path;
    }

    private static byte[] type(String internalName, String superName) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, superName, null);
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
