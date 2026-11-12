package net.weaver.clientproof;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.weaver.api.ModContext;
import net.weaver.api.WeaverMods;
import net.weaver.api.config.ConfigBuilder;
import net.weaver.api.config.ConfigManager;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.Side;
import net.weaver.api.game.client.gui.ConfigScreen;
import net.weaver.api.game.client.gui.ConfigScreens;
import net.weaver.api.game.client.gui.ModListScreen;
import net.weaver.api.game.client.level.LevelDrawRegistry;
import net.weaver.api.game.client.level.LevelDrawStage;
import net.weaver.api.game.event.client.ScreenInitEvent;
import net.weaver.core.config.WeaverConfig;

/**
 * The mod list, the generated settings screen, the "Mods" buttons, and the
 * fine-grained draw stages, in a real client.
 */
final class ClientGuiProof {
    static final Set<String> SEEN = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean SCREENS_CHECKED = new AtomicBoolean();
    private static final AtomicBoolean RIDER_CHECKED = new AtomicBoolean();
    private static volatile ConfigManager config;

    enum Mode { FAST, SLOW }

    private ClientGuiProof() {
    }

    /** From onInitialize: the proof's own configuration (so the mod list offers its settings). */
    static void installConfig(ModContext context) {
        try {
            ConfigBuilder builder = ConfigBuilder.create("weaverclientproof", Side.CLIENT)
                    .description("Client proof settings");
            builder.booleanValue("gui.enabled", true, "Whether the proof overlay is on");
            builder.intValue("gui.count", 4, 1, 16, "A bounded count");
            builder.enumValue("gui.mode", Mode.class, Mode.FAST, "A mode");
            builder.stringValue("gui.label", "hello", 1, 32, "A label");
            config = WeaverConfig.open(context, builder.build());
        } catch (RuntimeException failure) {
            ClientProof.out("CLIENT PROOF GUI CONFIG FAILED reason=" + failure);
        }
        // A player value the client may ask the server to change, within 0..3.
        net.weaver.api.capability.AttachmentRegistry.global().register(MODE);
        net.weaver.api.game.capability.PlayerDataRequests.allow(MODE, (player, mode) -> mode >= 0 && mode < 4);
    }

    static final net.weaver.api.capability.AttachmentType<Integer> MODE =
            net.weaver.api.capability.AttachmentType.builder("weaverclientproof:mode", Integer.class)
                    .codec(net.weaver.api.data.DataCodecs.varInt(), 5)
                    .build();
    private static final AtomicBoolean PAUSE_CANCELED = new AtomicBoolean();
    private static final AtomicBoolean CLIENT_COMMAND_RAN = new AtomicBoolean();
    private static final AtomicBoolean TRACKED = new AtomicBoolean();
    private static volatile net.minecraft.world.entity.animal.pig.Pig trackedPig;
    private static final AtomicBoolean HOLD_RUNNING = new AtomicBoolean();
    private static int extraStep;
    private static int extraTick;

