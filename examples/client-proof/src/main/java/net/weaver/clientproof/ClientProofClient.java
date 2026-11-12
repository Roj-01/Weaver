package net.weaver.clientproof;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.weaver.api.ModContext;
import net.weaver.api.capability.AttachmentRegistry;
import net.weaver.api.client.ClientRegistry;
import net.weaver.api.command.CommandRegistry;
import net.weaver.api.command.CommandTreeSnapshot;
import net.weaver.api.client.ClientTickContext;
import net.weaver.api.client.ClientTickListener;
import net.weaver.api.client.HudLayer;
import net.weaver.api.client.HudRenderContext;
import net.weaver.api.client.KeyMappingSpec;
import net.weaver.api.client.ScreenFactory;
import net.weaver.api.client.TooltipContext;
import net.weaver.api.client.TooltipProvider;
import net.weaver.api.game.client.color.BlockColorRegistry;
import net.weaver.api.game.client.fog.FogRenderContext;
import net.weaver.api.game.client.fog.FogRenderListener;
import net.weaver.api.game.client.fog.FogRenderRegistry;
import net.weaver.api.game.client.hand.HandRenderContext;
import net.weaver.api.game.client.hand.HandRenderListener;
import net.weaver.api.game.client.hand.HandRenderPhase;
import net.weaver.api.game.client.hand.HandRenderRegistry;
import net.weaver.api.game.client.hand.HandRenderResult;
import net.weaver.api.game.client.hud.HudRenderListener;
import net.weaver.api.game.client.hud.HudRenderRegistry;
import net.weaver.api.game.client.hud.HudRenderStage;
import net.weaver.api.game.client.screen.ScreenRenderContext;
import net.weaver.api.game.client.screen.ScreenRenderListener;
import net.weaver.api.game.client.screen.ScreenRenderPhase;
import net.weaver.api.game.client.screen.ScreenRenderRegistry;
import net.weaver.api.game.client.screen.ScreenRenderResult;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

final class ClientProofClient
{
    private static final String SCREEN_ID = "weaverclientproof:screen";
    private static final String HUD_ID = "weaverclientproof:hud";
    private static final String TOOLTIP_ID = "weaverclientproof:tooltip";
    private static final String HUD_STAGE_ID = "weaverclientproof:hud-stage";
    private static final String SCREEN_PHASE_ID = "weaverclientproof:screen-phase";
    private static final String HAND_RENDER_ID = "weaverclientproof:hand-render";
    private static final String FOG_ID = "weaverclientproof:fog";
    private static final String TOOLTIP_TEXT = "Weaver client proof tooltip";
    private static final int KEY_F6 = 295;
    private static final long CLIENT_DEADLINE_MILLIS = 240_000L;

    private static final AtomicBoolean SETUP = new AtomicBoolean();
    private static final AtomicBoolean KEY_ATTEMPTED = new AtomicBoolean();
    private static final AtomicBoolean KEY_REGISTERED = new AtomicBoolean();
    private static final AtomicLong KEY_REGISTERED_TICK = new AtomicLong(-1L);
    private static final AtomicBoolean CLICK_SENT = new AtomicBoolean();
    private static final AtomicBoolean TOOLTIP_ATTEMPTED = new AtomicBoolean();
    private static final AtomicBoolean CAPABILITY_ERROR = new AtomicBoolean();

    private ClientProofClient()
    {
    }

