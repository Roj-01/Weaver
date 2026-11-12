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
import java.util.ArrayList;
import java.util.List;

/** Calls the initialize, register, and common-setup phases from the registry bootstrap. */
public final class RegistryTransform implements ClassFileTransformer {

    public static final String TARGET = "net/minecraft/core/registries/BuiltInRegistries";
    private static final String TARGET_INTERNAL = "net/minecraft/core/registries/BuiltInRegistries";
    private static final String TARGET_DOTTED = "net.minecraft.core.registries.BuiltInRegistries";
    private static final String HOOKS = "net/weaver/core/LifecycleHooks";

    private final ClassLoader gameLoader;

    RegistryTransform(ClassLoader gameLoader) {
        this.gameLoader = gameLoader;
    }

    public static void install(Instrumentation instrumentation, ClassLoader gameLoader) {
        instrumentation.addTransformer(new RegistryTransform(gameLoader), false);
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
            boolean[] injected = new boolean[2];
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (method == null) {
                        return null;
                    }
                    if ("bootStrap".equals(name) && "()V".equals(descriptor)) {
                        injected[0] = true;
                        return new AdviceAdapter(Opcodes.ASM9, method, access, name, descriptor) {
                            @Override
                            protected void onMethodEnter() {
                                visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "onInitialize", "()V", false);
                            }

                            @Override
                            protected void onMethodExit(int opcode) {
                                if (opcode == Opcodes.RETURN) {
                                    visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "onCommonSetup", "()V", false);
                                }
                            }
                        };
                    }
                    if ("freeze".equals(name) && "()V".equals(descriptor)) {
                        injected[1] = true;
                        return new AdviceAdapter(Opcodes.ASM9, method, access, name, descriptor) {
                            @Override
                            protected void onMethodEnter() {
                                visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "onRegister", "()V", false);
                            }
                        };
                    }
                    return method;
                }
            };
            reader.accept(visitor, ClassReader.EXPAND_FRAMES);
            List<String> missing = new ArrayList<>();
            if (!injected[0]) {
                missing.add(TARGET_DOTTED + "#bootStrap()V");
            }
            if (!injected[1]) {
                missing.add(TARGET_DOTTED + "#freeze()V");
            }
            LifecycleInjection.missing(missing);
            return writer.toByteArray();
        } catch (Throwable failure) {
            LifecycleHooks.logLine("[weaver] REGISTRY TRANSFORM FAILED reason=" + failure);
            LifecycleInjection.missing(TARGET_DOTTED + "#transform-failed");
            return null;
        }
    }

    private boolean isHostLoader(ClassLoader loader) {
        return loader == null || loader == gameLoader;
    }
}

