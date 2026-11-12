package net.weaver.core;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchDetectionTest {

    private static final String CLASS_NAME = "net/weaver/patched/PatchedGame";
    private static final String METHOD_NAME = "weaver$patchLevel";

    @Test
    void presentClassReportsItsPatchLevel() {
        PatchDetection.Result result = PatchDetection.classify(loader(levelClass(3), Map.of()));

        assertEquals(PatchDetection.Status.PATCHED, result.status());
        assertEquals(3, result.level());
        assertTrue(result.patched());
        assertFalse(result.unavailable());
        assertNull(result.reason());
        assertNull(result.patchSet());
    }

    @Test
    void absentClassReportsZero() {
        PatchDetection.Result result = PatchDetection.classify(loader(null, Map.of()));

        assertEquals(PatchDetection.Status.UNPATCHED, result.status());
        assertEquals(0, result.level());
        assertFalse(result.patched());
        assertNull(result.reason());
        assertNull(result.patchSet());
    }

    @Test
    void failedProbeReportsZeroWithReason() {
        PatchDetection.Result result = PatchDetection.classify(loader(throwingClass(), Map.of()));

        assertEquals(PatchDetection.Status.UNAVAILABLE, result.status());
        assertEquals(0, result.level());
        assertFalse(result.patched());
        assertTrue(result.unavailable());
        assertTrue(result.reason().contains("IllegalStateException"), result.reason());
    }

    @Test
    void metadataIsReadWhenMarkerClassIsPresent() {
        byte[] metadata = "{\"patchSet\":\"weaver-26.3-1\"}"
                .getBytes(StandardCharsets.UTF_8);
        PatchDetection.Result result = PatchDetection.classify(
                loader(levelClass(3), Map.of(PatchDetection.PATCH_METADATA, metadata)));

        assertEquals(PatchDetection.Status.PATCHED, result.status());
        assertEquals(3, result.level());
        assertEquals("weaver-26.3-1", result.patchSet());
    }

    @Test
    void instanceProbeUsesTheMetadataLevel() {
        byte[] metadata = "{\"patchLevel\":1,\"patchSet\":\"weaver-26.3-v1\"}"
                .getBytes(StandardCharsets.UTF_8);
        PatchDetection.Result result = PatchDetection.classify(
                loader(instanceLevelClass(1), Map.of(PatchDetection.PATCH_METADATA, metadata)));

        assertEquals(PatchDetection.Status.PATCHED, result.status());
        assertEquals(1, result.level());
        assertEquals("weaver-26.3-v1", result.patchSet());
    }

    @Test
    void instanceProbeWithoutMetadataIsUnavailable() {
        PatchDetection.Result result = PatchDetection.classify(
                loader(instanceLevelClass(1), Map.of()));

        assertEquals(PatchDetection.Status.UNAVAILABLE, result.status());
        assertEquals(0, result.level());
        assertTrue(result.reason().contains("not static"), result.reason());
    }

    private static ClassLoader loader(byte[] markerClass, Map<String, byte[]> resources) {
        return new FixtureLoader(markerClass, resources);
    }

    private static byte[] levelClass(int level) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                CLASS_NAME, null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, METHOD_NAME, "()I", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ICONST_0 + level);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] instanceLevelClass(int level) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                CLASS_NAME, null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC, METHOD_NAME, "()I", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ICONST_0 + level);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] throwingClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                CLASS_NAME, null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, METHOD_NAME, "()I", null, null);
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException");
        method.visitInsn(Opcodes.DUP);
        method.visitLdcInsn("probe-boom");
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException",
                "<init>", "(Ljava/lang/String;)V", false);
        method.visitInsn(Opcodes.ATHROW);
        method.visitMaxs(3, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static final class FixtureLoader extends ClassLoader {

        private final byte[] markerClass;
        private final Map<String, byte[]> resources;

        private FixtureLoader(byte[] markerClass, Map<String, byte[]> resources) {
            super(PatchDetectionTest.class.getClassLoader());
            this.markerClass = markerClass;
            this.resources = resources;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (CLASS_NAME.replace('/', '.').equals(name)) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        loaded = findClass(name);
                    }
                    if (resolve) {
                        resolveClass(loaded);
                    }
                    return loaded;
                }
            }
            return super.loadClass(name, resolve);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (CLASS_NAME.replace('/', '.').equals(name) && markerClass != null) {
                return defineClass(name, markerClass, 0, markerClass.length);
            }
            throw new ClassNotFoundException(name);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            byte[] bytes = resources.get(name);
            if (bytes != null) {
                return new ByteArrayInputStream(bytes);
            }
            return super.getResourceAsStream(name);
        }
    }
}
