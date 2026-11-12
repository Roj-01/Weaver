package net.weaver.clientproof;

import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.weaver.api.event.EventBus;
import net.weaver.api.game.client.blockentity.BlockEntityRendererRegistry;
import net.weaver.api.game.client.render.BlockStructure;
import net.weaver.api.game.client.render.CustomRendering;
import net.weaver.api.game.client.render.GuiBlockScene;
import net.weaver.api.game.client.render.QuadMesh;
import net.weaver.api.game.client.render.WorldGeometry;
import net.weaver.api.game.event.client.PrepareRenderBuffersEvent;

/**
 * The rendering toolkit for machine mods: block entity renderers, world
 * geometry and blocks, code-built meshes, custom render types, post effects,
 * and 3D block scenes in screens.
 */
final class ClientRenderingProof {
    static final Set<String> SEEN = ConcurrentHashMap.newKeySet();
    private static final Identifier INVERT = Identifier.withDefaultNamespace("invert");
    private static final AtomicBoolean SCENE_OPENED = new AtomicBoolean();
    private static final AtomicInteger POST_FRAMES = new AtomicInteger();
    private static volatile Screen sceneScreen;
    private static final QuadMesh MESH = QuadMesh.builder().box(0, 0, 0, 0.5F, 0.5F, 0.5F).build();
    private static final BlockStructure STRUCTURE = BlockStructure.builder()
            .block(0, 0, 0, Blocks.STONE.defaultBlockState())
            .block(1, 0, 0, Blocks.OAK_PLANKS.defaultBlockState())
            .block(0, 1, 0, Blocks.GLASS.defaultBlockState())
            .build();

    private ClientRenderingProof() {
    }

