package net.weaver.proof;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.weaver.api.ModContext;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.EventFilter;
import net.weaver.api.event.EventPriority;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.event.events.LevelLoadEvent;
import net.weaver.api.event.events.ServerStartedEvent;
import net.weaver.api.event.events.ServerStartingEvent;
import net.weaver.api.event.events.ServerTickEvent;
import net.weaver.api.game.GameWeaverMod;
import net.weaver.api.game.event.ChatEvent;
import net.weaver.api.game.event.ContainerOpenEvent;
import net.weaver.api.game.event.EntityHurtEvent;
import net.weaver.api.game.event.EntityJoinLevelEvent;
import net.weaver.api.game.event.LootTableEvent;
import net.weaver.api.game.event.PlayerJoinEvent;
import net.weaver.api.game.event.PlayerRespawnEvent;

/** Entrypoint for the real Minecraft 26.3 proof. */
public final class ProofMod implements GameWeaverMod {

    @Override
    public void onInitialize(ModContext context) {
        ProofHooks.phase("initialize");
        EventBus localBus = context.modEvents();
        ProofHooks.check(localBus != EventBus.MOD, "Local bus distinct");
        ProofHooks.check(("mod:" + context.id()).equals(localBus.name()), "Local bus name");
        ProofHooks.check(context.globalModEvents() == EventBus.MOD, "Global aggregate bus");
        ProofChecks.install(context);
        ProofContent.defer(context);
        NfProof.install(context);
        ProofDataRegistries.register();
        ProofDataMaps.register();
        GameEventsExtrasProof.installEarly(context);
        context.events().addListener(ServerStartingEvent.class,
                event -> ProofHooks.eventOnce("ManualListener ServerStartingEvent"));
        context.events().addListener(ServerStartedEvent.class,
                event -> ProofHooks.eventOnce("ManualListener ServerStartedEvent"));
        context.events().addListener(LevelLoadEvent.class,
                event -> ProofHooks.eventOnce("ManualListener LevelLoadEvent"));
        context.events().addListener(LevelLoadEvent.class, ProofChecks::onBaseLevelLoad);
        context.events().addListener(net.weaver.api.game.event.LevelLoadEvent.class,
                ProofChecks::onTypedLevelLoad);
        context.events().addListener(LevelLoadEvent.class, ProofChecks::onClientFilterHit,
                EventFilter.logicalSide(LogicalSide.CLIENT));
        context.events().addListener(LevelLoadEvent.class, ProofChecks::onServerFilterHit,
                EventFilter.logicalSide(LogicalSide.SERVER));
        context.events().addListener(LevelLoadEvent.class, event -> {
            if (event.logicalSide() == LogicalSide.CLIENT) {
                ProofChecks.clientLevelComplete();
            }
        }, EventPriority.MONITOR);
        context.events().addListener(EntityJoinLevelEvent.class, ProofChecks::onTypedEntityJoin);
        context.events().addListener(EntityHurtEvent.class, ProofChecks::onTypedEntityHurt);
        context.events().addListener(PlayerJoinEvent.class, ProofChecks::onTypedPlayerJoin);
        context.events().addListener(ChatEvent.class, ProofChecks::onTypedChat);
        context.events().addListener(ContainerOpenEvent.class, ProofChecks::onTypedContainerOpen);
        context.events().addListener(PlayerRespawnEvent.class, ProofChecks::onTypedPlayerRespawn);
        context.events().addListener(LootTableEvent.class, ProofChecks::onTypedLootTable);
        context.events().addListener(ServerTickEvent.Post.class,
                event -> ProofChecks.onServerTick());
        context.log("PROOF INIT side=" + context.side());
    }

    @Override
    public void onRegister(ModContext context) {
        ProofHooks.phase("register");
        ProofContent.register(context);
    }

    @Override
    public void onCommonSetup(ModContext context) {
        ProofHooks.phase("common");
        NfProof.setup();
    }

    @Override
    public void onClientSetup(ModContext context) {
        ProofHooks.phase("client");
        NfProofClient.setup();
    }

    @Override
    public void onServerSetup(ModContext context) {
        ProofHooks.phase("server");
        ProofHooks.eventOnce("ServerSetupHook");
    }

    @Override
    public void onLoadComplete(ModContext context) {
        ProofHooks.phase("complete");
        ProofContent.check(context);
    }

    @Override
    public void onServerStarting(ModContext context) {
        ProofHooks.eventOnce("ModHook ServerStarting");
        ProofHooks.eventOnce("ModHook LogicalServer context=" + context.logicalSide());
    }

    @Override
    public void onServerStarted(ModContext context) {
        ProofHooks.eventOnce("ModHook ServerStarted");
    }

    @Override
    public void onServerTick(ModContext context, long tick) {
        ProofHooks.eventOnce("ModHook ServerTick");
    }

    @Override
    public void onClientTick(ModContext context, long tick) {
        ProofHooks.eventOnce("ModHook ClientTick");
        ProofHooks.eventOnce("ModHook LogicalClient context=" + context.logicalSide());
    }

    @Override
    public void onPlayerJoin(ModContext context, ServerPlayer player) {
        ProofHooks.eventOnce("ModHook LogicalPlayer context=" + context.logicalSide());
    }

    @Override
    public void onLevelLoad(ModContext context, Level level) {
        ProofHooks.eventOnce("ModHook LevelLoad");
    }

    @Override
    public void onCommandRegister(ModContext context, Object dispatcher) {
        ProofHooks.eventOnce("ModHook CommandRegister");
    }

    @Override
    public void onResourceReload(ModContext context, Object source, boolean client) {
        ProofHooks.eventOnce("ModHook ResourceReload client=" + client);
    }
}
