package net.weaver.core.hook;

import net.weaver.api.event.Side;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HookContractTest
{
    private static final String HURT_DESCRIPTOR =
            "(Lnet/minecraft/server/level/ServerLevel;"
                    + "Lnet/minecraft/world/damagesource/DamageSource;F)Z";
    private static final String DEATH_DESCRIPTOR =
            "(Lnet/minecraft/world/damagesource/DamageSource;)V";
    private static final String OPEN_MENU_DESCRIPTOR =
            "(Lnet/minecraft/world/MenuProvider;)Ljava/util/OptionalInt;";
    private static final String RESPAWN_DESCRIPTOR =
            "(ZLnet/minecraft/world/level/portal/TeleportTransition$PostTeleportTransition;)"
                    + "Lnet/minecraft/world/level/portal/TeleportTransition;";
    private static final String LOOT_DESCRIPTOR =
            "(Lnet/minecraft/world/level/storage/loot/LootParams;J)"
                    + "Lit/unimi/dsi/fastutil/objects/ObjectArrayList;";
    private static final String USE_ITEM_ON_DESCRIPTOR =
            "(Lnet/minecraft/server/level/ServerPlayer;"
                    + "Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/world/item/ItemStack;"
                    + "Lnet/minecraft/world/InteractionHand;"
                    + "Lnet/minecraft/world/phys/BlockHitResult;)"
                    + "Lnet/minecraft/world/InteractionResult;";
    private static final String USE_ITEM_DESCRIPTOR =
            "(Lnet/minecraft/server/level/ServerPlayer;"
                    + "Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/world/item/ItemStack;"
                    + "Lnet/minecraft/world/InteractionHand;)"
                    + "Lnet/minecraft/world/InteractionResult;";
    private static final String CLOSE_CONTAINER_DESCRIPTOR = "()V";
    private static final String CREATE_RESULT_DESCRIPTOR = "()V";
    private static final String CLICK_MENU_BUTTON_DESCRIPTOR =
            "(Lnet/minecraft/world/entity/player/Player;I)Z";
    private static final String TELEPORT_DESCRIPTOR =
            "(Lnet/minecraft/world/level/portal/TeleportTransition;)"
                    + "Lnet/minecraft/server/level/ServerPlayer;";

    @Test
    void everySpecCallBindsToAGameHooksMethod()
    {
        List<String> problems = GameHookTransformer.verifySpecs();

        assertTrue(problems.isEmpty(), () -> String.join(System.lineSeparator(), problems));
    }

    @Test
    void specsAreNotEmptyAndEveryEntryExitOrThrowIsDeclared()
    {
        List<HookSpec> specs = GameHookTransformer.specs();

        assertFalse(specs.isEmpty());
        assertEquals(59, specs.size(), "the built-in hook catalog");
        for (HookSpec spec : specs)
        {
            assertFalse(spec.entryCalls().isEmpty() && spec.exitCalls().isEmpty()
                    && spec.throwCalls().isEmpty(), spec.id());
        }
    }

    @Test
    void serverStoppedEventIsOnlyHookedAtRunServerExit()
    {
        HookSpec runServer = find("net/minecraft/server/MinecraftServer", "runServer", "()V");
        HookSpec stopServer = find("net/minecraft/server/MinecraftServer", "stopServer", "()V");

        assertTrue(hasCall(runServer.exitCalls(), "serverStopped"));
        assertTrue(hasCall(stopServer.entryCalls(), "serverStopping"));
        assertTrue(stopServer.exitCalls().isEmpty());
    }

    @Test
    void resourceReloadFinishedHooksRunAtExit()
    {
        HookSpec client = find("net/minecraft/client/Minecraft", "onResourceLoadFinished",
                "(Lnet/minecraft/client/GameLoadCookie;)V");
        HookSpec server = find("net/minecraft/server/players/PlayerList", "reloadResources", "()V");

        assertTrue(client.entryCalls().isEmpty());
        assertTrue(hasCall(client.exitCalls(), "clientResourceReloadFinished"));
        assertTrue(server.entryCalls().isEmpty());
        assertTrue(hasCall(server.exitCalls(), "serverResourceReloadFinished"));
    }

    @Test
    void resourceReloadStartHooksStayAtEntry()
    {
        HookSpec client = find("net/minecraft/client/Minecraft", "reloadResourcePacks",
                "()Ljava/util/concurrent/CompletableFuture;");
        HookSpec server = find("net/minecraft/server/MinecraftServer", "reloadResources",
                "(Ljava/util/Collection;)Ljava/util/concurrent/CompletableFuture;");

        assertTrue(hasCall(client.entryCalls(), "clientResourceReloadStart"));
        assertTrue(client.exitCalls().isEmpty());
        assertTrue(hasCall(server.entryCalls(), "serverResourceReloadStart"));
        assertTrue(server.exitCalls().isEmpty());
    }

    @Test
    void clientLevelUnloadPassesTheMinecraftInstanceToTheAccessor()
    {
        HookSpec spec = find("net/minecraft/client/Minecraft", "clearClientLevel",
                "(Lnet/minecraft/client/gui/screens/Screen;)V");

        assertEquals(1, spec.entryCalls().size());
        assertEquals("clientLevelUnload", spec.entryCalls().get(0).name());
        assertEquals(List.of(HookSpec.Arg.THIS), spec.entryCalls().get(0).args());
        assertTrue(spec.exitCalls().isEmpty());
    }

    @Test
    void cancelablePilotsUseCancelIfFalseOnTheServerSide()
    {
        HookSpec entityJoin = find("net/minecraft/server/level/ServerLevel", "addFreshEntity",
                "(Lnet/minecraft/world/entity/Entity;)Z");
        HookSpec chat = find("net/minecraft/server/players/PlayerList", "broadcastChatMessage",
                "(Lnet/minecraft/network/chat/PlayerChatMessage;"
                        + "Lnet/minecraft/server/level/ServerPlayer;"
                        + "Lnet/minecraft/network/chat/ChatType$Bound;)V");

        for (HookSpec spec : List.of(entityJoin, chat))
        {
            assertEquals(HookSpec.Action.CANCEL_IF_FALSE, spec.action(), spec.id());
            assertEquals(Side.SERVER, spec.side(), spec.id());
            assertTrue(spec.required(), spec.id());
            assertEquals(1, spec.entryCalls().size(), spec.id());
            assertEquals(HookSpec.Kind.CANCEL_IF_FALSE, spec.entryCalls().get(0).kind(), spec.id());
            assertEquals("(" + "Ljava/lang/Object;".repeat(spec.entryCalls().get(0).args().size())
                    + ")Z", spec.entryCalls().get(0).descriptor(), spec.id());
        }
        assertTrue(hasCall(entityJoin.entryCalls(), "entityJoinLevel"));
        assertTrue(hasCall(chat.entryCalls(), "chat"));
    }

    @Test
    void observationSpecsExistWithExactDescriptors()
    {
        assertObservation("net/minecraft/server/level/ServerPlayer", "die", DEATH_DESCRIPTOR,
                "playerDeath", true, List.of(HookSpec.Arg.THIS, HookSpec.Arg.ARG0));
        assertObservation("net/minecraft/server/level/ServerPlayer", "openMenu",
                OPEN_MENU_DESCRIPTOR, "containerOpen", false,
                List.of(HookSpec.Arg.THIS, HookSpec.Arg.ARG0));
        assertObservation("net/minecraft/server/level/ServerPlayer",
                "findRespawnPositionAndUseSpawnBlock", RESPAWN_DESCRIPTOR, "playerRespawn", false,
                List.of(HookSpec.Arg.THIS));
        assertObservation("net/minecraft/world/level/storage/loot/LootTable", "getRandomItems",
                LOOT_DESCRIPTOR, "lootTable", false,
                List.of(HookSpec.Arg.THIS, HookSpec.Arg.ARG0));
    }

    @Test
    void entityHurtIsCancelIfFalseWithExactPayloads()
    {
        HookSpec spec = find("net/minecraft/world/entity/LivingEntity", "hurtServer",
                HURT_DESCRIPTOR);

        assertEquals(Side.SERVER, spec.side(), spec.id());
        assertEquals(HookSpec.Action.CANCEL_IF_FALSE, spec.action(), spec.id());
        assertTrue(spec.required(), spec.id());
        assertEquals(1, spec.entryCalls().size(), spec.id());
        HookSpec.HookCall call = spec.entryCalls().get(0);
        assertEquals("entityHurt", call.name(), spec.id());
        assertEquals(List.of(HookSpec.Arg.THIS, HookSpec.Arg.ARG0, HookSpec.Arg.ARG1),
                call.args(), spec.id());
        assertEquals(HookSpec.Kind.CANCEL_IF_FALSE, call.kind(), spec.id());
        assertEquals("(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z",
                call.descriptor(), spec.id());
        assertTrue(spec.exitCalls().isEmpty(), spec.id());
        assertTrue(spec.throwCalls().isEmpty(), spec.id());
    }

    @Test
    void hookExpansionSpecsExistWithExactContracts()
    {
        assertServerHook("net/minecraft/server/level/ServerPlayerGameMode", "useItemOn",
                USE_ITEM_ON_DESCRIPTOR, "blockInteract",
                List.of(HookSpec.Arg.ARG0, HookSpec.Arg.ARG1, HookSpec.Arg.ARG2,
                        HookSpec.Arg.ARG3, HookSpec.Arg.ARG4));
        assertServerHook("net/minecraft/server/level/ServerPlayerGameMode", "useItem",
                USE_ITEM_DESCRIPTOR, "itemUse",
                List.of(HookSpec.Arg.ARG0, HookSpec.Arg.ARG1, HookSpec.Arg.ARG2,
                        HookSpec.Arg.ARG3));
        assertServerHook("net/minecraft/world/entity/LivingEntity", "die", DEATH_DESCRIPTOR,
                "livingEntityDeath", List.of(HookSpec.Arg.THIS, HookSpec.Arg.ARG0));
        assertServerHook("net/minecraft/server/level/ServerPlayer", "closeContainer",
                CLOSE_CONTAINER_DESCRIPTOR, "containerClose", List.of(HookSpec.Arg.THIS));
        assertServerHook("net/minecraft/world/inventory/AnvilMenu", "createResult",
                CREATE_RESULT_DESCRIPTOR, "anvilUpdate", List.of(HookSpec.Arg.THIS));
        assertServerHook("net/minecraft/world/inventory/EnchantmentMenu", "clickMenuButton",
                CLICK_MENU_BUTTON_DESCRIPTOR, "enchant",
                List.of(HookSpec.Arg.THIS, HookSpec.Arg.ARG0));
        assertServerHook("net/minecraft/server/level/ServerPlayer", "teleport",
                TELEPORT_DESCRIPTOR, "playerTeleport",
                List.of(HookSpec.Arg.THIS, HookSpec.Arg.ARG0));
    }

    @Test
    void respawnTargetSpecOverridesTheResultAtPriorityTen()
    {
        List<HookSpec> matches = GameHookTransformer.specs().stream()
                .filter(spec -> spec.className().equals("net/minecraft/server/level/ServerPlayer"))
                .filter(spec -> spec.methodName().equals("findRespawnPositionAndUseSpawnBlock"))
                .filter(spec -> spec.descriptor().equals(RESPAWN_DESCRIPTOR))
                .toList();

        assertEquals(2, matches.size(), "observation plus result-override respawn specs");
        HookSpec observation = matches.stream()
                .filter(spec -> hasCall(spec.entryCalls(), "playerRespawn"))
                .findFirst()
                .orElseThrow();
        HookSpec target = matches.stream()
                .filter(spec -> hasCall(spec.entryCalls(), "playerRespawnTarget"))
                .findFirst()
                .orElseThrow();

        assertEquals(HookSpec.Action.VOID, observation.action(), observation.id());
        assertEquals(Side.SERVER, target.side(), target.id());
        assertEquals(HookSpec.Action.RETURN_VALUE, target.action(), target.id());
        assertEquals(10, target.priority(), target.id());
        assertFalse(target.required(), target.id());
        assertEquals(1, target.entryCalls().size(), target.id());
        HookSpec.HookCall call = target.entryCalls().get(0);
        assertEquals("playerRespawnTarget", call.name(), target.id());
        assertEquals(List.of(HookSpec.Arg.THIS, HookSpec.Arg.ARG1), call.args(), target.id());
        assertEquals(HookSpec.Kind.RETURN_VALUE, call.kind(), target.id());
        assertEquals("(Ljava/lang/Object;Ljava/lang/Object;)"
                        + "Lnet/minecraft/world/level/portal/TeleportTransition;",
                call.descriptor(), target.id());
        assertTrue(target.exitCalls().isEmpty(), target.id());
        assertTrue(target.throwCalls().isEmpty(), target.id());
    }

    @Test
    void sideAssignmentsMatchTheTargetProcess()
    {
        Set<String> serverOnly = Set.of(
                "net/minecraft/server/level/ServerLevel",
                "net/minecraft/server/level/ServerPlayer",
                "net/minecraft/server/players/PlayerList",
                "net/minecraft/server/MinecraftServer",
                "net/minecraft/server/dedicated/DedicatedServer",
                "net/minecraft/server/level/ServerPlayerGameMode",
                "net/minecraft/client/server/IntegratedServer");

        for (HookSpec spec : GameHookTransformer.specs())
        {
            if (spec.className().equals("net/minecraft/commands/Commands"))
            {
                assertEquals(Side.BOTH, spec.side(), spec.id());
            }
            else if (serverOnly.contains(spec.className()))
            {
                assertEquals(Side.SERVER, spec.side(), spec.id());
            }
            else if (spec.className().startsWith("net/minecraft/client/"))
            {
                assertEquals(Side.CLIENT, spec.side(), spec.id());
            }
        }
    }

    @Test
    void verificationReportsMissingGameHooksMethods()
    {
        HookSpec spec = HookSpec.method("example.Test", "run", "()V")
                .entry("definitelyMissingHook")
                .build();

        List<String> problems = GameHookTransformer.verifySpecs(List.of(spec));

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("definitelyMissingHook"));
        assertTrue(problems.get(0).contains("missing method"));
    }

    @Test
    void verificationReportsDescriptorMismatches()
    {
        HookSpec spec = HookSpec.method("example.Test", "run", "()V")
                .entry("clientTickExit", HookSpec.Arg.ARG0)
                .build();

        List<String> problems = GameHookTransformer.verifySpecs(List.of(spec));

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("clientTickExit"));
    }

    @Test
    void verificationRejectsVoidReturnsForValueCalls()
    {
        HookSpec valueCall = HookSpec.method("example.Test", "run", "()Ljava/lang/String;")
                .action(HookSpec.Action.RETURN_VALUE)
                .entry("clientTickEnter")
                .build();

        List<String> problems = GameHookTransformer.verifySpecs(List.of(valueCall));
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("RETURN_VALUE"));
    }

    @Test
    void verificationChecksThrowCalls()
    {
        HookSpec spec = HookSpec.method("example.Test", "run", "()V")
                .throwHook("missingThrowHook")
                .build();

        List<String> problems = GameHookTransformer.verifySpecs(List.of(spec));

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("throw call=missingThrowHook"));
    }

    @Test
    void entityInteractionSpecsUseExactDescriptors()
    {
        assertServerHook("net/minecraft/world/entity/player/Player", "interactOn",
                "(Lnet/minecraft/world/entity/Entity;"
                        + "Lnet/minecraft/world/InteractionHand;"
                        + "Lnet/minecraft/world/phys/Vec3;)"
                        + "Lnet/minecraft/world/InteractionResult;",
                "entityInteract",
                List.of(HookSpec.Arg.THIS, HookSpec.Arg.ARG0, HookSpec.Arg.ARG1,
                        HookSpec.Arg.ARG2));
        assertCancelable("net/minecraft/world/entity/player/Player", "attack",
                "(Lnet/minecraft/world/entity/Entity;)V", "entityAttack",
                List.of(HookSpec.Arg.THIS, HookSpec.Arg.ARG0));
    }

    @Test
    void blockBreakAndPlaceSpecsUseExactContracts()
    {
        assertCancelable("net/minecraft/server/level/ServerPlayerGameMode", "destroyBlock",
                "(Lnet/minecraft/core/BlockPos;)Z", "blockBreak",
                List.of(HookSpec.Arg.THIS, HookSpec.Arg.ARG0));
        HookSpec place = find("net/minecraft/world/item/BlockItem", "place",
                "(Lnet/minecraft/world/item/context/BlockPlaceContext;)"
                        + "Lnet/minecraft/world/InteractionResult;");
        assertEquals(Side.SERVER, place.side(), place.id());
        assertEquals(HookSpec.Action.RETURN_VALUE, place.action(), place.id());
        assertTrue(place.required(), place.id());
        assertEquals(1, place.entryCalls().size(), place.id());
        HookSpec.HookCall call = place.entryCalls().get(0);
        assertEquals("blockPlace", call.name(), place.id());
        assertEquals(List.of(HookSpec.Arg.ARG0), call.args(), place.id());
        assertEquals(HookSpec.Kind.RETURN_VALUE, call.kind(), place.id());
        assertEquals("(Ljava/lang/Object;)Lnet/minecraft/world/InteractionResult;",
                call.descriptor(), place.id());
        assertTrue(place.exitCalls().isEmpty(), place.id());
        assertTrue(place.throwCalls().isEmpty(), place.id());
    }

    @Test
    void itemLifecycleAndProjectileSpecsUseExactContracts()
    {
        assertServerHook("net/minecraft/world/entity/LivingEntity", "completeUsingItem", "()V",
                "itemUseFinish", List.of(HookSpec.Arg.THIS));
        assertServerHook("net/minecraft/world/entity/LivingEntity", "releaseUsingItem", "()V",
                "itemRelease", List.of(HookSpec.Arg.THIS));
        assertServerHook("net/minecraft/world/entity/projectile/Projectile", "shoot", "(DDDFF)V",
                "projectileLaunch", List.of(HookSpec.Arg.THIS));
    }

    @Test
    void clientPlayerTickSpecBracketsTheLocalPlayerTick()
    {
        HookSpec spec = find("net/minecraft/client/player/LocalPlayer", "tick", "()V");

        assertEquals(Side.CLIENT, spec.side(), spec.id());
        assertEquals(HookSpec.Action.VOID, spec.action(), spec.id());
        assertTrue(spec.required(), spec.id());
        assertEquals(1, spec.entryCalls().size(), spec.id());
        assertEquals("clientPlayerTickEnter", spec.entryCalls().get(0).name(), spec.id());
        assertEquals(List.of(HookSpec.Arg.THIS), spec.entryCalls().get(0).args(), spec.id());
        assertEquals(1, spec.exitCalls().size(), spec.id());
        assertEquals("clientPlayerTickExit", spec.exitCalls().get(0).name(), spec.id());
        assertEquals(List.of(HookSpec.Arg.THIS), spec.exitCalls().get(0).args(), spec.id());
        assertTrue(spec.throwCalls().isEmpty(), spec.id());
    }

    @Test
    void entityTickSpecsCoverBothLogicalSides()
    {
        assertServerHook("net/minecraft/server/level/ServerLevel", "tickNonPassenger",
                "(Lnet/minecraft/world/entity/Entity;)V", "serverEntityTick",
                List.of(HookSpec.Arg.THIS, HookSpec.Arg.ARG0));
        HookSpec client = find("net/minecraft/client/multiplayer/ClientLevel", "tickNonPassenger",
                "(Lnet/minecraft/world/entity/Entity;)V");
        assertEquals(Side.CLIENT, client.side(), client.id());
        assertEquals(HookSpec.Action.VOID, client.action(), client.id());
        assertEquals(1, client.entryCalls().size(), client.id());
        assertEquals("clientEntityTick", client.entryCalls().get(0).name(), client.id());
        assertEquals(List.of(HookSpec.Arg.THIS, HookSpec.Arg.ARG0),
                client.entryCalls().get(0).args(), client.id());
        assertTrue(client.exitCalls().isEmpty(), client.id());
    }

    @Test
    void chunkAndRemovalSpecsUseExactContracts()
    {
        assertServerHook("net/minecraft/server/level/ServerLevel", "startTickingChunk",
                "(Lnet/minecraft/world/level/chunk/LevelChunk;)V", "chunkLoad",
                List.of(HookSpec.Arg.THIS, HookSpec.Arg.ARG0));
        assertServerHook("net/minecraft/server/level/ServerLevel", "unload",
                "(Lnet/minecraft/world/level/chunk/LevelChunk;)V", "chunkUnload",
                List.of(HookSpec.Arg.THIS, HookSpec.Arg.ARG0));
        assertServerHook("net/minecraft/world/entity/Entity", "remove",
                "(Lnet/minecraft/world/entity/Entity$RemovalReason;)V", "entityRemoval",
                List.of(HookSpec.Arg.THIS, HookSpec.Arg.ARG0));
    }

    @Test
    void containerCompletionAndBlockEntitySpecsUseExactContracts()
    {
        assertServerHook("net/minecraft/server/level/ServerPlayer", "doCloseContainer", "()V",
                "containerClosed", List.of(HookSpec.Arg.THIS));
        assertServerHook("net/minecraft/world/level/block/entity/BlockEntity",
                "loadWithComponents",
                "(Lnet/minecraft/world/level/storage/ValueInput;)V", "blockEntityLoad",
                List.of(HookSpec.Arg.THIS));
        assertServerHook("net/minecraft/world/level/block/entity/BlockEntity",
                "saveWithFullMetadata",
                "(Lnet/minecraft/world/level/storage/ValueOutput;)V", "blockEntitySave",
                List.of(HookSpec.Arg.THIS));
    }

    @Test
    void playerRespawnActionSpecSkipsThePrimitiveArgument()
    {
        assertServerHook("net/minecraft/server/players/PlayerList", "respawn",
                "(Lnet/minecraft/server/level/ServerPlayer;Z"
                        + "Lnet/minecraft/world/entity/Entity$RemovalReason;)"
                        + "Lnet/minecraft/server/level/ServerPlayer;",
                "playerRespawnAction",
                List.of(HookSpec.Arg.THIS, HookSpec.Arg.ARG0, HookSpec.Arg.ARG2));
    }

    @Test
    void entityPersistenceSpecsTargetTheConcreteWrappers()
    {
        assertServerHook("net/minecraft/world/entity/Entity", "saveWithoutId",
                "(Lnet/minecraft/world/level/storage/ValueOutput;)V", "entitySave",
                List.of(HookSpec.Arg.THIS, HookSpec.Arg.ARG0));
        assertServerHook("net/minecraft/world/entity/Entity", "load",
                "(Lnet/minecraft/world/level/storage/ValueInput;)V", "entityLoad",
                List.of(HookSpec.Arg.THIS, HookSpec.Arg.ARG0));
    }

    @Test
    void blockEntityPersistenceSpecsShareTheObservationTargets()
    {
        HookSpec load = findById("net.minecraft.world.level.block.entity.BlockEntity"
                + "#loadWithComponents(Lnet/minecraft/world/level/storage/ValueInput;)V"
                + "#capabilities");
        HookSpec save = findById("net.minecraft.world.level.block.entity.BlockEntity"
                + "#saveWithFullMetadata(Lnet/minecraft/world/level/storage/ValueOutput;)V"
                + "#capabilities");

        assertPersistenceSpec(load, -10, "blockEntityPersistenceLoad");
        assertPersistenceSpec(save, 10, "blockEntityPersistenceSave");
    }

    private static void assertPersistenceSpec(HookSpec spec, int priority, String hookName)
    {
        assertEquals(Side.SERVER, spec.side(), spec.id());
        assertEquals(HookSpec.Action.VOID, spec.action(), spec.id());
        assertEquals(priority, spec.priority(), spec.id());
        assertEquals(1, spec.entryCalls().size(), spec.id());
        HookSpec.HookCall call = spec.entryCalls().get(0);
        assertEquals(hookName, call.name(), spec.id());
        assertEquals(List.of(HookSpec.Arg.THIS, HookSpec.Arg.ARG0), call.args(), spec.id());
        assertEquals(HookSpec.Kind.VOID, call.kind(), spec.id());
        assertTrue(spec.exitCalls().isEmpty(), spec.id());
        assertTrue(spec.throwCalls().isEmpty(), spec.id());
    }

    private static HookSpec findById(String id)
    {
        return GameHookTransformer.specs().stream()
                .filter(spec -> spec.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing hook spec " + id));
    }

    private static void assertCancelable(String className, String methodName, String descriptor,
                                         String hookName, List<HookSpec.Arg> expectedArgs)
    {
        HookSpec spec = find(className, methodName, descriptor);

        assertEquals(Side.SERVER, spec.side(), spec.id());
        assertEquals(HookSpec.Action.CANCEL_IF_FALSE, spec.action(), spec.id());
        assertTrue(spec.required(), spec.id());
        assertEquals(1, spec.entryCalls().size(), spec.id());
        HookSpec.HookCall call = spec.entryCalls().get(0);
        assertEquals(hookName, call.name(), spec.id());
        assertEquals(expectedArgs, call.args(), spec.id());
        assertEquals(HookSpec.Kind.CANCEL_IF_FALSE, call.kind(), spec.id());
        assertEquals("(" + "Ljava/lang/Object;".repeat(expectedArgs.size()) + ")Z",
                call.descriptor(), spec.id());
        assertTrue(spec.exitCalls().isEmpty(), spec.id());
        assertTrue(spec.throwCalls().isEmpty(), spec.id());
    }

    private static void assertServerHook(String className, String methodName, String descriptor,
                                         String hookName, List<HookSpec.Arg> expectedArgs)
    {
        HookSpec spec = find(className, methodName, descriptor);

        assertEquals(Side.SERVER, spec.side(), spec.id());
        assertEquals(HookSpec.Action.VOID, spec.action(), spec.id());
        assertTrue(spec.required(), spec.id());
        assertEquals(1, spec.entryCalls().size(), spec.id());
        HookSpec.HookCall call = spec.entryCalls().get(0);
        assertEquals(hookName, call.name(), spec.id());
        assertEquals(expectedArgs, call.args(), spec.id());
        assertEquals(HookSpec.Kind.VOID, call.kind(), spec.id());
        assertTrue(spec.exitCalls().isEmpty(), spec.id());
        assertTrue(spec.throwCalls().isEmpty(), spec.id());
    }

    private static void assertObservation(String className, String methodName, String descriptor,
                                          String hookName, boolean required,
                                          List<HookSpec.Arg> expectedArgs)
    {
        HookSpec spec = find(className, methodName, descriptor);

        assertEquals(Side.SERVER, spec.side(), spec.id());
        assertEquals(required, spec.required(), spec.id());
        assertEquals(HookSpec.Action.VOID, spec.action(), spec.id());
        assertEquals(1, spec.entryCalls().size(), spec.id());
        assertEquals(hookName, spec.entryCalls().get(0).name(), spec.id());
        assertEquals(expectedArgs, spec.entryCalls().get(0).args(), spec.id());
        assertTrue(spec.exitCalls().isEmpty(), spec.id());
        assertTrue(spec.throwCalls().isEmpty(), spec.id());
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

    private static boolean hasCall(List<HookSpec.HookCall> calls, String name)
    {
        return calls.stream().anyMatch(call -> call.name().equals(name));
    }
}
