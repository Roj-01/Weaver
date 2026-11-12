package net.weaver.clientproof;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.weaver.api.event.EventBus;
import net.weaver.api.game.event.client.ClientChatReceivedEvent;
import net.weaver.api.game.event.client.ClientChatSendEvent;
import net.weaver.api.game.event.client.ClientDifficultyChangeEvent;
import net.weaver.api.game.event.client.ClientGameModeChangeEvent;
import net.weaver.api.game.event.client.ClientKeyInputEvent;
import net.weaver.api.game.event.client.ClientPlayerNetworkEvent;
import net.weaver.api.game.event.client.ClientTagsUpdatedEvent;
import net.weaver.api.game.event.client.FovModifierEvent;
import net.weaver.api.game.event.client.MovementInputUpdateEvent;
import net.weaver.api.game.event.client.PlaySoundEvent;
import net.weaver.api.game.event.client.RenderFrameEvent;
import net.weaver.api.game.event.client.RenderLivingEvent;
import net.weaver.api.game.event.client.ScreenInitEvent;
import net.weaver.api.game.event.client.SelectMusicEvent;
import net.weaver.api.game.event.client.ToastAddEvent;
import net.weaver.api.game.event.client.WindowResizeEvent;
import net.weaver.api.game.event.world.PlayLevelSoundEvent;

/**
 * Client game events (batch 6a). Listeners record which events fired while
 * the proof boots, opens screens, and joins the fixture world; once in the
 * world, the proof sends a chat message, a system message, and a toast, and
 * cancels each through its event so nothing leaves the client or appears on
 * screen. Logs {@code CLIENT PROOF GAME EVENTS ok=...} once every required
 * event was seen.
 */
final class ClientGameEventsProof {
    private static final String CHAT = "weaver proof chat";
    private static final String SYSTEM = "weaver proof system";
    private static final String TOAST = "weaver proof toast";
    private static final Set<String> REQUIRED = Set.of(
            "screen-init-pre", "screen-init-post", "render-frame-pre", "render-frame-post", "logged-in",
            "movement-input", "fov-modifier", "select-music", "chat-send-canceled", "chat-received-canceled",
            "toast-canceled", "client-tags", "flip-frame", "extract-level-state", "client-reload-listener", "particle-providers", "atlas-stitched", "model-baking", "registry-check", "mod-resource-pack", "creative-pages",
            "main-target", "debug-subscriptions", "frame-graph", "prepare-buffers", "section-geometry",
            "skull-models", "baking-result", "fluid-models", "pip-renderers", "texture-atlases", "render-state-modified", "feature-renderer", "screen-shield",
            "ber-registered", "custom-geometry", "world-blocks", "render-type", "post-effect", "gui-scene",
            "model-loader-read", "model-loader-baked", "mod-list", "config-screen", "mods-button-title",
            "mods-button-pause", "draw-after-sky", "draw-after-solid", "draw-after-translucent", "draw-after-level",
            "pause-cancel", "player-data-request", "instanced-mesh", "draw-after-translucent-blocks",
            "draw-after-translucent-features", "draw-after-clouds", "draw-after-weather", "draw-after-opaque-blocks", "draw-after-particles",
            "item-reequip", "item-keeps-mining", "block-particles", "rider-pose",
            "entity-tracking-extension", "item-highlight", "fluid-overlay",
            "block-sounds", "face-hiding", "arm-pose", "armor-model", "guide-screen", "instanced-translucent", "instanced-own-pass",
            "client-command", "mouse-button", "entity-tracking",
            "item-tint-source");
    private static final Map<String, Boolean> SEEN = new ConcurrentHashMap<>();
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean DRIVEN = new AtomicBoolean();
    private static final AtomicBoolean REPORTED = new AtomicBoolean();
    private static final AtomicBoolean CREATIVE_DONE = new AtomicBoolean();
    private static final AtomicBoolean SHIELD_OPENED = new AtomicBoolean();
    private static volatile net.minecraft.client.gui.screens.Screen shieldScreen;
    private static volatile boolean shieldThrew;

    private ClientGameEventsProof() {
    }