    /** The cancelable pause change and the player data request, a few ticks apart. */
    private static void extras(Minecraft minecraft) {
        // The GUI scene proof uses the screen first; this one opens the pause menu after it.
        if (!ClientRenderingProof.SEEN.contains("gui-scene")) {
            return;
        }
        extraTick++;
        if (extraStep == -1 || extraStep == 0 && (minecraft.gui.screen() != null || minecraft.isPaused())) {
            // Another proof's screen may still be open (and pausing): close it and wait until the game runs.
            if (extraStep == 0) {
                minecraft.setScreenAndShow(null);
                extraStep = -1;
                extraTick = 0;
            } else if (extraTick >= 5 && !minecraft.isPaused()) {
                extraStep = 0;
            }
            return;
        }
        switch (extraStep) {
            case 0 -> {
                net.minecraft.client.server.IntegratedServer integrated = minecraft.getSingleplayerServer();
                if (integrated != null && minecraft.player != null) {
                    java.util.UUID id = minecraft.player.getUUID();
                    integrated.execute(() -> {
                        net.minecraft.server.level.ServerPlayer player = integrated.getPlayerList().getPlayer(id);
                        if (player != null) {
                            trackedPig = net.minecraft.world.entity.EntityTypes.PIG.spawn(player.level(),
                                    player.blockPosition().offset(2, 0, 0), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                        }
                    });
                }
                HOLD_RUNNING.set(true);
                ClientProof.out("CLIENT PROOF PAUSE START paused=" + minecraft.isPaused() + " screen=" + minecraft.gui.screen()
                        + " published=" + (minecraft.getSingleplayerServer() != null && minecraft.getSingleplayerServer().isPublished()));
                minecraft.setScreenAndShow(new PauseScreen(true));
                boolean sent = net.weaver.api.game.capability.PlayerDataRequests.request(MODE, 2)
                        && net.weaver.api.game.capability.PlayerDataRequests.request(MODE, 9);
                ClientProof.out("CLIENT PROOF PLAYER DATA REQUEST SENT ok=" + sent);
                extraStep = 1;
                extraTick = 0;
            }
            case 1 -> {
                if (extraTick < 20) {
                    return;
                }
                checkClientGaps(minecraft);
                boolean pauseKept = PAUSE_CANCELED.get() && !minecraft.isPaused();
                if (pauseKept) {
                    SEEN.add("pause-cancel");
                }
                HOLD_RUNNING.set(false);
                minecraft.setScreenAndShow(null);
                Integer mode = null;
                if (minecraft.getSingleplayerServer() != null && minecraft.player != null) {
                    net.minecraft.server.level.ServerPlayer serverPlayer = minecraft.getSingleplayerServer()
                            .getPlayerList().getPlayer(minecraft.player.getUUID());
                    mode = serverPlayer == null ? null : net.weaver.api.capability.AttachmentRegistry.global().get(serverPlayer, MODE);
                }
                boolean applied = Integer.valueOf(2).equals(mode);
                if (applied) {
                    SEEN.add("player-data-request");
                }
                ClientProof.out("CLIENT PROOF EXTRAS pauseCanceled=" + PAUSE_CANCELED.get() + " pauseKept=" + pauseKept
                        + " mode=" + mode);
                extraStep = 2;
                extraTick = 0;
            }
            case 2 -> {
                if (extraTick < 5) {
                    return;
                }
                if (TRACKED.get()) {
                    SEEN.add("entity-tracking");
                }
                extraStep = 3;
            }
            default -> {
            }
        }
    }

    /** Client commands, the raw mouse button event, and entity tracking, in a real client. */
    private static void checkClientGaps(Minecraft minecraft) {
        if (minecraft.getConnection() != null) {
            boolean merged = minecraft.getConnection().getCommands().getRoot().getChild("weaverproofcmd") != null;
            minecraft.getConnection().sendCommand("weaverproofcmd");
            boolean ran = CLIENT_COMMAND_RAN.get();
            ClientProof.out("CLIENT PROOF CLIENT COMMAND ok=" + (merged && ran) + " merged=" + merged + " ran=" + ran);
            if (merged && ran) {
                SEEN.add("client-command");
            }
        }
        long window = minecraft.getWindow().handle();
        minecraft.mouseHandler.onButton(window, new net.minecraft.client.input.MouseButtonInfo(7, 0), 1);
        minecraft.mouseHandler.onButton(window, new net.minecraft.client.input.MouseButtonInfo(7, 0), 0);
        net.minecraft.client.server.IntegratedServer integrated = minecraft.getSingleplayerServer();
        net.minecraft.world.entity.animal.pig.Pig pig = trackedPig;
        if (integrated != null && pig != null && minecraft.player != null) {
            java.util.UUID id = minecraft.player.getUUID();
            integrated.execute(() -> {
                net.minecraft.server.level.ServerPlayer player = integrated.getPlayerList().getPlayer(id);
                boolean tracking = player != null && net.weaver.api.game.network.EntityTracking.trackingPlayers(pig).contains(player);
                boolean connected = net.weaver.api.game.network.EntityTracking.connectionOf(id) != null;
                ClientProof.out("CLIENT PROOF ENTITY TRACKING ok=" + (tracking && connected) + " tracking=" + tracking
                        + " connected=" + connected);
                if (tracking && connected) {
                    TRACKED.set(true);
                }
                pig.discard();
            });
        }
    }

    /** From client setup: draw stages and the title screen button. */
    static void install() {
        for (LevelDrawStage stage : LevelDrawStage.values()) {
            String marker = "draw-" + stage.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
            LevelDrawRegistry.register("weaverclientproof:" + marker, stage, context -> {
                if (context.target() != null) {
                    SEEN.add(marker);
                }
            });
        }
        LevelDrawRegistry.register("weaverclientproof:instanced", LevelDrawStage.AFTER_SOLID, ClientGuiProof::drawInstances);
        net.weaver.api.game.client.command.ClientCommands.register(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.<net.minecraft.client.multiplayer.ClientSuggestionProvider>literal("weaverproofcmd")
                        .executes(context -> {
                            CLIENT_COMMAND_RAN.set(true);
                            return 1;
                        }));
        EventBus.GAME.addListener(net.weaver.api.game.event.client.ClientMouseButtonEvent.class, event -> {
            if (event.button() == 7 && event.phase() == net.weaver.api.game.event.client.ClientMouseButtonEvent.Phase.PRE) {
                SEEN.add("mouse-button");
                event.setCanceled(true);
            }
        });
        LevelDrawRegistry.register("weaverclientproof:instanced_glass", LevelDrawStage.AFTER_TRANSLUCENT, ClientGuiProof::drawGlass);
        LevelDrawRegistry.register("weaverclientproof:instanced_glow", LevelDrawStage.AFTER_LEVEL, ClientGuiProof::drawGlow);
        // While the proof holds it, pausing is canceled: the world keeps running behind the pause menu.
        // Vanilla decides the pause every tick, so the listener cancels each time.
        EventBus.GAME.addListener(net.weaver.api.game.event.client.ClientPauseChangingEvent.class, event -> {
            if (event.paused() && HOLD_RUNNING.get()) {
                PAUSE_CANCELED.set(true);
                event.setCanceled(true);
            }
        });
        EventBus.GAME.addListener(ScreenInitEvent.class, event -> {
            if (event.phase() == ScreenInitEvent.Phase.POST && event.screen() instanceof TitleScreen title
                    && modsButton(title) != null) {
                SEEN.add("mods-button-title");
            }
        });
    }

    private static net.weaver.api.game.client.render.InstancedMesh mesh;

    /** 400 spinning diamond cubes in front of the player, in one instanced draw call. */
    private static void drawInstances(LevelDrawRegistry.LevelDrawContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (mesh == null) {
            net.minecraft.client.renderer.texture.TextureAtlas atlas = (net.minecraft.client.renderer.texture.TextureAtlas)
                    minecraft.getTextureManager().getTexture(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);
            mesh = net.weaver.api.game.client.render.InstancedMesh.builder()
                    .box(0, 0, 0, 16, 16, 16, atlas.getSprite(net.minecraft.resources.Identifier.withDefaultNamespace("block/diamond_block")))
                    .build();
        }
        net.minecraft.world.phys.Vec3 look = minecraft.player.getLookAngle();
        net.minecraft.world.phys.Vec3 centre = minecraft.player.getEyePosition().add(look.scale(9.0));
        float spin = (minecraft.player.tickCount % 360) * 0.05F;
        net.weaver.api.game.client.render.InstancedMesh.Instances batch = mesh.instances(context);
        for (int i = 0; i < 20; i++) {
            for (int j = 0; j < 20; j++) {
                batch.add(centre.x + (i - 10) * 0.6, centre.y + (j - 10) * 0.6, centre.z,
                        new org.joml.Quaternionf().rotateY(spin + i * 0.2F).rotateX(j * 0.2F), 0.35F, 0xFFFFFFFF, 0xF000F0);
            }
        }
        if (batch.draw() && mesh.instancesDrawn() >= 400) {
            SEEN.add("instanced-mesh");
        }
    }

    private static net.weaver.api.game.client.render.InstancedMesh glass;
    private static net.weaver.api.game.client.render.InstancedMesh glow;

    /** A row of translucent stained glass cubes above the diamonds, blended over the world. */
    private static void drawGlass(LevelDrawRegistry.LevelDrawContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (glass == null) {
            net.minecraft.client.renderer.texture.TextureAtlas atlas = (net.minecraft.client.renderer.texture.TextureAtlas)
                    minecraft.getTextureManager().getTexture(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);
            glass = net.weaver.api.game.client.render.InstancedMesh.builder()
                    .material(net.weaver.api.game.client.render.InstancedMesh.Material.blocks()
                            .withBlend(net.weaver.api.game.client.render.InstancedMesh.Blend.TRANSLUCENT).withCull(false))
                    .box(0, 0, 0, 16, 16, 16, atlas.getSprite(net.minecraft.resources.Identifier.withDefaultNamespace("block/red_stained_glass")))
                    .build();
        }
        net.minecraft.world.phys.Vec3 centre = minecraft.player.getEyePosition().add(minecraft.player.getLookAngle().scale(9.0));
        net.weaver.api.game.client.render.InstancedMesh.Instances batch = glass.instances(context);
        for (int i = 0; i < 10; i++) {
            // Three blocks behind the diamond grid, overlapping its top rows: the diamonds must hide parts of it.
            net.minecraft.world.phys.Vec3 behind = centre.add(minecraft.player.getLookAngle().scale(3.0));
            batch.add(behind.x + (i - 5) * 1.2, behind.y + 4.0, behind.z, new org.joml.Quaternionf(), 1.0F, 0xFFFFFFFF, 0xF000F0);
        }
        if (batch.draw()) {
            SEEN.add("instanced-translucent");
        }
    }

    /** A row of glowing additive cubes below the diamonds, from a plain texture, in a pass of their own. */
    private static void drawGlow(LevelDrawRegistry.LevelDrawContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (glow == null) {
            glow = net.weaver.api.game.client.render.InstancedMesh.builder()
                    .material(net.weaver.api.game.client.render.InstancedMesh.Material
                            .texture(net.minecraft.resources.Identifier.withDefaultNamespace("textures/block/glowstone.png"))
                            .withBlend(net.weaver.api.game.client.render.InstancedMesh.Blend.ADDITIVE).withEmissive(true))
                    .box(0, 0, 0, 16, 16, 16, null)
                    .build();
        }
        net.minecraft.world.phys.Vec3 centre = minecraft.player.getEyePosition().add(minecraft.player.getLookAngle().scale(9.0));
        // In front of the diamonds, over their lower rows: the additive glow brightens what is behind it.
        net.minecraft.world.phys.Vec3 front = centre.subtract(minecraft.player.getLookAngle().scale(2.0));
        net.weaver.api.game.client.render.InstancedMesh.Instances batch = glow.instances(context);
        for (int i = 0; i < 6; i++) {
            batch.add(front.x + (i - 3) * 1.0, front.y - 1.5, front.z, new org.joml.Quaternionf(), 0.6F, 0xFFFFAA44, 0xF000F0);
        }
        if (context.pass() == null && batch.draw()) {
            SEEN.add("instanced-own-pass");
        }
    }


    /** An item whose stack changes do not play the first-person re-equip animation. */
    static final class SteadyItem extends net.minecraft.world.item.Item
            implements net.weaver.api.game.extension.ItemExtension, net.weaver.api.game.client.model.ClientItemExtension {
        @Override
        public net.minecraft.client.model.HumanoidModel.ArmPose getArmPose(net.minecraft.world.item.ItemStack stack,
                net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.InteractionHand hand) {
            return steadyPose();
        }

        static volatile boolean armorModelAsked;
        static volatile boolean armorModelPosed;
        private static net.minecraft.client.model.HumanoidModel<net.minecraft.client.renderer.entity.state.HumanoidRenderState> armorModel;

        @Override
        public net.minecraft.client.model.HumanoidModel<?> getHumanoidArmorModel(net.minecraft.world.item.ItemStack stack,
                net.minecraft.world.entity.EquipmentSlot slot, net.minecraft.client.model.HumanoidModel<?> original) {
            armorModelAsked = true;
            if (armorModel == null) {
                var root = Minecraft.getInstance().getEntityModels().bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER);
                armorModel = new net.minecraft.client.model.HumanoidModel<>(root) {
                    @Override
                    public void setupAnim(net.minecraft.client.renderer.entity.state.HumanoidRenderState state) {
                        armorModelPosed = true;
                        super.setupAnim(state);
                    }
                };
            }
            return armorModel;
        }

        SteadyItem(net.minecraft.world.item.Item.Properties properties) {
            super(properties);
        }

        @Override
        public boolean causesReequipAnimation(net.minecraft.world.item.ItemStack from, net.minecraft.world.item.ItemStack to) {
            return false;
        }

        @Override
        public boolean shouldResetBlockBreaking(net.minecraft.world.item.ItemStack from, net.minecraft.world.item.ItemStack to) {
            return false;
        }

        static volatile boolean highlighted;

        @Override
        public net.minecraft.network.chat.Component getHighlightTip(net.minecraft.world.item.ItemStack stack,
                net.minecraft.network.chat.Component name) {
            highlighted = true;
            return net.minecraft.network.chat.Component.literal("Steady ").append(name);
        }
    }

