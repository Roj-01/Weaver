package net.weaver.core.hook;

import net.weaver.core.LifecycleHooks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HookGuardrailTest
{
    private static final String GUARD_INTERNAL = "net/weaver/core/hook/HookGuardFixture";
    private static final String GUARD_DOTTED = "net.weaver.core.hook.HookGuardFixture";
    private static final String GUARD_HOOKS_OWNER = "net/weaver/core/hook/HookGuardHooks";

    @TempDir
    Path tempDir;

    @BeforeEach
    void resetHooks()
    {
        HookGuardHooks.reset();
    }

    @AfterEach
    void tearDown()
    {
        HookGuardHooks.reset();
        LifecycleHooks.reset();
    }

    @Test
    void nonVoidActionOnAStaticTargetIsInvalidAndNotApplied() throws Exception
    {
        HookSpec staticSpec = HookSpec.method(GUARD_INTERNAL, "statically", "()V")
                .action(HookSpec.Action.CANCEL_IF_FALSE)
                .entry("allow")
                .build();
        HookSpec instanceSpec = HookSpec.method(GUARD_INTERNAL, "instance", "()V")
                .action(HookSpec.Action.CANCEL_IF_FALSE)
                .entry("allow")
                .build();

        byte[] original = fixtureBytes(GUARD_INTERNAL);
        byte[] transformed = GameHookTransformer.apply(GUARD_INTERNAL,
                List.of(staticSpec, instanceSpec), original, GUARD_HOOKS_OWNER,
                HookGuardrailTest.class.getClassLoader());

        assertNotNull(transformed);
        assertFalse(Arrays.equals(original, transformed));
        assertEquals("INVALID", GameHookTransformer.statusOf(staticSpec));
        assertEquals("APPLIED", GameHookTransformer.statusOf(instanceSpec));

        Class<?> type = new FixtureLoader(GUARD_DOTTED, transformed).loadFixture();
        type.getMethod("statically").invoke(null);
        assertEquals(1, HookGuardHooks.STATIC_BODIES.get(),
                "an invalid static action must not cancel the body");
        assertEquals(0, HookGuardHooks.ALLOW_CHECKS.get(),
                "an invalid static action must not inject a hook call");

        Object instance = type.getDeclaredConstructor().newInstance();
        type.getMethod("instance").invoke(instance);
        assertEquals(0, type.getField("calls").getInt(instance),
                "the valid instance action must cancel the body");
        assertEquals(1, HookGuardHooks.ALLOW_CHECKS.get());
        assertEquals(1, type.getField("constructed").getInt(instance));
    }

    @Test
    void nonVoidActionOnAConstructorIsInvalidAndFailSoft() throws Exception
    {
        HookSpec spec = HookSpec.testUnsafe(GUARD_INTERNAL, "<init>", "()V",
                HookSpec.Action.CANCEL_IF_FALSE,
                List.of(new HookSpec.HookCall("allow", List.of())),
                List.of(), List.of());

        byte[] original = fixtureBytes(GUARD_INTERNAL);
        byte[] transformed = GameHookTransformer.apply(GUARD_INTERNAL, List.of(spec), original,
                GUARD_HOOKS_OWNER, HookGuardrailTest.class.getClassLoader());

        assertNotNull(transformed);
        assertEquals("INVALID", GameHookTransformer.statusOf(spec));

        Class<?> type = new FixtureLoader(GUARD_DOTTED, transformed).loadFixture();
        Object instance = type.getDeclaredConstructor().newInstance();
        assertEquals(1, type.getField("constructed").getInt(instance),
                "an invalid constructor action must not skip the constructor body");
        assertEquals(0, HookGuardHooks.ALLOW_CHECKS.get());
    }

    @Test
    void constructorThrowHooksAreInvalidAndFailSoft() throws Exception
    {
        HookSpec spec = HookSpec.testUnsafe(GUARD_INTERNAL, "<init>", HookSpec.ANY_DESCRIPTOR,
                HookSpec.Action.VOID, List.of(), List.of(),
                List.of(new HookSpec.HookCall("allow", List.of())));

        byte[] original = fixtureBytes(GUARD_INTERNAL);
        byte[] transformed = GameHookTransformer.apply(GUARD_INTERNAL, List.of(spec), original,
                GUARD_HOOKS_OWNER, HookGuardrailTest.class.getClassLoader());

        assertNotNull(transformed);
        assertEquals("INVALID", GameHookTransformer.statusOf(spec));
        assertEquals("constructor throw hooks are not supported",
                GameHookTransformer.validateForTest(spec, Opcodes.ACC_PUBLIC, "()V"));
    }

    @Test
    void validationReportsPreciseReasonsForBypassedSpecs()
    {
        HookSpec staticAction = HookSpec.method(GUARD_INTERNAL, "statically", "()V")
                .action(HookSpec.Action.CANCEL_IF_FALSE)
                .entry("allow")
                .build();
        HookSpec constructorAction = HookSpec.testUnsafe(GUARD_INTERNAL, "<init>", "()V",
                HookSpec.Action.CANCEL_IF_FALSE,
                List.of(new HookSpec.HookCall("allow", List.of())), List.of(), List.of());
        HookSpec constructorThrow = HookSpec.testUnsafe(GUARD_INTERNAL, "<init>", "()V",
                HookSpec.Action.VOID, List.of(), List.of(),
                List.of(new HookSpec.HookCall("allow", List.of())));

        assertEquals("action CANCEL_IF_FALSE on a static target",
                GameHookTransformer.validateForTest(staticAction,
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "()V"));
        assertEquals("action CANCEL_IF_FALSE on a constructor",
                GameHookTransformer.validateForTest(constructorAction, Opcodes.ACC_PUBLIC, "()V"));
        assertEquals("constructor throw hooks are not supported",
                GameHookTransformer.validateForTest(constructorThrow, Opcodes.ACC_PUBLIC, "()V"));
        assertNull(GameHookTransformer.validateForTest(staticAction, Opcodes.ACC_PUBLIC, "()V"));
    }

    @Test
    void argumentValidationStillReportsPreciseReasons()
    {
        HookSpec primitiveArgument = HookSpec.method(GUARD_INTERNAL, "instance", "(I)V")
                .entry("allow", HookSpec.Arg.ARG0)
                .build();
        HookSpec thisOnStatic = HookSpec.method(GUARD_INTERNAL, "statically", "()V")
                .entry("allow", HookSpec.Arg.THIS)
                .build();

        assertEquals("entry allow arg=ARG0 is not a reference",
                GameHookTransformer.validateForTest(primitiveArgument, Opcodes.ACC_PUBLIC, "(I)V"));
        assertEquals("entry allow arg=THIS on a static target",
                GameHookTransformer.validateForTest(thisOnStatic,
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "()V"));
    }

    @Test
    void invalidStaticActionIsNotedWithTheReason() throws Exception
    {
        List<String> lines = new ArrayList<>();
        LifecycleHooks.install(List.of(), getClass().getClassLoader(), "server", false,
                tempDir.resolve("config"), tempDir.resolve("data"), lines::add);

        HookSpec spec = HookSpec.method(GUARD_INTERNAL, "statically", "()V")
                .id("guard.invalid.static")
                .action(HookSpec.Action.CANCEL_IF_FALSE)
                .entry("allow")
                .build();

        byte[] original = fixtureBytes(GUARD_INTERNAL);
        byte[] transformed = GameHookTransformer.apply(GUARD_INTERNAL, List.of(spec), original,
                GUARD_HOOKS_OWNER, HookGuardrailTest.class.getClassLoader());

        assertNotNull(transformed);
        assertEquals("INVALID", GameHookTransformer.statusOf(spec));
        assertTrue(lines.stream().anyMatch(line -> line.contains("GAME HOOK INVALID id=guard.invalid.static")
                        && line.contains("reason=action CANCEL_IF_FALSE on a static target")),
                () -> String.join(System.lineSeparator(), lines));
    }

    private static byte[] fixtureBytes(String internalName) throws Exception
    {
        try (InputStream in = HookGuardrailTest.class
                .getResourceAsStream("/" + internalName + ".class"))
        {
            assertNotNull(in, "fixture class bytes missing: " + internalName);
            return in.readAllBytes();
        }
    }

    private static final class FixtureLoader extends ClassLoader
    {
        private final String dottedName;
        private final byte[] bytes;

        private FixtureLoader(String dottedName, byte[] bytes)
        {
            super(HookGuardrailTest.class.getClassLoader());
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