    static void install() {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        ClientRenderingProof.install();
        ClientGuiProof.install();
        EventBus.GAME.addListener(ScreenInitEvent.class, event ->
                see(event.phase() == ScreenInitEvent.Phase.PRE ? "screen-init-pre" : "screen-init-post"));
        EventBus.GAME.addListener(RenderFrameEvent.class, event ->
                see(event.phase() == RenderFrameEvent.Phase.PRE ? "render-frame-pre" : "render-frame-post"));
        EventBus.GAME.addListener(ClientPlayerNetworkEvent.class, event -> {
            if (event.kind() == ClientPlayerNetworkEvent.Kind.LOGGED_IN && event.player() != null) {
                see("logged-in");
            }
        });
        EventBus.GAME.addListener(MovementInputUpdateEvent.class, event -> see("movement-input"));
        EventBus.GAME.addListener(FovModifierEvent.class, event -> see("fov-modifier"));
        EventBus.GAME.addListener(SelectMusicEvent.class, event -> see("select-music"));
        EventBus.GAME.addListener(ClientTagsUpdatedEvent.class, event -> see("client-tags"));
        EventBus.GAME.addListener(ClientChatSendEvent.class, event -> {
            if (CHAT.equals(event.message())) {
                event.setCanceled(true);
                see("chat-send-canceled");
            }
        });
        EventBus.GAME.addListener(ClientChatReceivedEvent.class, event -> {
            if (event.message().getString().equals(SYSTEM)) {
                event.setCanceled(true);
                see("chat-received-canceled");
            }
        });
        EventBus.GAME.addListener(ToastAddEvent.class, event -> {
            if (event.toast() instanceof SystemToast toast && toast.getToken() == SystemToast.SystemToastId.WORLD_BACKUP) {
                event.setCanceled(true);
                see("toast-canceled");
            }
        });
        EventBus.GAME.addListener(net.weaver.api.game.event.client.FlipFrameEvent.class, event -> see("flip-frame"));
        EventBus.GAME.addListener(net.weaver.api.game.event.client.ExtractLevelRenderStateEvent.class, event -> see("extract-level-state"));
        EventBus.GAME.addListener(net.weaver.api.game.event.client.AddClientReloadListenersEvent.class, event ->
                event.addListener(new net.minecraft.server.packs.resources.SimplePreparableReloadListener<Object>() {
                    @Override
                    protected Object prepare(net.minecraft.server.packs.resources.ResourceManager manager,
                                             net.minecraft.util.profiling.ProfilerFiller profiler) {
                        return Boolean.TRUE;
                    }

                    @Override
                    protected void apply(Object prepared, net.minecraft.server.packs.resources.ResourceManager manager,
                                         net.minecraft.util.profiling.ProfilerFiller profiler) {
                        see("client-reload-listener");
                    }
                }));
        EventBus.GAME.addListener(net.weaver.api.game.event.client.RegisterParticleProvidersEvent.class, event -> see("particle-providers"));
        EventBus.GAME.addListener(net.weaver.api.game.event.client.TextureAtlasStitchedEvent.class, event -> see("atlas-stitched"));
        EventBus.GAME.addListener(net.weaver.api.game.event.client.ModelBakingCompletedEvent.class, event -> see("model-baking"));
        EventBus.GAME.addListener(net.weaver.api.game.event.client.ConfigureMainRenderTargetEvent.class, event -> see("main-target"));
        EventBus.GAME.addListener(net.weaver.api.game.event.client.AddDebugSubscriptionFlagsEvent.class, event -> see("debug-subscriptions"));
        EventBus.GAME.addListener(net.weaver.api.game.event.client.FrameGraphSetupEvent.class, event -> {
            if (event.builder() != null && event.targets() != null) {
                see("frame-graph");
            }
        });
        EventBus.GAME.addListener(net.weaver.api.game.event.client.PrepareRenderBuffersEvent.class, event -> see("prepare-buffers"));
        EventBus.GAME.addListener(net.weaver.api.game.event.client.AddSectionGeometryEvent.class, event -> {
            if (event.region() != null) {
                see("section-geometry");
            }
        });
        EventBus.GAME.addListener(net.weaver.api.game.event.client.ExtractBlockScreenEffectEvent.class, event -> see("screen-effect-" + event.type()));
        EventBus.GAME.addListener(net.weaver.api.game.event.client.GatherEffectScreenTooltipsEvent.class, event -> see("effect-tooltip"));
        EventBus.GAME.addListener(net.weaver.api.game.event.client.CreateSkullModelsEvent.class, event -> see("skull-models"));
        EventBus.GAME.addListener(net.weaver.api.game.event.client.ModifyBakingResultEvent.class, event -> {
            if (!event.blockStateModels().isEmpty() && !event.itemModels().isEmpty()) {
                see("baking-result");
            }
        });
        EventBus.GAME.addListener(net.weaver.api.game.event.client.RegisterFluidModelsEvent.class, event -> {
            if (event.models().containsKey(net.minecraft.world.level.material.Fluids.WATER)) {
                see("fluid-models");
            }
        });
        EventBus.GAME.addListener(net.weaver.api.game.event.client.RegisterPictureInPictureRenderersEvent.class, event -> {
            if (event.renderers().size() >= 5) {
                see("pip-renderers");
            }
        });
        EventBus.GAME.addListener(net.weaver.api.game.event.client.RegisterTextureAtlasesEvent.class, event -> {
            if (!event.atlases().isEmpty()) {
                see("texture-atlases");
            }
        });
        EventBus.GAME.addListener(net.weaver.api.game.event.client.RegisterRenderStateModifiersEvent.class, event ->
                event.register(net.minecraft.world.entity.Entity.class,
                        net.minecraft.client.renderer.entity.state.EntityRenderState.class,
                        (entity, state, partialTick) -> see("render-state-modified")));
        EventBus.GAME.addListener(net.weaver.api.game.event.world.DefaultDataComponentsBoundEvent.class, event -> see("components-bound-" + (event.client() ? "client" : "server")));
        EventBus.GAME.addListener(net.weaver.api.game.event.client.RegisterFeatureRenderersEvent.class, event ->
                event.register(ProofFeature.TYPE, new ProofFeature.Renderer()));
        EventBus.GAME.addListener(net.weaver.api.game.event.client.PrepareRenderBuffersEvent.class, event -> {
            net.minecraft.client.renderer.SubmitNodeCollection phases = event.phases();
            if (phases != null) {
                phases.solid.submit(new ProofFeature.Node());
            }
        });
        // Optional: depend on what the fixture world shows and on input.
        EventBus.GAME.addListener(net.weaver.api.game.event.client.HeartTypeEvent.class, event -> see("heart-type"));
        EventBus.GAME.addListener(net.weaver.api.game.event.client.SoundSourceEvent.class, event -> see("sound-source"));
        EventBus.GAME.addListener(net.weaver.api.game.event.client.RenderTooltipEvent.class, event -> see("tooltip-" + event.phase()));
        EventBus.GAME.addListener(RenderLivingEvent.class, event -> see("render-living"));
        EventBus.GAME.addListener(PlaySoundEvent.class, event -> see("play-sound"));
        EventBus.GAME.addListener(PlayLevelSoundEvent.class, event -> see("level-sound"));
        EventBus.GAME.addListener(ClientKeyInputEvent.class, event -> see("key-input"));
        EventBus.GAME.addListener(WindowResizeEvent.class, event -> see("window-resize"));
        EventBus.GAME.addListener(ClientDifficultyChangeEvent.class, event -> see("difficulty"));
        EventBus.GAME.addListener(ClientGameModeChangeEvent.class, event -> see("game-mode"));
    }