    static void install(ModContext context)
    {
        if (!ClientProof.clientClassPresent())
        {
            return;
        }
        if (!SETUP.compareAndSet(false, true))
        {
            return;
        }
        ProofState.clientDeadline(System.currentTimeMillis() + CLIENT_DEADLINE_MILLIS);
        try
        {
            registerRenderProofs();
            ClientGameEventsProof.install();
            ClientRegistry.registerScreenFactory(new ScreenFactory()
            {
                @Override
                public String id()
                {
                    return SCREEN_ID;
                }

                @Override
                public Object createScreen(Object minecraft, Object parentScreen)
                {
                    return new ProofScreen();
                }
            });
            ClientRegistry.registerHudLayer(new HudLayer()
            {
                @Override
                public String id()
                {
                    return HUD_ID;
                }

                @Override
                public int order()
                {
                    return 100;
                }

                @Override
                public void render(HudRenderContext renderContext)
                {
                    Object graphicsObject = renderContext.graphics();
                    Object minecraftObject = renderContext.minecraft();
                    if (!(graphicsObject instanceof GuiGraphicsExtractor graphics)
                            || !(minecraftObject instanceof Minecraft minecraft))
                    {
                        return;
                    }
                    graphics.text(minecraft.font, "Weaver", 4, 4, 0xFFFFFFFF);
                    if (ProofState.markHud())
                    {
                        ClientProof.out("CLIENT PROOF HUD ok=true");
                    }
                }
            });
            ClientRegistry.registerTooltipProvider(new TooltipProvider()
            {
                @Override
                public String id()
                {
                    return TOOLTIP_ID;
                }

                @Override
                public void append(TooltipContext tooltipContext, List<String> lines)
                {
                    lines.add(TOOLTIP_TEXT);
                }
            });
            ClientRegistry.registerTickListener(new ClientTickListener()
            {
                @Override
                public void onTick(ClientTickContext tickContext)
                {
                    ClientProofClient.onClientTick(tickContext);
                }
            });
            ClientProof.out("CLIENT PROOF CLIENT SETUP ok=true");
        }
        catch (Throwable failure)
        {
            ClientProof.out("CLIENT PROOF CLIENT SETUP ok=false reason="
                    + ClientProof.reason(failure));
            ProofState.clientDeadline(System.currentTimeMillis());
        }
    }

    private static void registerRenderProofs()
    {
        HudRenderRegistry.register(new HudRenderListener()
        {
            @Override
            public String id()
            {
                return HUD_STAGE_ID;
            }

            @Override
            public void onRender(net.weaver.api.game.client.hud.HudRenderContext context)
            {
                if (context.stage() == HudRenderStage.POST_HUD
                        && ProofState.markHudStage())
                {
                    ClientProof.out("CLIENT PROOF HUD STAGE ok=true stage=post_hud");
                }
            }
        });
        ScreenRenderRegistry.register(new ScreenRenderListener()
        {
            @Override
            public String id()
            {
                return SCREEN_PHASE_ID;
            }

            @Override
            public ScreenRenderResult onScreenRender(ScreenRenderContext context)
            {
                if (context.phase() == ScreenRenderPhase.PRE
                        && context.screen() instanceof ProofScreen
                        && ProofState.markScreenPhase())
                {
                    ClientProof.out("CLIENT PROOF SCREEN PHASE ok=true phase=pre");
                }
                return ScreenRenderResult.CONTINUE;
            }
        });
        HandRenderRegistry.register(new HandRenderListener()
        {
            @Override
            public String id()
            {
                return HAND_RENDER_ID;
            }

            @Override
            public HandRenderResult onRender(HandRenderContext context)
            {
                if (context.phase() == HandRenderPhase.PRE && context.perHand()
                        && ProofState.markHandRender())
                {
                    ClientProof.out("CLIENT PROOF HAND RENDER ok=true hand="
                            + context.hand());
                }
                return HandRenderResult.CONTINUE;
            }
        });
        FogRenderRegistry.register(new FogRenderListener()
        {
            @Override
            public String id()
            {
                return FOG_ID;
            }

            @Override
            public void onSetupFog(FogRenderContext context)
            {
                if (ProofState.markFog())
                {
                    context.data().environmentalStart += 0.001F;
                    ClientProof.out("CLIENT PROOF FOG ok=true");
                }
            }
        });
        BlockColorRegistry.register(Blocks.SHORT_GRASS, BlockTintSources.grass());
        if (ProofState.markBlockColor())
        {
            ClientProof.out("CLIENT PROOF BLOCK COLOR REGISTERED ok=true block=short_grass");
        }
    }

