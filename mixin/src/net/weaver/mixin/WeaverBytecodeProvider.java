package net.weaver.mixin;

import java.io.IOException;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.service.IClassBytecodeProvider;

/**
 * Supplies class bytes to the Mixin engine from the owning mod loader first, then the game loader,
 * then this engine. The routing lives in {@code ResourceRegistry}; this class is only the
 * {@code IClassBytecodeProvider} adapter.
 */
public class WeaverBytecodeProvider implements IClassBytecodeProvider {

    @Override
    public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
        return getClassNode(name, false, 0);
    }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers)
            throws ClassNotFoundException, IOException {
        return getClassNode(name, runTransformers, 0);
    }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers, int readerFlags)
            throws ClassNotFoundException, IOException {
        byte[] bytes = readClassBytes(name);
        if (bytes == null) {
            throw new ClassNotFoundException(name);
        }
        ClassReader reader = new ClassReader(bytes);
        ClassNode node = new ClassNode(Opcodes.ASM9);
        reader.accept(node, readerFlags == 0 ? ClassReader.SKIP_FRAMES : readerFlags);
        return node;
    }

    static byte[] readClassBytes(String name) throws IOException {
        byte[] bytes = net.weaver.mixin.core.MixinEngine.get().resources().classBytes(name);
        if (bytes != null) {
            return bytes;
        }
        String resource = name.replace('.', '/') + ".class";
        // The engine's own loader does not necessarily see the game classpath. Try the
        // process classpath before giving up.
        for (ClassLoader candidate : new ClassLoader[] {
                WeaverBytecodeProvider.class.getClassLoader(),
                Thread.currentThread().getContextClassLoader(),
                ClassLoader.getSystemClassLoader()}) {
            if (candidate == null) {
                continue;
            }
            try (java.io.InputStream in = candidate.getResourceAsStream(resource)) {
                if (in == null) {
                    continue;
                }
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(8192);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                return out.toByteArray();
            }
        }
        // Last resort: read the entry straight out of the launch classpath.
        for (String entry : System.getProperty("java.class.path", "").split(java.io.File.pathSeparator)) {
            if (entry.isEmpty()) {
                continue;
            }
            java.io.File file = new java.io.File(entry);
            if (!file.isFile()) {
                continue;
            }
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(file)) {
                java.util.zip.ZipEntry found = zip.getEntry(resource);
                if (found == null) {
                    continue;
                }
                try (java.io.InputStream in = zip.getInputStream(found)) {
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(8192);
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                    return out.toByteArray();
                }
            } catch (Throwable ignored) {
                // not a readable archive
            }
        }
        // Last resort: resolve the class through the game loader and read its bytes from whichever
        // loader actually defines it.
        try {
            ClassLoader game = net.weaver.mixin.core.MixinEngine.get().resources().gameClassLoader();
            if (game != null) {
                Class<?> resolved = Class.forName(name, false, game);
                ClassLoader defining = resolved.getClassLoader();
                if (defining != null) {
                    try (java.io.InputStream in = defining.getResourceAsStream(resource)) {
                        if (in != null) {
                            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(8192);
                            byte[] buf = new byte[8192];
                            int n;
                            while ((n = in.read(buf)) > 0) {
                                out.write(buf, 0, n);
                            }
                            return out.toByteArray();
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // not resolvable here either
        }
        return null;
    }
}