    /** Called every client tick from the proof's tick listener. */
    static void tick(Minecraft minecraft) {
        if (!INSTALLED.get() || REPORTED.get()) {
            return;
        }
        if (minecraft.player != null && minecraft.player.connection != null && DRIVEN.compareAndSet(false, true)) {
            try {
                minecraft.player.connection.sendChat(CHAT);
                minecraft.gui.chatListener().handleSystemMessage(Component.literal(SYSTEM), false);
                SystemToast.addOrUpdate(minecraft.gui.toastManager(), SystemToast.SystemToastId.WORLD_BACKUP,
                        Component.literal(TOAST), null);
            } catch (Throwable failure) {
                ClientProof.out("CLIENT PROOF GAME EVENTS ok=false reason=" + ClientProof.reason(failure));
                REPORTED.set(true);
                return;
            }
        }
        if ("Weaver mod resource pack".equals(net.minecraft.locale.Language.getInstance().getOrDefault("weaverclientproof.mod_pack"))) {
            see("mod-resource-pack");
        }
        // After the proof screen check, so the creative screen does not take its place.
        if (DRIVEN.get() && ProofState.screenShown() && !CREATIVE_DONE.get()) {
            String creative = ClientCreativeTabProof.tick(minecraft);
            if (creative != null && CREATIVE_DONE.compareAndSet(false, true)) {
                if ("ok".equals(creative)) {
                    see("creative-pages");
                } else {
                    ClientProof.out("CLIENT PROOF CREATIVE PAGES ok=false reason=" + creative);
                }
            }
        }
        // A screen whose tick throws must be closed by the crash shield while the game keeps running.
        if (CREATIVE_DONE.get() && SHIELD_OPENED.compareAndSet(false, true)) {
            shieldScreen = new net.minecraft.client.gui.screens.Screen(Component.literal("weaver shield proof")) {
                @Override
                public void tick() {
                    shieldThrew = true;
                    throw new IllegalStateException("weaver proof: a mod screen that throws");
                }
            };
            minecraft.gui.setScreen(shieldScreen);
        } else if (shieldScreen != null && shieldThrew && minecraft.gui.screen() != shieldScreen) {
            shieldScreen = null;
            see("screen-shield");
        }
        if (CREATIVE_DONE.get() && SHIELD_OPENED.get() && shieldScreen == null) {
            ClientRenderingProof.tick(minecraft);
            ClientRenderingProof.SEEN.forEach(ClientGameEventsProof::see);
            ClientGuiProof.tick(minecraft);
            ClientGuiProof.SEEN.forEach(ClientGameEventsProof::see);
        }
        if ("ok".equals(net.weaver.api.game.network.RegistrySyncCheck.lastResult())) {
            see("registry-check");
        }
        if (SEEN.keySet().containsAll(REQUIRED) && REPORTED.compareAndSet(false, true)) {
            ProofState.markGameEvents();
            ClientProof.out("CLIENT PROOF GAME EVENTS ok=true seen=" + new TreeSet<>(SEEN.keySet()));
        }
    }