    private static void onClientTick(ClientTickContext context)
    {
        long now = System.currentTimeMillis();
        Minecraft minecraft;
        try
        {
            minecraft = Minecraft.getInstance();
        }
        catch (Throwable failure)
        {
            return;
        }
        if (minecraft == null)
        {
            finishIfDue(now);
            return;
        }
        if (!KEY_REGISTERED.get() && !KEY_ATTEMPTED.get() && minecraft.options != null
                && minecraft.level != null)
        {
            registerKeyMapping(context.tick());
        }
        ClientGameEventsProof.tick(minecraft);
        if (!TOOLTIP_ATTEMPTED.get() && minecraft.level != null)
        {
            checkTooltip(minecraft);
        }
        if (minecraft.player != null)
        {
            try
            {
                String synced = AttachmentRegistry.global().get(minecraft.player,
                        ClientProof.syncCapability());
                if ("synced".equals(synced) && ProofState.markCapabilitySync())
                {
                    ClientProof.out("CLIENT PROOF CAPABILITY SYNC ok=true");
                }
            }
            catch (Throwable failure)
            {
                if (CAPABILITY_ERROR.compareAndSet(false, true))
                {
                    ClientProof.out("CLIENT PROOF CAPABILITY SYNC ok=false reason="
                            + ClientProof.reason(failure));
                }
            }
        }
        if (minecraft.level != null && !ProofState.commandTree())
        {
            checkCommandTree();
        }
        if (KEY_REGISTERED.get() && ProofState.hud() && !CLICK_SENT.get())
        {
            long registeredTick = KEY_REGISTERED_TICK.get();
            if (registeredTick >= 0L && context.tick() >= registeredTick + 5L
                    && CLICK_SENT.compareAndSet(false, true))
            {
                KeyMapping.click(InputConstants.Type.KEYBOARD.getOrCreate(KEY_F6));
            }
        }
        finishIfDue(now);
    }

    private static void registerKeyMapping(long tick)
    {
        if (!KEY_ATTEMPTED.compareAndSet(false, true))
        {
            return;
        }
        try
        {
            KeyMappingSpec spec = KeyMappingSpec.builder("weaverclientproof", "open_screen",
                            KEY_F6)
                    .screenFactory(SCREEN_ID)
                    .build();
            ClientRegistry.registerKeyMapping(spec, () ->
            {
                if (ProofState.markKeybind())
                {
                    ClientProof.out("CLIENT PROOF KEYBIND ok=true");
                }
                ClientRegistry.openScreen(SCREEN_ID);
            });
            KEY_REGISTERED.set(true);
            KEY_REGISTERED_TICK.set(tick);
            ClientProof.out("CLIENT PROOF KEYBIND REGISTERED ok=true");
        }
        catch (Throwable failure)
        {
            ClientProof.out("CLIENT PROOF KEYBIND REGISTERED ok=false reason="
                    + ClientProof.reason(failure));
        }
    }

    private static void checkTooltip(Minecraft minecraft)
    {
        if (!TOOLTIP_ATTEMPTED.compareAndSet(false, true))
        {
            return;
        }
        try
        {
            List<Component> lines = Screen.getTooltipFromItem(minecraft,
                    new ItemStack(Items.STONE));
            boolean found = false;
            StringBuilder actual = new StringBuilder();
            for (Component line : lines)
            {
                String text = line.getString();
                if (actual.length() > 0)
                {
                    actual.append('|');
                }
                actual.append(text);
                if (text.contains(TOOLTIP_TEXT))
                {
                    found = true;
                }
            }
            if (found)
            {
                ProofState.markTooltip();
                ClientProof.out("CLIENT PROOF TOOLTIP ok=true");
            }
            else
            {
                ClientProof.out("CLIENT PROOF TOOLTIP ok=false lines=" + actual);
            }
        }
        catch (Throwable failure)
        {
            ClientProof.out("CLIENT PROOF TOOLTIP ok=false reason="
                    + ClientProof.reason(failure));
        }
    }

    private static void checkCommandTree()
    {
        try
        {
            CommandTreeSnapshot snapshot = CommandRegistry.treeSnapshot();
            if (snapshot == null || !"client".equals(snapshot.source()))
            {
                return;
            }
            if (ProofState.markCommandTree())
            {
                ClientProof.out("CLIENT PROOF COMMAND TREE ok=true source=client names="
                        + snapshot.commandNames().size());
            }
        }
        catch (Throwable failure)
        {
            ClientProof.out("CLIENT PROOF COMMAND TREE ok=false reason="
                    + ClientProof.reason(failure));
        }
    }

    private static void finishIfDue(long now)
    {
        if (ProofState.clientOk())
        {
            if (ProofState.claimClientResult())
            {
                ClientProof.out("CLIENT PROOF RESULT ok=true side=client");
            }
            return;
        }
        long deadline = ProofState.clientDeadline();
        if (deadline > 0L && now >= deadline && ProofState.claimClientResult())
        {
            ClientProof.out("CLIENT PROOF RESULT ok=false side=client missing="
                    + ProofState.clientMissing());
        }
    }
}
