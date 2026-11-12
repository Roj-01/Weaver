package net.weaver.core.hook;

import net.weaver.api.event.Side;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HookSpecTest {

    @Test
    void normalizesDottedClassNamesAndBuildsDescriptors() {
        HookSpec spec = HookSpec.method("net.minecraft.client.Minecraft", "tick", "()V")
                .entry("clientTickEnter")
                .exit("clientTickExit")
                .build();

        assertEquals("net/minecraft/client/Minecraft", spec.className());
        assertEquals("()V", spec.entryCalls().get(0).descriptor());
        assertEquals("()V", spec.exitCalls().get(0).descriptor());
        assertTrue(spec.matches("tick", "()V"));
        assertFalse(spec.matches("tick", "(Z)V"));
    }

    @Test
    void buildsObjectDescriptorsForArguments() {
        HookSpec spec = HookSpec.method("net.minecraft.server.players.PlayerList",
                        "placeNewPlayer",
                        "(Lnet/minecraft/network/Connection;"
                                + "Lnet/minecraft/server/level/ServerPlayer;"
                                + "Lnet/minecraft/server/network/CommonListenerCookie;)V")
                .entry("playerJoin", HookSpec.Arg.THIS, HookSpec.Arg.ARG1)
                .build();

        assertArrayEquals(new HookSpec.Arg[]{HookSpec.Arg.THIS, HookSpec.Arg.ARG1},
                spec.entryCalls().get(0).args().toArray());
        assertEquals("(Ljava/lang/Object;Ljava/lang/Object;)V",
                spec.entryCalls().get(0).descriptor());
    }

    @Test
    void fiveArgumentCallsExposeEveryArgument() {
        HookSpec spec = HookSpec.method("example.Target", "five",
                        "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;"
                                + "Ljava/lang/Object;Ljava/lang/Object;)V")
                .entry("record", HookSpec.Arg.ARG0, HookSpec.Arg.ARG1, HookSpec.Arg.ARG2,
                        HookSpec.Arg.ARG3, HookSpec.Arg.ARG4)
                .build();

        assertEquals(List.of(HookSpec.Arg.ARG0, HookSpec.Arg.ARG1, HookSpec.Arg.ARG2,
                HookSpec.Arg.ARG3, HookSpec.Arg.ARG4), spec.entryCalls().get(0).args());
        assertEquals("(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;"
                        + "Ljava/lang/Object;Ljava/lang/Object;)V",
                spec.entryCalls().get(0).descriptor());
    }

    @Test
    void wildcardDescriptorIsOnlyValidForConstructors() {
        HookSpec constructor = HookSpec.method("net.minecraft.commands.Commands", "<init>",
                        HookSpec.ANY_DESCRIPTOR)
                .exit("commandRegister", HookSpec.Arg.THIS)
                .build();

        assertTrue(constructor.anyDescriptor());
        assertTrue(constructor.matches("<init>", "(Ljava/lang/Object;)V"));

        assertThrows(IllegalArgumentException.class,
                () -> HookSpec.method("net.minecraft.commands.Commands", "tick",
                        HookSpec.ANY_DESCRIPTOR));
    }

    @Test
    void emptyHooksAreRejected() {
        assertThrows(IllegalStateException.class,
                () -> HookSpec.method("net.minecraft.client.Minecraft", "tick", "()V").build());
    }

    @Test
    void hooksAreRequiredByDefaultAndCanOptOut() {
        HookSpec required = HookSpec.method("net.minecraft.client.Minecraft", "tick", "()V")
                .exit("clientTickExit")
                .build();
        HookSpec optional = HookSpec.method("net.minecraft.client.Minecraft", "tick", "()V")
                .exit("clientTickExit")
                .required(false)
                .build();
        HookSpec explicitlyRequired = HookSpec.method("net.minecraft.client.Minecraft", "tick", "()V")
                .exit("clientTickExit")
                .required(true)
                .build();

        assertTrue(required.required());
        assertFalse(optional.required());
        assertTrue(explicitlyRequired.required());
    }

    @Test
    void exposesStableSpecId() {
        HookSpec spec = HookSpec.method("net.minecraft.server.MinecraftServer", "runServer", "()V")
                .exit("serverStopped", HookSpec.Arg.THIS)
                .build();

        assertEquals("net/minecraft/server/MinecraftServer#runServer()V", spec.id());
    }

    @Test
    void explicitIdOverridesTheDefault() {
        HookSpec spec = HookSpec.method("net.minecraft.server.MinecraftServer", "runServer", "()V")
                .id("server.run")
                .entry("clientTickExit")
                .build();

        assertEquals("server.run", spec.id());
        assertEquals("net/minecraft/server/MinecraftServer#runServer()V",
                HookSpec.method("net.minecraft.server.MinecraftServer", "runServer", "()V")
                        .entry("clientTickExit")
                        .build()
                        .id());
        assertThrows(IllegalArgumentException.class,
                () -> HookSpec.method("net.minecraft.server.MinecraftServer", "runServer", "()V")
                        .id("  ")
                        .entry("clientTickExit")
                        .build());
    }

    @Test
    void sidesDefaultToBothAndCanBeRestricted() {
        HookSpec both = HookSpec.method("net.minecraft.commands.Commands", "<init>",
                        HookSpec.ANY_DESCRIPTOR)
                .exit("commandRegister", HookSpec.Arg.THIS)
                .build();
        HookSpec server = HookSpec.method("net.minecraft.server.level.ServerLevel", "close", "()V")
                .side(Side.SERVER)
                .entry("levelUnload", HookSpec.Arg.THIS)
                .build();

        assertEquals(Side.BOTH, both.side());
        assertEquals(Side.SERVER, server.side());
    }

    @Test
    void priorityDefaultsToZeroAndSortsLowerFirst() {
        HookSpec defaultPriority = HookSpec.method("net.minecraft.client.Minecraft", "tick", "()V")
                .entry("clientTickEnter")
                .build();
        HookSpec early = HookSpec.method("net.minecraft.client.Minecraft", "tick", "()V")
                .priority(-10)
                .entry("clientTickEnter")
                .build();

        assertEquals(0, defaultPriority.priority());
        assertEquals(-10, early.priority());
    }

    @Test
    void throwHooksAreRecordedAndValidWithoutEntryOrExitCalls() {
        HookSpec spec = HookSpec.method("net.minecraft.client.Minecraft", "tick", "()V")
                .throwHook("clientTickExit", HookSpec.Arg.THIS)
                .build();

        assertEquals(1, spec.throwCalls().size());
        assertEquals("clientTickExit", spec.throwCalls().get(0).name());
        assertEquals(List.of(HookSpec.Arg.THIS), spec.throwCalls().get(0).args());
        assertEquals("(Ljava/lang/Object;)V", spec.throwCalls().get(0).descriptor());
        assertTrue(spec.entryCalls().isEmpty());
        assertTrue(spec.exitCalls().isEmpty());
    }

    @Test
    void cancelIfFalseActionProducesBooleanEntryCall() {
        HookSpec spec = HookSpec.method("net.minecraft.server.level.ServerLevel", "addFreshEntity",
                        "(Lnet/minecraft/world/entity/Entity;)Z")
                .side(Side.SERVER)
                .action(HookSpec.Action.CANCEL_IF_FALSE)
                .entry("entityJoinLevel", HookSpec.Arg.THIS, HookSpec.Arg.ARG0)
                .build();

        assertEquals(HookSpec.Action.CANCEL_IF_FALSE, spec.action());
        assertEquals(HookSpec.Kind.CANCEL_IF_FALSE, spec.entryCalls().get(0).kind());
        assertEquals("(Ljava/lang/Object;Ljava/lang/Object;)Z",
                spec.entryCalls().get(0).descriptor());
    }

    @Test
    void returnValueActionUsesTheTargetReturnDescriptor() {
        HookSpec spec = HookSpec.method("example.Target", "name", "()Ljava/lang/String;")
                .action(HookSpec.Action.RETURN_VALUE)
                .entry("clientTickExit")
                .build();

        assertEquals(HookSpec.Kind.RETURN_VALUE, spec.entryCalls().get(0).kind());
        assertEquals("()Ljava/lang/String;", spec.entryCalls().get(0).descriptor());
    }

    @Test
    void returnValueRejectsVoidPrimitiveAndWildcardTargets() {
        assertThrows(IllegalStateException.class,
                () -> HookSpec.method("example.Target", "run", "()V")
                        .action(HookSpec.Action.RETURN_VALUE)
                        .entry("clientTickExit")
                        .build());
        assertThrows(IllegalStateException.class,
                () -> HookSpec.method("example.Target", "count", "()I")
                        .action(HookSpec.Action.RETURN_VALUE)
                        .entry("clientTickExit")
                        .build());
        assertThrows(IllegalStateException.class,
                () -> HookSpec.method("example.Target", "<init>", HookSpec.ANY_DESCRIPTOR)
                        .action(HookSpec.Action.RETURN_VALUE)
                        .entry("clientTickExit")
                        .build());
    }

    @Test
    void cancelIfFalseIsRejectedOnConstructors() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> HookSpec.method("example.Target", "<init>", "()V")
                        .action(HookSpec.Action.CANCEL_IF_FALSE)
                        .entry("clientTickEnter")
                        .build());

        assertTrue(failure.getMessage().contains("CANCEL_IF_FALSE"), failure.getMessage());
        assertTrue(failure.getMessage().contains("constructor"), failure.getMessage());
        assertTrue(failure.getMessage().contains("example/Target#<init>"), failure.getMessage());
    }

    @Test
    void returnValueOnConstructorsHasAnExplicitMessage() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> HookSpec.method("example.Target", "<init>",
                                "(Ljava/lang/String;)V")
                        .action(HookSpec.Action.RETURN_VALUE)
                        .entry("clientTickExit")
                        .build());

        assertTrue(failure.getMessage().contains("RETURN_VALUE"), failure.getMessage());
        assertTrue(failure.getMessage().contains("constructor"), failure.getMessage());
        assertTrue(failure.getMessage().contains("example/Target#<init>"), failure.getMessage());
    }

    @Test
    void throwHooksAreRejectedOnConstructors() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> HookSpec.method("example.Target", "<init>", "()V")
                        .throwHook("clientTickExit")
                        .build());

        assertTrue(failure.getMessage().contains("Constructor throw hooks are not supported"),
                failure.getMessage());
        assertTrue(failure.getMessage().contains("example/Target#<init>"), failure.getMessage());
    }

    @Test
    void cancelIfFalseIsValidOnNonConstructorInstanceMethods() {
        HookSpec voidTarget = HookSpec.method("example.Target", "run", "()V")
                .action(HookSpec.Action.CANCEL_IF_FALSE)
                .entry("clientTickEnter")
                .build();
        HookSpec booleanTarget = HookSpec.method("example.Target", "allowed", "()Z")
                .action(HookSpec.Action.CANCEL_IF_FALSE)
                .entry("clientTickEnter")
                .build();

        assertEquals(HookSpec.Action.CANCEL_IF_FALSE, voidTarget.action());
        assertEquals(HookSpec.Kind.CANCEL_IF_FALSE, voidTarget.entryCalls().get(0).kind());
        assertEquals(HookSpec.Action.CANCEL_IF_FALSE, booleanTarget.action());
        assertEquals(HookSpec.Kind.CANCEL_IF_FALSE, booleanTarget.entryCalls().get(0).kind());
    }

    @Test
    void voidCallsAreValidOnMethodsAndConstructors() {
        HookSpec method = HookSpec.method("example.Target", "run", "()V")
                .entry("clientTickEnter")
                .exit("clientTickExit")
                .throwHook("clientTickExit")
                .build();
        HookSpec constructor = HookSpec.method("example.Target", "<init>", "()V")
                .entry("clientTickEnter")
                .exit("clientTickExit")
                .build();

        assertEquals(HookSpec.Action.VOID, method.action());
        assertEquals(3, method.entryCalls().size() + method.exitCalls().size()
                + method.throwCalls().size());
        assertEquals(HookSpec.Action.VOID, constructor.action());
        assertEquals(1, constructor.entryCalls().size());
        assertEquals(1, constructor.exitCalls().size());
    }

    @Test
    void nonVoidActionsRequireExactlyOneEntryCall() {
        assertThrows(IllegalStateException.class,
                () -> HookSpec.method("example.Target", "run", "()V")
                        .action(HookSpec.Action.CANCEL_IF_FALSE)
                        .exit("clientTickExit")
                        .build());
        assertThrows(IllegalStateException.class,
                () -> HookSpec.method("example.Target", "run", "()V")
                        .action(HookSpec.Action.CANCEL_IF_FALSE)
                        .entry("clientTickEnter")
                        .entry("clientTickExit")
                        .build());
    }

    @Test
    void nonVoidActionsMayKeepObservationCalls() {
        HookSpec spec = HookSpec.method("example.Target", "run", "()Z")
                .action(HookSpec.Action.CANCEL_IF_FALSE)
                .entry("clientTickEnter")
                .exit("clientTickExit")
                .throwHook("clientTickExit")
                .build();

        assertEquals(1, spec.entryCalls().size());
        assertEquals(1, spec.exitCalls().size());
        assertEquals(1, spec.throwCalls().size());
    }
}