    static String missing() {
        Set<String> missing = new TreeSet<>(REQUIRED);
        missing.removeAll(SEEN.keySet());
        return String.join("+", missing);
    }

    /** A mod feature type: vanilla groups its submitted nodes and hands them to the renderer. */
    static final class ProofFeature {
        static final net.minecraft.client.renderer.feature.FeatureRendererType<Node> TYPE =
                net.minecraft.client.renderer.feature.FeatureRendererType.create("weaverclientproof:feature");

        record Node() implements net.minecraft.client.renderer.feature.submit.SubmitNode {
            @Override
            public net.minecraft.client.renderer.feature.FeatureRendererType<Node> featureType() {
                return TYPE;
            }
        }

        static final class Renderer implements net.minecraft.client.renderer.feature.FeatureRenderer<Node> {
            @Override
            public void prepareGroup(net.minecraft.client.renderer.feature.FeatureFrameContext context,
                                     java.util.List<Node> nodes, boolean sorted) {
                if (!nodes.isEmpty()) {
                    see("feature-renderer");
                }
            }

            @Override
            public void executeGroup(net.minecraft.client.renderer.feature.FeatureFrameContext context,
                                     net.minecraft.client.renderer.oit.OitStage stage,
                                     com.mojang.renderpearl.api.commands.RenderPass pass, int group,
                                     java.util.List<Node> nodes, boolean sorted) {
            }
        }
    }

    private static void see(String name) {
        SEEN.putIfAbsent(name, Boolean.TRUE);
    }
}