    static void install() {
        // A custom model loader: read on the reload workers, baked when an item uses the model.
        net.weaver.api.game.client.model.ModelLoaderRegistry.register(
                Identifier.fromNamespaceAndPath("weaverclientproof", "test"), (json, context) -> {
                    SEEN.add("model-loader-read");
                    net.weaver.api.game.client.model.QuadGeometry quads = net.weaver.api.game.client.model.QuadGeometry.builder()
                            .quad("#all", net.minecraft.core.Direction.UP, null, -1, new float[][] {
                                    {0, 8, 16, 0, 16}, {16, 8, 16, 16, 16}, {16, 8, 0, 16, 0}, {0, 8, 0, 0, 0}})
                            .build();
                    return (slots, baker, state, name) -> {
                        SEEN.add("model-loader-baked");
                        return quads.bake(slots, baker, state, name);
                    };
                });
        EventBus.GAME.addListener(net.weaver.api.game.event.client.ModifyBakingResultEvent.class, event -> {
            if (event.itemModels().containsKey(Identifier.fromNamespaceAndPath("weaverclientproof", "slope"))
                    && event.itemModels().containsKey(Identifier.fromNamespaceAndPath("weaverclientproof", "loader_test"))) {
                SEEN.add("loader-item-models");
            }
        });
        // An item tint source: decoded when an item model names it.
        net.weaver.api.game.client.model.ModelLoaderRegistry.registerItemTintSource(
                Identifier.fromNamespaceAndPath("weaverclientproof", "energy"), ProofTint.CODEC);
        // A renderer for a block entity type vanilla does not draw.
        BlockEntityRendererRegistry.register(net.minecraft.world.level.block.entity.BlockEntityTypes.FURNACE, context -> new FurnaceGlow());
        EventBus.GAME.addListener(PrepareRenderBuffersEvent.class, event -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null || minecraft.player == null) {
                return;
            }
            SubmitNodeCollector collector = event.collector();
            PoseStack poses = new PoseStack();
            CameraRenderState camera = event.levelState().cameraRenderState;
            WorldGeometry.translateToWorld(poses, camera, minecraft.player.getX(), minecraft.player.getY() + 3, minecraft.player.getZ());
            WorldGeometry.geometry(collector, poses, RenderTypes.debugQuads(), (pose, consumer) -> SEEN.add("custom-geometry"));
            WorldGeometry.block(collector, poses, minecraft.level, minecraft.player.blockPosition().above(3),
                    Blocks.GOLD_BLOCK.defaultBlockState());
            STRUCTURE.submit(collector, poses, minecraft.level, minecraft.player.blockPosition().above(4));
            MESH.submit(collector, poses, RenderTypes.debugQuads(), WorldGeometry.FULL_BRIGHT, -1);
            SEEN.add("world-blocks");
        });
    }

    static void tick(Minecraft minecraft) {
        if (!SEEN.contains("ber-registered") && rendererRegistered(minecraft)) {
            SEEN.add("ber-registered");
        }
        if (!SEEN.contains("render-type")) {
            if (CustomRendering.renderType("weaverclientproof_quads",
                    RenderSetup.builder(net.minecraft.client.renderer.RenderPipelines.DEBUG_QUADS).createRenderSetup()) != null) {
                SEEN.add("render-type");
            }
        }
        if (minecraft.level != null && !SEEN.contains("post-effect")) {
            CustomRendering.enablePostEffect(INVERT);
            if (minecraft.gameRenderer.getRequestedPostEffects().contains(INVERT) && POST_FRAMES.incrementAndGet() > 2) {
                CustomRendering.disablePostEffect(INVERT);
                SEEN.add("post-effect");
            }
        }
        if (minecraft.level != null && SCENE_OPENED.compareAndSet(false, true)) {
            sceneScreen = new Screen(Component.literal("weaver scene proof")) {
                @Override
                public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
                    super.extractRenderState(graphics, mouseX, mouseY, partialTick);
                    GuiBlockScene.draw(graphics, 20, 20, 180, 140, STRUCTURE, 45F, 30F, 24F);
                }
            };
            minecraft.gui.setScreen(sceneScreen);
        } else if (sceneScreen != null && GuiBlockScene.SceneStats.count() > 0) {
            if (minecraft.gui.screen() == sceneScreen) {
                minecraft.gui.setScreen(null);
            }
            sceneScreen = null;
            SEEN.add("gui-scene");
        }
    }

    private static boolean rendererRegistered(Minecraft minecraft) {
        try {
            Field field = minecraft.getBlockEntityRenderDispatcher().getClass().getDeclaredField("renderers");
            field.setAccessible(true);
            Object renderers = field.get(minecraft.getBlockEntityRenderDispatcher());
            return renderers instanceof Map<?, ?> map && map.get(net.minecraft.world.level.block.entity.BlockEntityTypes.FURNACE) instanceof FurnaceGlow;
        } catch (ReflectiveOperationException failure) {
            return false;
        }
    }

    /** A tint source that colors the layer green. */
    record ProofTint() implements net.minecraft.client.color.item.ItemTintSource {
        static final com.mojang.serialization.MapCodec<ProofTint> CODEC = com.mojang.serialization.MapCodec.unit(ProofTint::decoded);

        private static ProofTint decoded() {
            SEEN.add("item-tint-source");
            return new ProofTint();
        }

        @Override
        public int calculate(net.minecraft.world.item.ItemStack stack, net.minecraft.client.multiplayer.ClientLevel level,
                net.minecraft.world.entity.LivingEntity entity) {
            return 0xFF00FF00;
        }

        @Override
        public com.mojang.serialization.MapCodec<ProofTint> type() {
            return CODEC;
        }
    }

    /** A do-nothing furnace renderer: the proof only checks it is installed. */
    static final class FurnaceGlow implements BlockEntityRenderer<FurnaceBlockEntity, BlockEntityRenderState> {
        @Override
        public BlockEntityRenderState createRenderState() {
            return new BlockEntityRenderState();
        }

        @Override
        public void submit(BlockEntityRenderState state, PoseStack poses, SubmitNodeCollector collector,
                           CameraRenderState camera) {
        }
    }
}
