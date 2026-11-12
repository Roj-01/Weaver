package net.weaver.api.game.client.render;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.weaver.api.game.client.mixin.GuiGraphicsExtractorAccessor;
import org.joml.Quaternionf;

/**
 * Draws a {@link BlockStructure} as a rotating 3D scene inside a screen, the
 * building block of guide and tutorial screens (like Create's Ponder): a small
 * machine shown from any angle, with your own text and buttons around it.
 *
 * <pre>{@code
 * // in a Screen's extractRenderState(graphics, mouseX, mouseY, partialTick):
 * GuiBlockScene.draw(graphics, left, top, left + 160, top + 120, scene, yaw, 30F, 20F);
 * }</pre>
 *
 * Yaw and pitch are in degrees; {@code scale} is pixels per block. Lighting is a
 * fixed full brightness (there is no world around the scene). Animate by
 * changing yaw or by drawing a different structure each frame.
 */
public final class GuiBlockScene {
    private GuiBlockScene() {
    }

    /** Adds the scene to this frame of the screen, clipped to the current scissor. */
    public static void draw(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, BlockStructure structure,
            float yawDegrees, float pitchDegrees, float scale) {
        GuiGraphicsExtractorAccessor accessor = (GuiGraphicsExtractorAccessor) graphics;
        ScreenRectangle scissor = currentScissor(graphics);
        ScreenRectangle bounds = PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissor);
        accessor.weaver$guiRenderState().addPicturesInPictureState(new State(structure, yawDegrees, pitchDegrees,
                x0, y0, x1, y1, scale, scissor, bounds));
    }

    private static java.lang.reflect.Field scissorField;
    private static java.lang.reflect.Method peek;

    /** The clip rectangle the screen set (scrolling lists), or null for none. */
    private static ScreenRectangle currentScissor(GuiGraphicsExtractor graphics) {
        try {
            if (scissorField == null) {
                java.lang.reflect.Field field = GuiGraphicsExtractor.class.getDeclaredField("scissorStack");
                field.setAccessible(true);
                java.lang.reflect.Method method = field.getType().getDeclaredMethod("peek");
                method.setAccessible(true);
                peek = method;
                scissorField = field;
            }
            return (ScreenRectangle) peek.invoke(scissorField.get(graphics));
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return null;
        }
    }

    /** One scene in one frame. */
    public record State(BlockStructure structure, float yaw, float pitch, int x0, int y0, int x1, int y1, float scale,
            ScreenRectangle scissorArea, ScreenRectangle bounds) implements PictureInPictureRenderState {
    }

    /** Renders {@link State}s; added to the GUI renderer by the loader. */
    public static final class Renderer extends PictureInPictureRenderer<State> {
        @Override
        public Class<State> getRenderStateClass() {
            return State.class;
        }

        @Override
        protected void renderToTexture(State state, PoseStack poses, SubmitNodeCollector collector) {
            Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
            double[] centre = state.structure().centre();
            poses.rotate(new Quaternionf()
                    .rotateZ((float) Math.PI)
                    .rotateX((float) Math.toRadians(-state.pitch()))
                    .rotateY((float) Math.toRadians(state.yaw())));
            poses.translate(-centre[0], -centre[1], -centre[2]);
            state.structure().submitWithLight(collector, poses, WorldGeometry.FULL_BRIGHT);
            SceneStats.rendered();
        }

        @Override
        protected String getTextureLabel() {
            return "weaver block scene";
        }

        /** The structure's centre at the middle of the box (vanilla's default puts it at the bottom edge). */
        @Override
        protected float getTranslateY(int height, int guiScale) {
            return height / 2.0F;
        }
    }

    /** For the loader's proofs: how many scenes were drawn. */
    public static final class SceneStats {
        private static volatile long rendered;

        private SceneStats() {
        }

        static void rendered() {
            rendered++;
        }

        public static long count() {
            return rendered;
        }
    }
}
