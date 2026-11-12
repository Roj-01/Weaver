package net.weaver.core.lifecycle;

import net.weaver.core.LifecycleHooks;
import net.weaver.core.LifecycleInjection;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.AdviceAdapter;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/** Calls the server-setup phase when the server starts running. */
public final class ServerInitTransform implements ClassFileTransformer {

    public static final String TARGET = "net/minecraft/server/MinecraftServer";
    private static final String TARGET_INTERNAL = "net/minecraft/server/MinecraftServer";
    private static final String TARGET_DOTTED = "net.minecraft.server.MinecraftServer";
    private static final String HOOKS = "net/weaver/core/LifecycleHooks";

    private final ClassLoader gameLoader;

    ServerInitTransform(ClassLoader gameLoader) {
        this.gameLoader = gameLoader;
    }

    public static void install(Instrumentation instrumentation, ClassLoader gameLoader) {
        instrumentation.addTransformer(new ServerInitTransform(gameLoader), false);
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
                    if (method == null || !"runServer".equals(name) || !"()V".equals(descriptor)) {
                        return method;
                    }
                    injected[0] = true;
                    return new AdviceAdapter(Opcodes.ASM9, method, access, name, descriptor) {
                        @Override
                        protected void onMethodEnter() {
                            visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "onServerSetup", "()V", false);
                        }
                    };
                }
            };
            reader.accept(visitor, ClassReader.EXPAND_FRAMES);
            if (!injected[0]) {
                LifecycleInjection.missing(TARGET_DOTTED + "#runServer()V");
            }
            return writer.toByteArray();
        } catch (Throwable failure) {
            LifecycleHooks.logLine("[weaver] SERVER TRANSFORM FAILED reason=" + failure);
            LifecycleInjection.missing(TARGET_DOTTED + "#transform-failed");
            return null;
        }
    }

    private boolean isHostLoader(ClassLoader loader) {
        return loader == null || loader == gameLoader;
    }
}

