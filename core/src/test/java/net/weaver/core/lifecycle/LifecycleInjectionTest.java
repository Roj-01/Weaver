package net.weaver.core.lifecycle;

import net.weaver.core.LifecycleInjection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleInjectionTest {

    private final List<String> lines = new ArrayList<>();

    @BeforeEach
    void setUp() {
        System.clearProperty(LifecycleInjection.PROPERTY);
        LifecycleInjection.resetForTests();
        LifecycleInjection.setLoggerForTests(lines::add);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(LifecycleInjection.PROPERTY);
        LifecycleInjection.resetForTests();
        LifecycleInjection.setLoggerForTests(null);
    }

    @Test
    void registryTransformReportsRenamedBootstrapAndFreeze() {
        byte[] bytes = type("net/minecraft/core/registries/BuiltInRegistries",
                "bootStrapRenamed", "freezeRenamed");

        new RegistryTransform(null).transform(null,
                "net/minecraft/core/registries/BuiltInRegistries", null, null, bytes);

        assertTrue(LifecycleInjection.missingMethods().contains(
                        "net.minecraft.core.registries.BuiltInRegistries#bootStrap()V"),
                LifecycleInjection.missingMethods().toString());
        assertTrue(LifecycleInjection.missingMethods().contains(
                        "net.minecraft.core.registries.BuiltInRegistries#freeze()V"),
                LifecycleInjection.missingMethods().toString());
        assertTrue(lines.stream().anyMatch(line -> line.equals(
                        "[weaver] FATAL LIFECYCLE INJECTION MISSING method="
                                + "net.minecraft.core.registries.BuiltInRegistries#bootStrap()V")),
                String.join("\n", lines));
        assertThrows(IllegalStateException.class, LifecycleInjection::enforce);
    }

    @Test
    void clientTransformReportsMissingOptionsConstruction() {
        byte[] bytes = type("net/minecraft/client/Minecraft", new String[0]);

        new ClientInitTransform(null).transform(null, "net/minecraft/client/Minecraft",
                null, null, bytes);

        assertTrue(LifecycleInjection.missingMethods().stream()
                        .anyMatch(method -> method.contains("net.minecraft.client.Minecraft#<init>")),
                LifecycleInjection.missingMethods().toString());
        assertTrue(lines.stream().anyMatch(line -> line.startsWith(
                "[weaver] FATAL LIFECYCLE INJECTION MISSING method=")),
                String.join("\n", lines));
    }

    @Test
    void serverTransformReportsMissingRunServer() {
        byte[] bytes = type("net/minecraft/server/MinecraftServer", "runServerRenamed");

        new ServerInitTransform(null).transform(null, "net/minecraft/server/MinecraftServer",
                null, null, bytes);

        assertTrue(LifecycleInjection.missingMethods().contains(
                        "net.minecraft.server.MinecraftServer#runServer()V"),
                LifecycleInjection.missingMethods().toString());
        assertTrue(lines.stream().anyMatch(line -> line.equals(
                        "[weaver] FATAL LIFECYCLE INJECTION MISSING method="
                                + "net.minecraft.server.MinecraftServer#runServer()V")),
                String.join("\n", lines));
    }

    @Test
    void warnPolicyLogsWithoutStopping() {
        System.setProperty(LifecycleInjection.PROPERTY, "warn");
        byte[] bytes = type("net/minecraft/server/MinecraftServer", "runServerRenamed");

        new ServerInitTransform(null).transform(null, "net/minecraft/server/MinecraftServer",
                null, null, bytes);

        assertFalse(LifecycleInjection.missingMethods().isEmpty());
        assertDoesNotThrow(LifecycleInjection::enforce);
        assertTrue(lines.stream().anyMatch(line -> line.startsWith(
                "[weaver] FATAL LIFECYCLE INJECTION MISSING method=")),
                String.join("\n", lines));
    }

    @Test
    void aTransformThatThrowsIsRecordedAsAMissForEveryLifecycleTarget() {
        byte[] garbage = {1, 2, 3, 4};

        new RegistryTransform(null).transform(null,
                "net/minecraft/core/registries/BuiltInRegistries", null, null, garbage);
        new ClientInitTransform(null).transform(null, "net/minecraft/client/Minecraft",
                null, null, garbage);
        new ServerInitTransform(null).transform(null, "net/minecraft/server/MinecraftServer",
                null, null, garbage);

        assertTrue(LifecycleInjection.missingMethods().containsAll(java.util.Set.of(
                        "net.minecraft.core.registries.BuiltInRegistries#transform-failed",
                        "net.minecraft.client.Minecraft#transform-failed",
                        "net.minecraft.server.MinecraftServer#transform-failed")),
                LifecycleInjection.missingMethods().toString());
        assertTrue(lines.stream().anyMatch(line -> line.equals(
                        "[weaver] FATAL LIFECYCLE INJECTION MISSING method="
                                + "net.minecraft.server.MinecraftServer#transform-failed")),
                String.join("\n", lines));
        assertThrows(IllegalStateException.class, LifecycleInjection::enforce);
    }

    private static byte[] type(String internalName, String... methods) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null,
                "java/lang/Object", null);

        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V",
                null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>",
                "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();

        for (String method : methods) {
            MethodVisitor visitor = writer.visitMethod(Opcodes.ACC_PUBLIC, method, "()V",
                    null, null);
            visitor.visitCode();
            visitor.visitInsn(Opcodes.RETURN);
            visitor.visitMaxs(0, 1);
            visitor.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }
}
