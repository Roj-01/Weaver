package net.weaver.core.lifecycle;

import net.weaver.core.LifecycleHooks;
import net.weaver.core.LifecycleInjection;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/** Calls the client-setup phase before the client creates its options. */
public final class ClientInitTransform implements ClassFileTransformer {

    public static final String TARGET = "net/minecraft/client/Minecraft";
    private static final String TARGET_INTERNAL = "net/minecraft/client/Minecraft";
    private static final String OPTIONS = "net/minecraft/client/Options";
    private static final String TARGET_DOTTED = "net.minecraft.client.Minecraft";
    private static final String OPTIONS_DOTTED = "net.minecraft.client.Options";
    private static final String HOOKS = "net/weaver/core/LifecycleHooks";

    private final ClassLoader gameLoader;

    ClientInitTransform(ClassLoader gameLoader) {
        this.gameLoader = gameLoader;
    }

    public static void install(Instrumentation instrumentation, ClassLoader gameLoader) {
        instrumentation.addTransformer(new ClientInitTransform(gameLoader), false);
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (!TARGET_INTERNAL.equals(className) || !isHostLoader(loader)) {
            return null;
        }
        try {
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            boolean[] injected = new boolean[1];
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (method == null || !"<init>".equals(name)) {
                        return method;
                    }
                    return new MethodVisitor(Opcodes.ASM9, method) {
                        @Override
                        public void visitTypeInsn(int opcode, String type) {
                            if (opcode == Opcodes.NEW && OPTIONS.equals(type)) {
                                injected[0] = true;
                                visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "onClientSetup", "()V", false);
                            }
                            super.visitTypeInsn(opcode, type);
                        }
                    };
                }
            };
            reader.accept(visitor, ClassReader.EXPAND_FRAMES);
            if (!injected[0]) {
                LifecycleInjection.missing(TARGET_DOTTED + "#<init>(new " + OPTIONS_DOTTED + ")");
            }
            return writer.toByteArray();
        } catch (Throwable failure) {
            LifecycleHooks.logLine("[weaver] CLIENT TRANSFORM FAILED reason=" + failure);
            LifecycleInjection.missing(TARGET_DOTTED + "#transform-failed");
            return null;
        }
    }

    private boolean isHostLoader(ClassLoader loader) {
        return loader == null || loader == gameLoader;
    }
}

