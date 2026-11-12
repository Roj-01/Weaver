package net.weaver.core.hook;

import net.weaver.core.LifecycleHooks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Status bookkeeping of {@link GameHookTransformer}: pending, absent, late and failed specs. */
class GameHookStatusTest
{
    @TempDir
    Path tempDir;

    private final List<String> lines = new ArrayList<>();
    private String previousStrict;

    @BeforeEach
    void setUp()
    {
        previousStrict = System.getProperty("weaver.hooks.strict");
        LifecycleHooks.install(List.of(), getClass().getClassLoader(), "server", false,
                tempDir.resolve("config"), tempDir.resolve("data"), lines::add);
        GameHookTransformer.resetSummaryForTests();
    }

    @AfterEach
    void tearDown()
    {
        if (previousStrict == null)
        {
            System.clearProperty("weaver.hooks.strict");
        }
        else
        {
            System.setProperty("weaver.hooks.strict", previousStrict);
        }
        GameHookTransformer.resetSummaryForTests();
        LifecycleHooks.reset();
        GameHookTransformer.configureSides(true, true);
    }

    @Test
    void pendingSpecsAreCountedSeparatelyFromSkippedOnes()
    {
        HookSpec applied = spec("example.status.Applied");
        HookSpec skipped = spec("example.status.Skipped");
        HookSpec pending = spec("example.status.Pending");
        Map<HookSpec, String> statuses = new LinkedHashMap<>();
        statuses.put(applied, "APPLIED");
        statuses.put(skipped, "SKIPPED");
        statuses.put(pending, "PENDING");

        String line = GameHookTransformer.summaryLine(List.of(applied, skipped, pending), statuses);

        assertEquals("[weaver] GAME HOOK SUMMARY applied=1 skipped=1 missing=0 invalid=0"
                + " required_misses=none pending=1", line);
    }

    @Test
    void aRequiredSpecWhoseClassIsAbsentIsMissingAtInstall()
    {
        HookSpec absent = spec("example.status.NeverShipped");
        HookSpec optional = HookSpec.method("example.status.NeverShippedOptional", "run", "()V")
                .required(false)
                .entry("clientTickEnter")
                .build();
        HookSpec present = HookSpec.method("net.weaver.core.hook.HookGuardFixture", "instance", "()V")
                .entry("clientTickEnter")
                .build();

        GameHookTransformer.markAbsentTargets(List.of(absent, optional, present),
                getClass().getClassLoader());

        assertEquals("MISSING", GameHookTransformer.statusOf(absent));
        assertEquals("SKIPPED", GameHookTransformer.statusOf(optional));
        assertEquals("PENDING", GameHookTransformer.statusOf(present));
        assertTrue(lines.stream().anyMatch(line -> line.equals("[weaver] GAME HOOK MISSING id="
                + absent.id() + " reason=class-absent")), String.join("\n", lines));
    }

    @Test
    void aRequiredSpecThatGoesMissingAfterTheSummaryFailsStrictMode()
    {
        System.setProperty("weaver.hooks.strict", "true");
        GameHookTransformer.markSummaryLoggedForTests();
        String internal = "example/status/LateLoaded";
        HookSpec late = HookSpec.method(internal, "renamedAway", "()V")
                .entry("clientTickEnter")
                .build();

        GameHookTransformer.apply(internal, List.of(late), type(internal, "run", 1));

        assertEquals("MISSING", GameHookTransformer.statusOf(late));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith(
                "[weaver] GAME HOOK LATE MISSING id=" + late.id())), String.join("\n", lines));
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                GameHookTransformer::logSummaryOnce);
        assertTrue(failure.getMessage().contains(late.id()), failure.getMessage());
        assertTrue(lines.stream().anyMatch(line -> line.startsWith(
                "[weaver] GAME HOOK STRICT FAILURE required_misses=" + late.id())),
                String.join("\n", lines));
        assertDoesNotThrow(GameHookTransformer::logSummaryOnce, "a late miss is enforced once");
    }

    @Test
    void aLateMissWithoutStrictModeIsOnlyLogged()
    {
        GameHookTransformer.markSummaryLoggedForTests();
        String internal = "example/status/LateLenient";
        HookSpec late = HookSpec.method(internal, "renamedAway", "()V")
                .entry("clientTickEnter")
                .build();

        GameHookTransformer.apply(internal, List.of(late), type(internal, "run", 1));

        assertDoesNotThrow(GameHookTransformer::logSummaryOnce);
        assertTrue(lines.stream().anyMatch(line -> line.startsWith(
                "[weaver] GAME HOOK LATE MISSING id=" + late.id())), String.join("\n", lines));
    }

    @Test
    void aClassThatCannotBeWrittenIsNotReportedAsApplied()
    {
        String internal = "example/status/TooLarge";
        HookSpec spec = HookSpec.method(internal, "run", "()V")
                .entry("clientTickEnter")
                .exit("clientTickExit")
                .build();
        // The original method fits the 64 KiB code limit; the injected calls push it over.
        byte[] original = type(internal, "run", 65_532);

        assertThrows(RuntimeException.class,
                () -> GameHookTransformer.apply(internal, List.of(spec), original));

        assertEquals("INVALID", GameHookTransformer.statusOf(spec));
    }

    private static HookSpec spec(String className)
    {
        return HookSpec.method(className, "run", "()V")
                .entry("clientTickEnter")
                .build();
    }

    /** A class with one {@code ()V} method made of {@code nops} NOPs and a RETURN. */
    private static byte[] type(String internalName, String method, int nops)
    {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        MethodVisitor visitor = writer.visitMethod(Opcodes.ACC_PUBLIC, method, "()V", null, null);
        visitor.visitCode();
        for (int i = 0; i < nops; i++)
        {
            visitor.visitInsn(Opcodes.NOP);
        }
        visitor.visitInsn(Opcodes.RETURN);
        visitor.visitMaxs(0, 1);
        visitor.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
