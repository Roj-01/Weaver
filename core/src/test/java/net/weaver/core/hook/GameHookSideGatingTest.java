package net.weaver.core.hook;

import net.weaver.core.LifecycleHooks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameHookSideGatingTest
{
    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown()
    {
        LifecycleHooks.reset();
        GameHookTransformer.configureSides(true, true);
    }

    @Test
    void aServerOnlyProcessSkipsClientHookSpecs()
    {
        LifecycleHooks.install(List.of(), getClass().getClassLoader(), "server", false,
                tempDir.resolve("config"), tempDir.resolve("data"), line -> { });

        GameHookTransformer.configureSides(LifecycleHooks.clientAvailable(),
                LifecycleHooks.serverAvailable());

        HookSpec client = find("net/minecraft/client/Minecraft", "tick", "()V");
        HookSpec server = find("net/minecraft/server/MinecraftServer", "runServer", "()V");
        HookSpec both = find("net/minecraft/commands/Commands", "<init>", HookSpec.ANY_DESCRIPTOR);
        HookSpec localPlayer = find("net/minecraft/client/player/LocalPlayer", "tick", "()V");
        HookSpec clientEntityTick = find("net/minecraft/client/multiplayer/ClientLevel",
                "tickNonPassenger", "(Lnet/minecraft/world/entity/Entity;)V");

        assertEquals("SKIPPED", GameHookTransformer.statusOf(client));
        assertEquals("SKIPPED", GameHookTransformer.statusOf(localPlayer));
        assertEquals("SKIPPED", GameHookTransformer.statusOf(clientEntityTick));
        assertEquals("PENDING", GameHookTransformer.statusOf(server));
        assertEquals("PENDING", GameHookTransformer.statusOf(both));
    }

    @Test
    void aClientOnlyProcessSkipsServerHookSpecsAndKeepsBothSides()
    {
        LifecycleHooks.install(List.of(), getClass().getClassLoader(), "client", false,
                tempDir.resolve("config"), tempDir.resolve("data"), line -> { });

        GameHookTransformer.configureSides(LifecycleHooks.clientAvailable(),
                LifecycleHooks.serverAvailable());

        HookSpec server = find("net/minecraft/server/players/PlayerList", "remove",
                "(Lnet/minecraft/server/level/ServerPlayer;)V");
        HookSpec both = find("net/minecraft/commands/Commands", "<init>", HookSpec.ANY_DESCRIPTOR);
        HookSpec blockBreak = find("net/minecraft/server/level/ServerPlayerGameMode",
                "destroyBlock", "(Lnet/minecraft/core/BlockPos;)Z");
        HookSpec respawnAction = find("net/minecraft/server/players/PlayerList", "respawn",
                "(Lnet/minecraft/server/level/ServerPlayer;Z"
                        + "Lnet/minecraft/world/entity/Entity$RemovalReason;)"
                        + "Lnet/minecraft/server/level/ServerPlayer;");

        assertEquals("SKIPPED", GameHookTransformer.statusOf(server));
        assertEquals("SKIPPED", GameHookTransformer.statusOf(blockBreak));
        assertEquals("SKIPPED", GameHookTransformer.statusOf(respawnAction));
        assertEquals("PENDING", GameHookTransformer.statusOf(both));
    }

    @Test
    void aProcessWithNeitherSideStillHostsBothSideSpecs()
    {
        GameHookTransformer.configureSides(false, false);

        HookSpec client = find("net/minecraft/client/Minecraft", "tick", "()V");
        HookSpec both = find("net/minecraft/commands/Commands", "<init>", HookSpec.ANY_DESCRIPTOR);

        assertEquals("SKIPPED", GameHookTransformer.statusOf(client));
        assertEquals("PENDING", GameHookTransformer.statusOf(both));
    }

    @Test
    void strictModeThrowsForMissingRequiredHooks()
    {
        HookSpec required = HookSpec.method("example.Test", "run", "()V")
                .entry("clientTickEnter")
                .build();
        Map<HookSpec, String> statuses = new LinkedHashMap<>();
        statuses.put(required, "MISSING");

        String previous = System.getProperty("weaver.hooks.strict");
        System.setProperty("weaver.hooks.strict", "true");
        try
        {
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> GameHookTransformer.enforceStrict(List.of(required), statuses));
            assertTrue(failure.getMessage().contains(required.id()));
        }
        finally
        {
            restoreStrict(previous);
        }

        assertDoesNotThrow(() -> GameHookTransformer.enforceStrict(List.of(required), statuses));
    }

    @Test
    void strictModeIgnoresOptionalAndHealthySpecs()
    {
        HookSpec required = HookSpec.method("example.Test", "run", "()V")
                .entry("clientTickEnter")
                .build();
        HookSpec optional = HookSpec.method("example.Optional", "run", "()V")
                .required(false)
                .entry("clientTickEnter")
                .build();
        Map<HookSpec, String> statuses = new LinkedHashMap<>();
        statuses.put(required, "APPLIED");
        statuses.put(optional, "MISSING");

        String previous = System.getProperty("weaver.hooks.strict");
        System.setProperty("weaver.hooks.strict", "true");
        try
        {
            assertDoesNotThrow(() -> GameHookTransformer.enforceStrict(
                    List.of(required, optional), statuses));
        }
        finally
        {
            restoreStrict(previous);
        }
    }

    private static void restoreStrict(String previous)
    {
        if (previous == null)
        {
            System.clearProperty("weaver.hooks.strict");
        }
        else
        {
            System.setProperty("weaver.hooks.strict", previous);
        }
    }

    private static HookSpec find(String className, String methodName, String descriptor)
    {
        return GameHookTransformer.specs().stream()
                .filter(spec -> spec.className().equals(className))
                .filter(spec -> spec.methodName().equals(methodName))
                .filter(spec -> spec.descriptor().equals(descriptor))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "spec not found: " + className + "#" + methodName + descriptor));
    }
}
