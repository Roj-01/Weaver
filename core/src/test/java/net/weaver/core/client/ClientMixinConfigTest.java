package net.weaver.core.client;

import net.weaver.mixin.core.ConfigParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ClientMixinConfigTest
{
    private static final String CONFIG = "weaver.client.mixins.json";

    @Test
    void clientMixinConfigDeclaresTheBuiltInHooks() throws Exception
    {
        ClassLoader loader = ClientMixinConfigTest.class.getClassLoader();
        Class<?> mixinClass;
        try
        {
            mixinClass = Class.forName("net.weaver.api.game.client.mixin.TooltipMixin", false,
                    loader);
        }
        catch (ClassNotFoundException missing)
        {
            assumeTrue(false, "api-game is not on the test runtime classpath");
            return;
        }
        byte[] bytes;
        try (InputStream input = mixinClass.getResourceAsStream("/" + CONFIG))
        {
            assertTrue(input != null, "api-game mixin config resource is missing");
            bytes = input.readAllBytes();
        }

        ConfigParser.Parsed parsed = ConfigParser.parse("weaver-client", CONFIG, bytes, loader,
                "client");

        String pkg = "net.weaver.api.game.client.mixin.";
        assertTrue(parsed.mixinClasses().contains(pkg + "TooltipMixin"));
        assertTrue(parsed.mixinClasses().contains(pkg + "ClientChunkMixin"));
        assertTrue(parsed.mixinClasses().contains(pkg + "ClientCommandTreeMixin"));
        assertTrue(parsed.mixinClasses().contains(pkg + "WorldRenderMixin"));
        assertTrue(parsed.mixinClasses().contains(pkg + "HudLayerMixin"));
        assertTrue(parsed.mixinClasses().contains(pkg + "ArabicEditBoxMixin"));
        assertTrue(parsed.mixinClasses().contains(pkg + "HandRenderMixin"));
        assertTrue(parsed.mixinClasses().contains(pkg + "HandStateMixin"));
        assertTrue(parsed.mixinClasses().contains(pkg + "LevelRenderMixin"));
        assertTrue(parsed.mixinClasses().contains(pkg + "ScreenRenderMixin"));
        assertTrue(parsed.mixinClasses().contains(pkg + "HudStageMixin"));
        assertTrue(parsed.mixinClasses().contains(pkg + "GuiStageMixin"));
        assertTrue(parsed.mixinClasses().contains(pkg + "BlockColorMixin"));
        assertTrue(parsed.mixinClasses().contains(pkg + "FogRenderMixin"));
        assertTrue(parsed.mixinClasses().contains(pkg + "SkyRenderMixin"));
        assertTrue(parsed.mixinClasses().contains(pkg + "CameraRenderMixin"));
        assertTrue(parsed.mixinClasses().contains(pkg + "EntityRendererMixin"));
        assertTrue(parsed.mixinClasses().contains(pkg + "EntityLayerMixin"));
        assertTrue(parsed.mixinClasses().contains(pkg + "LivingEntityRendererInvoker"));
        assertTrue(parsed.mixinClasses().contains(pkg + "ModelLayerMixin"));

        Set<String> targets = parsed.targets();
        assertTrue(targets.contains("net.minecraft.client.gui.screens.Screen"));
        assertTrue(targets.contains("net.minecraft.client.multiplayer.ClientLevel"));
        assertTrue(targets.contains("net.minecraft.client.multiplayer.ClientPacketListener"));
        assertTrue(targets.contains("net.minecraft.client.renderer.LevelRenderer"));
        assertTrue(targets.contains("net.minecraft.client.gui.Hud"));
        assertTrue(targets.contains("net.minecraft.client.gui.components.EditBox"));
        assertTrue(targets.contains("net.minecraft.client.gui.Gui"));
        assertTrue(targets.contains(
                "net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer"));
        assertTrue(targets.contains("net.minecraft.client.player.FirstPersonHandsAndItems"));
        assertTrue(targets.contains("net.minecraft.client.color.block.BlockColors"));
        assertTrue(targets.contains("net.minecraft.client.renderer.fog.FogRenderer"));
        assertTrue(targets.contains("net.minecraft.client.renderer.SkyRenderer"));
        assertTrue(targets.contains("net.minecraft.client.Camera"));
        assertTrue(targets.contains("net.minecraft.client.renderer.entity.EntityRenderers"));
        assertTrue(targets.contains("net.minecraft.client.renderer.entity.LivingEntityRenderer"));
        assertTrue(targets.contains("net.minecraft.client.model.geom.LayerDefinitions"));
    }
}