    private static SteadyItem steadyItem;

    /** A block that replaces its break and crack particles. */
    static final class QuietBlock extends net.minecraft.world.level.block.Block
            implements net.weaver.api.game.extension.BlockExtension {
        static volatile boolean destroyed;
        static volatile boolean hit;
        static volatile int soundAsks;

        @Override
        public net.minecraft.world.level.block.SoundType getSoundType(net.minecraft.world.level.block.state.BlockState state,
                net.minecraft.world.level.LevelReader level, net.minecraft.core.BlockPos pos, net.minecraft.world.entity.Entity entity) {
            soundAsks++;
            return net.minecraft.world.level.block.SoundType.AMETHYST;
        }

        QuietBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties properties) {
            super(properties);
        }

        @Override
        public boolean addDestroyEffects(net.minecraft.world.level.block.state.BlockState state,
                net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
            destroyed = true;
            return true;
        }

        @Override
        public boolean addHitEffects(net.minecraft.world.level.block.state.BlockState state,
                net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction side) {
            hit = true;
            return true;
        }
    }

    private static QuietBlock quietBlock;

    /** A see-through block that asks for the water overlay. */
    static final class OverlayGlass extends net.minecraft.world.level.block.Block
            implements net.weaver.api.game.extension.BlockExtension {
        static volatile boolean asked;

        @Override
        public boolean hidesNeighborFace(net.minecraft.world.level.block.state.BlockState state,
                net.minecraft.world.level.block.state.BlockState neighbor, net.minecraft.core.Direction side) {
            return true;
        }

        OverlayGlass(net.minecraft.world.level.block.state.BlockBehaviour.Properties properties) {
            super(properties);
        }

        @Override
        public boolean shouldDisplayFluidOverlay(net.minecraft.world.level.block.state.BlockState state,
                net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos,
                net.minecraft.world.level.material.FluidState fluid) {
            asked = true;
            return true;
        }
    }

    private static OverlayGlass overlayGlass;
    private static net.minecraft.world.entity.monster.zombie.Zombie armoredZombie;
    private static int armorTicks;

    /**
     * Puts a zombie wearing the steady item as a helmet in front of the camera, on the
     * client only, until the armor layer draws it with the item's own model.
     */
    private static void checkArmorModel(Minecraft minecraft) {
        if (SEEN.contains("armor-model") || armorTicks > 400) {
            return;
        }
        var level = minecraft.level;
        if (armorTicks++ == 0) {
            var helmet = new net.minecraft.world.item.ItemStack(steadyItem);
            helmet.set(net.minecraft.core.component.DataComponents.EQUIPPABLE,
                    net.minecraft.world.item.equipment.Equippable.builder(net.minecraft.world.entity.EquipmentSlot.HEAD)
                            .setAsset(net.minecraft.world.item.equipment.EquipmentAssets.IRON).build());
            armoredZombie = new net.minecraft.world.entity.monster.zombie.Zombie(net.minecraft.world.entity.EntityTypes.ZOMBIE, level);
            armoredZombie.setId(-1_000_020);
            armoredZombie.setNoAi(true);
            var look = minecraft.player.getLookAngle();
            armoredZombie.setPos(minecraft.player.position().add(look.x * 3.0, 0.0, look.z * 3.0));
            armoredZombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, helmet);
            SteadyItem.armorModelAsked = false;
            SteadyItem.armorModelPosed = false;
            level.addEntity(armoredZombie);
            return;
        }
        if (SteadyItem.armorModelPosed || armorTicks > 400) {
            ClientProof.out("CLIENT PROOF ARMOR MODEL asked=" + SteadyItem.armorModelAsked + " posed="
                    + SteadyItem.armorModelPosed + " ticks=" + armorTicks);
            level.removeEntity(armoredZombie.getId(), net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            if (SteadyItem.armorModelAsked && SteadyItem.armorModelPosed) {
                SEEN.add("armor-model");
            }
        }
    }
    private static net.minecraft.core.BlockPos overlayAt;
    private static int overlayTicks;

    /**
     * Puts the overlay glass next to water, on the client only, until the section is
     * rebuilt and the fluid renderer asks the glass; then clears both.
     */
    private static void checkFluidOverlay(Minecraft minecraft) {
        if (SEEN.contains("fluid-overlay") || overlayTicks > 400) {
            return;
        }
        var level = minecraft.level;
        if (overlayTicks++ == 0) {
            overlayAt = minecraft.player.blockPosition().offset(2, 1, 0);
            OverlayGlass.asked = false;
            level.setBlock(overlayAt, overlayGlass.defaultBlockState(), 3);
            level.setBlock(overlayAt.east(), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), 3);
            return;
        }
        if (OverlayGlass.asked || overlayTicks > 400) {
            ClientProof.out("CLIENT PROOF FLUID OVERLAY asked=" + OverlayGlass.asked + " ticks=" + overlayTicks);
            level.setBlock(overlayAt, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(overlayAt.east(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            if (OverlayGlass.asked) {
                SEEN.add("fluid-overlay");
            }
        }
    }

    /** A vehicle whose riders stand, and that counts when the client level tracks it. */
    static final class StandingPig extends net.minecraft.world.entity.animal.pig.Pig
            implements net.weaver.api.game.extension.EntityExtension {
        static volatile int added;
        static volatile int removed;

        StandingPig(net.minecraft.world.level.Level level) {
            super(net.minecraft.world.entity.EntityTypes.PIG, level);
        }

        @Override
        public boolean shouldRiderSit() {
            return false;
        }

        @Override
        public void onAddedToLevel() {
            added++;
        }

        @Override
        public void onRemovedFromLevel() {
            removed++;
        }
    }

    /** The arm pose the client proof adds to HumanoidModel.ArmPose (META-INF/enumextensions.json). */
    static net.minecraft.client.model.HumanoidModel.ArmPose steadyPose() {
        return net.weaver.api.EnumExtensions.get(net.minecraft.client.model.HumanoidModel.ArmPose.class, "WEAVERCLIENTPROOF_STEADY");
    }

    static volatile boolean poseApplied;

    /**
     * The steady item picks the added pose for a player and for a zombie, and the
     * pose's registered transformer moves the arm.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void checkArmPose(Minecraft minecraft) throws ReflectiveOperationException {
        var pose = steadyPose();
        net.weaver.api.game.client.model.ArmPoses.register(pose, (model, state, arm) -> {
            poseApplied = true;
            (arm == net.minecraft.world.entity.HumanoidArm.RIGHT ? model.rightArm : model.leftArm).xRot = -1.4F;
        });
        var stack = new net.minecraft.world.item.ItemStack(steadyItem);
        var avatarPose = net.minecraft.client.renderer.entity.player.AvatarRenderer.class.getDeclaredMethod("getArmPose",
                net.minecraft.world.entity.Avatar.class, net.minecraft.world.item.ItemStack.class, net.minecraft.world.InteractionHand.class);
        avatarPose.setAccessible(true);
        Object playerPose = avatarPose.invoke(null, minecraft.player, stack, net.minecraft.world.InteractionHand.MAIN_HAND);

        var zombie = new net.minecraft.world.entity.monster.zombie.Zombie(net.minecraft.world.entity.EntityTypes.ZOMBIE, minecraft.level);
        zombie.setId(-1_000_010);
        zombie.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        var renderer = (net.minecraft.client.renderer.entity.HumanoidMobRenderer) minecraft.getEntityRenderDispatcher().getRenderer(zombie);
        var mobPose = net.minecraft.client.renderer.entity.HumanoidMobRenderer.class.getDeclaredMethod("getArmPose",
                net.minecraft.world.entity.Mob.class, net.minecraft.world.entity.HumanoidArm.class);
        mobPose.setAccessible(true);
        Object zombiePose = mobPose.invoke(renderer, zombie, zombie.getMainArm());

        poseApplied = false;
        var model = (net.minecraft.client.model.HumanoidModel) renderer.getModel();
        var state = (net.minecraft.client.renderer.entity.state.HumanoidRenderState) renderer.createRenderState();
        state.rightArmPose = pose;
        state.mainArm = net.minecraft.world.entity.HumanoidArm.RIGHT;
        model.setupAnim(state);
        ClientProof.out("CLIENT PROOF ARM POSE player=" + playerPose + " zombie=" + zombiePose + " applied=" + poseApplied
                + " armX=" + model.rightArm.xRot);
        if (playerPose == pose && zombiePose == pose && poseApplied) {
            SEEN.add("arm-pose");
        }
    }

    /** A zombie riding a standing pig is drawn standing; on a plain pig it sits. */
    private static void checkRiderPose(Minecraft minecraft) {
        var level = minecraft.level;
        var standing = new StandingPig(level);
        var plain = new net.minecraft.world.entity.animal.pig.Pig(net.minecraft.world.entity.EntityTypes.PIG, level);
        var onStanding = new net.minecraft.world.entity.monster.zombie.Zombie(net.minecraft.world.entity.EntityTypes.ZOMBIE, level);
        var onPlain = new net.minecraft.world.entity.monster.zombie.Zombie(net.minecraft.world.entity.EntityTypes.ZOMBIE, level);
        // Client-made entities get no id of their own; these never reach the server.
        standing.setId(-1_000_001);
        plain.setId(-1_000_002);
        onStanding.setId(-1_000_003);
        onPlain.setId(-1_000_004);
        onStanding.startRiding(standing, true, false);
        onPlain.startRiding(plain, true, false);
        var standingState = new net.minecraft.client.renderer.entity.state.HumanoidRenderState();
        var plainState = new net.minecraft.client.renderer.entity.state.HumanoidRenderState();
        net.minecraft.client.renderer.entity.HumanoidMobRenderer.extractHumanoidRenderState(onStanding, standingState, 0.0F,
                minecraft.getItemModelResolver());
        net.minecraft.client.renderer.entity.HumanoidMobRenderer.extractHumanoidRenderState(onPlain, plainState, 0.0F,
                minecraft.getItemModelResolver());
        ClientProof.out("CLIENT PROOF RIDER POSE standingSits=" + standingState.isPassenger + " plainSits=" + plainState.isPassenger
                + " riding=" + onStanding.isPassenger());
        if (onStanding.isPassenger() && !standingState.isPassenger && plainState.isPassenger) {
            SEEN.add("rider-pose");
        }

        var tracked = new StandingPig(level);
        tracked.setId(-1_000_005);
        tracked.setPos(minecraft.player.position().add(0.0, 3.0, 0.0));
        StandingPig.added = 0;
        StandingPig.removed = 0;
        level.addEntity(tracked);
        int added = StandingPig.added;
        level.removeEntity(tracked.getId(), net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        int removed = StandingPig.removed;
        ClientProof.out("CLIENT PROOF ENTITY TRACKING added=" + added + " removed=" + removed);
        if (added == 1 && removed == 1) {
            SEEN.add("entity-tracking-extension");
        }
    }

    private static net.minecraft.world.item.ItemStack heldBeforeHighlight;
    private static int highlightTicks;

    /**
     * Holds the steady item until the HUD draws its name above the hotbar (which asks
     * the item), then puts back what was held.
     */
    private static void checkHighlight(Minecraft minecraft) {
        if (SEEN.contains("item-highlight") || highlightTicks > 200) {
            return;
        }
        var inventory = minecraft.player.getInventory();
        if (highlightTicks++ == 0) {
            heldBeforeHighlight = inventory.getSelectedItem().copy();
            SteadyItem.highlighted = false;
            inventory.setSelectedItem(new net.minecraft.world.item.ItemStack(steadyItem));
            return;
        }
        if (SteadyItem.highlighted || highlightTicks > 200) {
            ClientProof.out("CLIENT PROOF ITEM HIGHLIGHT asked=" + SteadyItem.highlighted + " ticks=" + highlightTicks);
            inventory.setSelectedItem(heldBeforeHighlight);
            if (SteadyItem.highlighted) {
                SEEN.add("item-highlight");
            }
        }
    }

    /** From the register phase. */
    static void registerSteadyItem() {
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("weaverclientproof", "steady_item"));
        steadyItem = net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.ITEM, key,
                new SteadyItem(new net.minecraft.world.item.Item.Properties().setId(key)));
        var blockKey = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BLOCK,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("weaverclientproof", "quiet_block"));
        quietBlock = net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.BLOCK, blockKey,
                new QuietBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().setId(blockKey)));
        var glassKey = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BLOCK,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("weaverclientproof", "overlay_glass"));
        overlayGlass = net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.BLOCK, glassKey,
                new OverlayGlass(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().setId(glassKey).noOcclusion()));
    }

    /**
     * Two stacks of the same item that differ in a component: vanilla plays the re-equip
     * animation (no instant replace) for sticks; the steady item replaces instantly.
     */
    private static void checkReequip(Minecraft minecraft) {
        try {
            var method = net.minecraft.client.player.FirstPersonHandsAndItems.class.getDeclaredMethod(
                    "shouldInstantlyReplaceVisibleItem", net.minecraft.world.item.ItemStack.class,
                    net.minecraft.world.item.ItemStack.class, net.minecraft.client.player.LocalPlayer.class);
            method.setAccessible(true);
            var hands = new net.minecraft.client.player.FirstPersonHandsAndItems();
            boolean steady = (boolean) method.invoke(hands, named(new net.minecraft.world.item.ItemStack(steadyItem), "a"),
                    named(new net.minecraft.world.item.ItemStack(steadyItem), "b"), minecraft.player);
            boolean stick = (boolean) method.invoke(hands, named(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK), "a"),
                    named(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK), "b"), minecraft.player);
            ClientProof.out("CLIENT PROOF ITEM REEQUIP steadyInstant=" + steady + " stickInstant=" + stick);
            if (steady && !stick) {
                SEEN.add("item-reequip");
            }
            boolean steadyKeeps = keepsMining(minecraft, new net.minecraft.world.item.ItemStack(steadyItem));
            boolean stickKeeps = keepsMining(minecraft, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK));
            ClientProof.out("CLIENT PROOF ITEM MINING steadyKeeps=" + steadyKeeps + " stickKeeps=" + stickKeeps);
            if (steadyKeeps && !stickKeeps) {
                SEEN.add("item-keeps-mining");
            }
            var quiet = quietBlock.defaultBlockState();
            var at = minecraft.player.blockPosition().above(3);
            QuietBlock.destroyed = false;
            QuietBlock.hit = false;
            minecraft.level.addDestroyBlockEffect(at, quiet);
            var crack = net.minecraft.client.multiplayer.ClientLevel.class.getDeclaredMethod("addBreakingParticles",
                    net.minecraft.core.BlockPos.class, net.minecraft.core.Direction.class,
                    net.minecraft.world.level.block.state.BlockState.class);
            crack.setAccessible(true);
            crack.invoke(minecraft.level, at, net.minecraft.core.Direction.UP, quiet);
            ClientProof.out("CLIENT PROOF BLOCK PARTICLES destroyed=" + QuietBlock.destroyed + " hit=" + QuietBlock.hit);
            if (QuietBlock.destroyed && QuietBlock.hit) {
                SEEN.add("block-particles");
            }
            QuietBlock.soundAsks = 0;
            minecraft.level.levelEvent(2001, at, net.minecraft.world.level.block.Block.getId(quiet));
            var hitSound = net.minecraft.client.multiplayer.ClientLevel.class.getDeclaredMethod("playBreakingSound",
                    net.minecraft.core.BlockPos.class, net.minecraft.world.level.block.state.BlockState.class);
            hitSound.setAccessible(true);
            hitSound.invoke(minecraft.level, at, quiet);
            var stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
            boolean hidden = !net.minecraft.world.level.block.Block.shouldRenderFace(stone, overlayGlass.defaultBlockState(),
                    net.minecraft.core.Direction.UP);
            boolean glassShows = net.minecraft.world.level.block.Block.shouldRenderFace(stone,
                    net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState(), net.minecraft.core.Direction.UP);
            ClientProof.out("CLIENT PROOF BLOCK SOUNDS asks=" + QuietBlock.soundAsks + " FACE hidden=" + hidden
                    + " glassShows=" + glassShows);
            if (QuietBlock.soundAsks >= 2) {
                SEEN.add("block-sounds");
            }
            if (hidden && glassShows) {
                SEEN.add("face-hiding");
            }
            checkArmPose(minecraft);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            ClientProof.out("CLIENT PROOF ITEM REEQUIP FAILED reason=" + failure);
        }
    }

    /**
     * Mining with {@code stack} (named "a"), then holding the same item named "b":
     * whether the game mode still counts it as the same mining target. The held
     * stack and the mining state are put back afterwards.
     */
    private static boolean keepsMining(Minecraft minecraft, net.minecraft.world.item.ItemStack stack)
            throws ReflectiveOperationException {
        var mode = minecraft.gameMode;
        Class<?> type = net.minecraft.client.multiplayer.MultiPlayerGameMode.class;
        var posField = type.getDeclaredField("destroyBlockPos");
        var itemField = type.getDeclaredField("destroyingItem");
        var same = type.getDeclaredMethod("sameDestroyTarget", net.minecraft.core.BlockPos.class);
        posField.setAccessible(true);
        itemField.setAccessible(true);
        same.setAccessible(true);
        Object oldPos = posField.get(mode);
        Object oldItem = itemField.get(mode);
        var inventory = minecraft.player.getInventory();
        var oldHeld = inventory.getSelectedItem();
        net.minecraft.core.BlockPos pos = minecraft.player.blockPosition().below();
        try {
            posField.set(mode, pos);
            itemField.set(mode, named(stack.copy(), "a"));
            inventory.setSelectedItem(named(stack.copy(), "b"));
            return (boolean) same.invoke(mode, pos);
        } finally {
            posField.set(mode, oldPos);
            itemField.set(mode, oldItem);
            inventory.setSelectedItem(oldHeld);
        }
    }

    private static net.minecraft.world.item.ItemStack named(net.minecraft.world.item.ItemStack stack, String name) {
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(name));
        return stack;
    }

    /** From the client tick, once a world is loaded. */
    static void tick(Minecraft minecraft) {
        if (minecraft.level == null) {
            return;
        }
        if (SCREENS_CHECKED.compareAndSet(false, true)) {
            try {
                checkModList(minecraft);
                checkConfigScreen(minecraft);
                checkPauseButton(minecraft);
                checkGuide(minecraft);
                checkReequip(minecraft);
            } catch (RuntimeException | LinkageError failure) {
                ClientProof.out("CLIENT PROOF GUI CHECK FAILED reason=" + failure);
            }
            if (SHOTS) {
                SEEN.remove("mods-button-pause");
            }
        }
        if (minecraft.player != null && SCREENS_CHECKED.get() && minecraft.gui.screen() == null) {
            checkHighlight(minecraft);
            checkFluidOverlay(minecraft);
            checkArmorModel(minecraft);
            if (RIDER_CHECKED.compareAndSet(false, true)) {
                try {
                    checkRiderPose(minecraft);
                } catch (RuntimeException | LinkageError failure) {
                    ClientProof.out("CLIENT PROOF RIDER CHECK FAILED reason=" + failure);
                }
            }
        }
        // Translucent particles in view, for the AFTER_PARTICLES draw stage.
        if (!SEEN.contains("draw-after-particles") && minecraft.player != null && minecraft.player.tickCount % 5 == 0) {
            var eye = minecraft.player.getEyePosition();
            var look = minecraft.player.getLookAngle();
            minecraft.level.addParticle(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    eye.x + look.x * 3, eye.y + look.y * 3, eye.z + look.z * 3, 0.0, 0.01, 0.0);
        }
        if (SHOTS) {
            screenshots(minecraft);
        } else {
            extras(minecraft);
        }
    }

    /** With WEAVER_PROOF_SHOTS=1: shows each screen and saves a screenshot of it in screenshots/. */
    private static final boolean SHOTS = "1".equals(System.getenv("WEAVER_PROOF_SHOTS"));
    private static int shotTick;
    private static int shot;

    private static void screenshots(Minecraft minecraft) {
        String[] names = {"weaver-modlist.png", "weaver-config.png", "weaver-pause.png", "weaver-title.png",
                "weaver-guide.png", "weaver-instanced.png"};
        if (shot >= names.length) {
            return;
        }
        if (shotTick == 0) {
            Screen screen = switch (shot) {
                case 0 -> new ModListScreen(null);
                case 1 -> ConfigScreens.create("weaverclientproof", null).orElse(null);
                case 2 -> new PauseScreen(true);
                case 3 -> new TitleScreen();
                case 4 -> guide();
                default -> null;
            };
            minecraft.setScreenAndShow(screen);
            if (screen instanceof ModListScreen list) {
                list.select("weaverclientproof");
            }
        }
        if (++shotTick == 40) {
            net.minecraft.client.Screenshot.grab(minecraft.gameDirectory, names[shot],
                    minecraft.gameRenderer.mainRenderTarget(), 1, message -> { });
            ClientProof.out("CLIENT PROOF SCREENSHOT " + names[shot]);
            shotTick = 0;
            shot++;
            if (shot == names.length) {
                minecraft.setScreenAndShow(null);
                SEEN.add("mods-button-pause");
            }
        }
    }

    private static void checkModList(Minecraft minecraft) {
        WeaverMods.Mod self = WeaverMods.find("weaverclientproof").orElse(null);
        ModListScreen screen = new ModListScreen(null);
        screen.init(minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        int all = screen.shownCount();
        screen.search("client proof");
        int filtered = screen.shownCount();
        boolean selected = screen.select("weaverclientproof");
        boolean settings = screen.settingsAvailable();
        boolean metadata = self != null && !self.description().isEmpty() && !self.authors().isEmpty()
                && "LGPL-2.1-only".equals(self.license());
        boolean ok = all == ModListScreen.entries().size() && all >= 3 && filtered >= 1 && filtered < all
                && selected && settings && metadata;
        ClientProof.out("CLIENT PROOF MOD LIST ok=" + ok + " all=" + all + " filtered=" + filtered
                + " selected=" + selected + " settings=" + settings + " metadata=" + metadata);
        if (ok) {
            SEEN.add("mod-list");
        }
    }

    private static void checkConfigScreen(Minecraft minecraft) {
        ConfigManager manager = config;
        Screen created = ConfigScreens.create("weaverclientproof", null).orElse(null);
        if (manager == null || !(created instanceof ConfigScreen screen)) {
            ClientProof.out("CLIENT PROOF CONFIG SCREEN ok=false reason=no screen manager=" + (manager != null));
            return;
        }
        screen.init(minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        boolean rows = screen.edits().size() == 4;
        screen.edit("gui.count", "99");
        boolean rejected = screen.problems().containsKey("gui.count") && !screen.save();
        boolean unchanged = ((Number) manager.value("gui.count").get()).intValue() == 4;
        screen.edit("gui.count", "7");
        screen.edit("gui.mode", "SLOW");
        screen.edit("gui.enabled", "false");
        boolean saved = screen.save();
        boolean applied = ((Number) manager.value("gui.count").get()).intValue() == 7
                && "SLOW".equals(manager.value("gui.mode").text())
                && Boolean.FALSE.equals(manager.value("gui.enabled").get());
        screen.resetToDefaults();
        boolean reset = screen.save() && ((Number) manager.value("gui.count").get()).intValue() == 4;
        boolean ok = rows && rejected && unchanged && saved && applied && reset;
        ClientProof.out("CLIENT PROOF CONFIG SCREEN ok=" + ok + " rows=" + rows + " rejected=" + rejected
                + " unchanged=" + unchanged + " saved=" + saved + " applied=" + applied + " reset=" + reset);
        if (ok) {
            SEEN.add("config-screen");
        }
    }

    /** A machine of three layers. */
    static net.weaver.api.game.client.render.BlockStructure machine() {
        net.weaver.api.game.client.render.BlockStructure.Builder builder = net.weaver.api.game.client.render.BlockStructure.builder();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                builder.block(x, 0, z, net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        builder.block(1, 1, 1, net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState());
        builder.block(0, 1, 1, net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState());
        builder.block(2, 1, 1, net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState());
        builder.block(1, 2, 1, net.minecraft.world.level.block.Blocks.HOPPER.defaultBlockState());
        return builder.build();
    }

    static net.weaver.api.game.client.gui.GuideScreen guide() {
        return net.weaver.api.game.client.gui.GuideScreen.builder(net.minecraft.network.chat.Component.literal("Smelter guide"))
                .page(net.weaver.api.game.client.gui.GuideScreen.page(net.minecraft.network.chat.Component.literal("Building the smelter"))
                        .buildUp(machine(), net.minecraft.network.chat.Component.literal(
                                "Lay a 3 by 3 floor of stone bricks, put the furnace in the middle with iron on both sides, and the hopper on top.")))
                .page(net.weaver.api.game.client.gui.GuideScreen.page(net.minecraft.network.chat.Component.literal("Using it"))
                        .step(machine(), net.minecraft.network.chat.Component.literal("Drop ore into the hopper; ingots come out of the furnace.")))
                .build(null);
    }

    private static void checkGuide(Minecraft minecraft) {
        net.weaver.api.game.client.gui.GuideScreen screen = guide();
        screen.init(minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        boolean layers = screen.pages().get(0).steps().size() == 3
                && screen.pages().get(0).steps().get(0).scene().blocks().size() == 9
                && screen.pages().get(0).steps().get(2).scene().blocks().size() == 13;
        boolean forward = screen.next() && screen.next() && screen.next() && screen.pageIndex() == 1 && !screen.next();
        boolean back = screen.previous() && screen.pageIndex() == 0 && screen.stepIndex() == 2;
        screen.replay();
        boolean replay = screen.stepIndex() == 0;
        boolean ok = layers && forward && back && replay;
        ClientProof.out("CLIENT PROOF GUIDE SCREEN ok=" + ok + " layers=" + layers + " forward=" + forward + " back=" + back
                + " replay=" + replay);
        if (ok) {
            SEEN.add("guide-screen");
        }
    }

    private static void checkPauseButton(Minecraft minecraft) {
        PauseScreen pause = new PauseScreen(true);
        pause.init(minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        if (modsButton(pause) != null) {
            SEEN.add("mods-button-pause");
        } else {
            ClientProof.out("CLIENT PROOF PAUSE MODS BUTTON ok=false");
        }
        // The title screen may have been shown before this proof was installed.
        TitleScreen title = new TitleScreen();
        title.init(minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        Button mods = modsButton(title);
        if (mods != null) {
            SEEN.add("mods-button-title");
        } else {
            ClientProof.out("CLIENT PROOF TITLE MODS BUTTON ok=false");
        }
    }

    private static Button modsButton(Screen screen) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button button && button.getMessage().getContents() instanceof TranslatableContents contents
                    && "weaver.mods.button".equals(contents.getKey())) {
                return button;
            }
        }
        return null;
    }
}
