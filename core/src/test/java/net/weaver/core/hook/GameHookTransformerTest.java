package net.weaver.core.hook;

import net.weaver.api.event.EventBus;
import net.weaver.api.event.ListenerHandle;
import net.weaver.api.event.events.LevelLoadEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameHookTransformerTest
{
    private static final String FIXTURE_INTERNAL = "net/weaver/core/hook/HookFixture";
    private static final String FIXTURE_DOTTED = "net.weaver.core.hook.HookFixture";
    private static final String BEHAVIOR_INTERNAL = "net/weaver/core/hook/HookBehaviorFixture";
    private static final String BEHAVIOR_DOTTED = "net.weaver.core.hook.HookBehaviorFixture";
    private static final String TEST_HOOKS_OWNER = "net/weaver/core/hook/HookTestHooks";
    private static final String ARGS_INTERNAL = "net/weaver/core/hook/HookArgsFixture";
    private static final String ARGS_DOTTED = "net.weaver.core.hook.HookArgsFixture";
    private static final String ARGS_HOOKS_OWNER = "net/weaver/core/hook/HookArgsHooks";

    @BeforeEach
    void resetHooks()
    {
        HookTestHooks.reset();
    }

    @Test
    void injectsEntryAndExitCallsAndPreservesReturnValues() throws Exception
    {
        List<HookSpec> specs = List.of(
                HookSpec.method(FIXTURE_INTERNAL, "voidMethod", "()V")
                        .entry("levelLoad", HookSpec.Arg.THIS)
                        .exit("levelLoad", HookSpec.Arg.THIS)
                        .build(),
                HookSpec.method(FIXTURE_INTERNAL, "intMethod", "()I")
                        .entry("levelLoad", HookSpec.Arg.THIS)
                        .exit("levelLoad", HookSpec.Arg.THIS)
                        .build(),
                HookSpec.method(FIXTURE_INTERNAL, "longMethod", "()J")
                        .entry("levelLoad", HookSpec.Arg.THIS)
                        .exit("levelLoad", HookSpec.Arg.THIS)
                        .build(),
                HookSpec.method(FIXTURE_INTERNAL, "<init>", HookSpec.ANY_DESCRIPTOR)
                        .entry("levelLoad", HookSpec.Arg.THIS)
                        .exit("levelLoad", HookSpec.Arg.THIS)
                        .build());

        byte[] original = fixtureBytes(FIXTURE_INTERNAL);
        byte[] transformed = GameHookTransformer.apply(FIXTURE_INTERNAL, specs, original);

        assertFalse(Arrays.equals(original, transformed), "fixture should have been instrumented");
        assertEquals(2, invokeStaticCount(transformed, "voidMethod", "()V"));
        assertEquals(2, invokeStaticCount(transformed, "intMethod", "()I"));
        assertEquals(2, invokeStaticCount(transformed, "longMethod", "()J"));
        assertEquals(2, invokeStaticCount(transformed, "<init>", "()V"));
        assertPreservedReturn(transformed, "intMethod", "()I", Opcodes.DUP, Opcodes.POP,
                Opcodes.IRETURN);
        assertPreservedReturn(transformed, "longMethod", "()J", Opcodes.DUP2, Opcodes.POP2,
                Opcodes.LRETURN);

        AtomicInteger events = new AtomicInteger();
        ListenerHandle handle = EventBus.GAME.addListener(LevelLoadEvent.class,
                event -> events.incrementAndGet());
        try
        {
            Class<?> type = new FixtureLoader(FIXTURE_DOTTED, transformed).loadFixture();
            Object instance = type.getDeclaredConstructor().newInstance();
            assertEquals(2, events.get(), "constructor entry and exit must both fire");

            Method voidMethod = type.getMethod("voidMethod");
            voidMethod.invoke(instance);
            assertEquals(4, events.get());

            Method intMethod = type.getMethod("intMethod");
            assertEquals(42, ((Integer) intMethod.invoke(instance)).intValue());
            assertEquals(6, events.get());

            Method longMethod = type.getMethod("longMethod");
            assertEquals(1234567890123L, ((Long) longMethod.invoke(instance)).longValue());
            assertEquals(8, events.get());
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void cancelIfFalseSkipsTheVoidBodyWhenTheHookReturnsFalse() throws Exception
    {
        Class<?> type = loadBehavior(behaviorSpecs());
        Object instance = type.getDeclaredConstructor().newInstance();
        Method method = type.getMethod("cancelVoid");
        Field calls = type.getField("calls");

        HookTestHooks.allowed = true;
        method.invoke(instance);
        assertEquals(1, calls.getInt(instance));

        HookTestHooks.allowed = false;
        method.invoke(instance);
        assertEquals(1, calls.getInt(instance), "a false action call must skip the body");
    }

    @Test
    void cancelIfFalseReturnsTheDefaultBooleanValue() throws Exception
    {
        Class<?> type = loadBehavior(behaviorSpecs());
        Object instance = type.getDeclaredConstructor().newInstance();
        Method method = type.getMethod("cancelBoolean");
        Field calls = type.getField("calls");

        HookTestHooks.allowed = true;
        assertEquals(true, method.invoke(instance));
        assertEquals(1, calls.getInt(instance));

        HookTestHooks.allowed = false;
        assertEquals(false, method.invoke(instance));
        assertEquals(1, calls.getInt(instance), "a false action call must skip the body");
    }

    @Test
    void cancelIfFalseIsRejectedOnConstructors()
    {
        assertThrows(IllegalStateException.class,
                () -> HookSpec.method(BEHAVIOR_INTERNAL, "<init>", HookSpec.ANY_DESCRIPTOR)
                        .action(HookSpec.Action.CANCEL_IF_FALSE)
                        .entry("allow", HookSpec.Arg.THIS)
                        .build());
    }

    @Test
    void returnValueReplacesTheOriginalResult() throws Exception
    {
        Class<?> type = loadBehavior(behaviorSpecs());
        Object instance = type.getDeclaredConstructor().newInstance();
        Method method = type.getMethod("replace");
        Field calls = type.getField("calls");

        HookTestHooks.replacement = "hooked";
        assertEquals("hooked", method.invoke(instance));
        assertEquals(0, calls.getInt(instance), "a returning action skips the original body");
    }

    @Test
    void returnValueNullFallsThroughToTheOriginalBody() throws Exception
    {
        Class<?> type = loadBehavior(behaviorSpecs());
        Object instance = type.getDeclaredConstructor().newInstance();
        Method method = type.getMethod("replace");
        Field calls = type.getField("calls");

        HookTestHooks.replacement = null;
        assertEquals("original", method.invoke(instance));
        assertEquals(1, calls.getInt(instance), "a null action result must run the original body");
    }

    @Test
    void fiveArgumentEntryHooksLoadEveryArgumentInOrder() throws Exception
    {
        HookArgsHooks.LAST = null;
        Object first = new Object();
        Object second = new Object();
        Object third = new Object();
        Object fourth = new Object();
        Object fifth = new Object();
        List<HookSpec> specs = List.of(
                HookSpec.method(ARGS_INTERNAL, "five",
                                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;"
                                        + "Ljava/lang/Object;Ljava/lang/Object;)V")
                        .entry("record", HookSpec.Arg.ARG0, HookSpec.Arg.ARG1,
                                HookSpec.Arg.ARG2, HookSpec.Arg.ARG3, HookSpec.Arg.ARG4)
                        .build());

        byte[] original = fixtureBytes(ARGS_INTERNAL);
        byte[] transformed = GameHookTransformer.apply(ARGS_INTERNAL, specs, original,
                ARGS_HOOKS_OWNER, GameHookTransformerTest.class.getClassLoader());

        assertFalse(Arrays.equals(original, transformed), "fixture should have been instrumented");
        Class<?> type = new FixtureLoader(ARGS_DOTTED, transformed).loadFixture();
        Object instance = type.getDeclaredConstructor().newInstance();
        type.getMethod("five", Object.class, Object.class, Object.class, Object.class,
                        Object.class)
                .invoke(instance, first, second, third, fourth, fifth);

        assertNotNull(HookArgsHooks.LAST);
        assertEquals(5, HookArgsHooks.LAST.length);
        assertSame(first, HookArgsHooks.LAST[0]);
        assertSame(second, HookArgsHooks.LAST[1]);
        assertSame(third, HookArgsHooks.LAST[2]);
        assertSame(fourth, HookArgsHooks.LAST[3]);
        assertSame(fifth, HookArgsHooks.LAST[4]);

        Object[] seen = (Object[]) type.getField("seen").get(instance);
        assertNotNull(seen, "the original body must still run after a void entry call");
        assertSame(first, seen[0]);
        assertSame(second, seen[1]);
        assertSame(third, seen[2]);
        assertSame(fourth, seen[3]);
        assertSame(fifth, seen[4]);
    }

    @Test
    void throwHookObservesTheExceptionWithoutReplacingIt() throws Exception
    {
        Class<?> type = loadBehavior(behaviorSpecs());
        Object instance = type.getDeclaredConstructor().newInstance();
        Method method = type.getMethod("throwing");
        Field calls = type.getField("calls");

        InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                () -> method.invoke(instance));

        assertSame(HookTestHooks.ERROR, failure.getCause(),
                "the original exception instance must propagate unchanged");
        assertEquals("boom", ((IllegalStateException) failure.getCause()).getMessage());
        assertEquals(1, HookTestHooks.THROWS.get());
        assertEquals(0, HookTestHooks.EXITS.get(), "exit hooks must not run on a throw path");
        assertEquals(1, calls.getInt(instance));
    }

    @Test
    void entryCallsRunInPriorityOrder() throws Exception
    {
        List<HookSpec> specs = List.of(
                HookSpec.method(BEHAVIOR_INTERNAL, "ordered", "()V")
                        .priority(10)
                        .entry("recordLate", HookSpec.Arg.THIS)
                        .build(),
                HookSpec.method(BEHAVIOR_INTERNAL, "ordered", "()V")
                        .priority(-5)
                        .entry("recordEarly", HookSpec.Arg.THIS)
                        .build());
        Class<?> type = loadBehavior(specs);
        Object instance = type.getDeclaredConstructor().newInstance();

        type.getMethod("ordered").invoke(instance);

        assertEquals(List.of("early", "late", "body"), HookTestHooks.ORDER);
    }

    private static List<HookSpec> behaviorSpecs()
    {
        return List.of(
                HookSpec.method(BEHAVIOR_INTERNAL, "cancelVoid", "()V")
                        .action(HookSpec.Action.CANCEL_IF_FALSE)
                        .entry("allow", HookSpec.Arg.THIS)
                        .build(),
                HookSpec.method(BEHAVIOR_INTERNAL, "cancelBoolean", "()Z")
                        .action(HookSpec.Action.CANCEL_IF_FALSE)
                        .entry("allow", HookSpec.Arg.THIS)
                        .build(),
                HookSpec.method(BEHAVIOR_INTERNAL, "replace", "()Ljava/lang/String;")
                        .action(HookSpec.Action.RETURN_VALUE)
                        .entry("replacement", HookSpec.Arg.THIS)
                        .build(),
                HookSpec.method(BEHAVIOR_INTERNAL, "throwing", "()V")
                        .exit("recordExit", HookSpec.Arg.THIS)
                        .throwHook("recordThrow", HookSpec.Arg.THIS)
                        .build());
    }

    private static Class<?> loadBehavior(List<HookSpec> specs) throws Exception
    {
        byte[] original = fixtureBytes(BEHAVIOR_INTERNAL);
        byte[] transformed = GameHookTransformer.apply(BEHAVIOR_INTERNAL, specs, original,
                TEST_HOOKS_OWNER, GameHookTransformerTest.class.getClassLoader());
        assertFalse(Arrays.equals(original, transformed), "fixture should have been instrumented");
        return new FixtureLoader(BEHAVIOR_DOTTED, transformed).loadFixture();
    }

    private static byte[] fixtureBytes(String internalName) throws Exception
    {
        try (InputStream in = GameHookTransformerTest.class
                .getResourceAsStream("/" + internalName + ".class"))
        {
            assertNotNull(in, "fixture class bytes missing: " + internalName);
            return in.readAllBytes();
        }
    }

    private static int invokeStaticCount(byte[] bytes, String methodName, String descriptor)
    {
        AtomicInteger count = new AtomicInteger();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9)
        {
            @Override
            public MethodVisitor visitMethod(int access, String name, String methodDescriptor,
                                             String signature, String[] exceptions)
            {
                if (!methodName.equals(name) || !descriptor.equals(methodDescriptor))
                {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9)
                {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String callName,
                                                String callDescriptor, boolean isInterface)
                    {
                        if (opcode == Opcodes.INVOKESTATIC)
                        {
                            count.incrementAndGet();
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return count.get();
    }

    private static void assertPreservedReturn(byte[] bytes, String methodName, String descriptor,
                                              int duplicate, int pop, int returnOpcode)
    {
        List<Integer> ops = opcodes(bytes, methodName, descriptor);
        int ret = ops.lastIndexOf(returnOpcode);
        assertTrue(ret >= 0, methodName + " should return");
        assertEquals(duplicate, ops.get(ret - 4));
        assertEquals(Opcodes.ALOAD, ops.get(ret - 3));
        assertEquals(Opcodes.INVOKESTATIC, ops.get(ret - 2));
        assertEquals(pop, ops.get(ret - 1));
    }

    private static List<Integer> opcodes(byte[] bytes, String methodName, String descriptor)
    {
        List<Integer> ops = new ArrayList<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9)
        {
            @Override
            public MethodVisitor visitMethod(int access, String name, String methodDescriptor,
                                             String signature, String[] exceptions)
            {
                if (!methodName.equals(name) || !descriptor.equals(methodDescriptor))
                {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9)
                {
                    @Override
                    public void visitInsn(int opcode)
                    {
                        ops.add(opcode);
                    }

                    @Override
                    public void visitIntInsn(int opcode, int operand)
                    {
                        ops.add(opcode);
                    }

                    @Override
                    public void visitVarInsn(int opcode, int varIndex)
                    {
                        ops.add(opcode);
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String callName,
                                                String callDescriptor, boolean isInterface)
                    {
                        ops.add(opcode);
                    }

                    @Override
                    public void visitLdcInsn(Object value)
                    {
                        ops.add(Opcodes.LDC);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return ops;
    }

    private static final class FixtureLoader extends ClassLoader
    {
        private final String dottedName;
        private final byte[] bytes;

        private FixtureLoader(String dottedName, byte[] bytes)
        {
            super(GameHookTransformerTest.class.getClassLoader());
            this.dottedName = dottedName;
            this.bytes = bytes;
        }

        private Class<?> loadFixture()
        {
            Class<?> loaded = findLoadedClass(dottedName);
            if (loaded == null)
            {
                loaded = defineClass(dottedName, bytes, 0, bytes.length);
            }
            resolveClass(loaded);
            return loaded;
        }
    }
}
