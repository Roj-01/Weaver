package net.weaver.api.game.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.game.client.render.BlockStructure;
import net.weaver.api.game.client.render.GuiBlockScene;

/**
 * A guide screen: pages of steps, each step a 3D scene of blocks with an
 * explanation, the way guide mods show how a machine is built and works.
 *
 * <pre>{@code
 * GuideScreen.builder(Component.literal("Water wheel"))
 *         .page(GuideScreen.page(Component.literal("Building it"))
 *                 .buildUp(wheel, Component.literal("Place the wheel layer by layer.")))
 *         .page(GuideScreen.page(Component.literal("Using it"))
 *                 .step(wheelWithWater, Component.literal("Flowing water turns it.")))
 *         .open();
 * }</pre>
 *
 * <ul>
 *   <li>Drag with the mouse to turn the scene, scroll to zoom.</li>
 *   <li>The arrow buttons (and the left and right keys) go to the previous and
 *       next step, across pages; "Replay" restarts the page.</li>
 *   <li>Steps with a duration advance by themselves while autoplay is on.</li>
 *   <li>{@link PageBuilder#buildUp} splits a structure into steps that add one
 *       layer at a time.</li>
 * </ul>
 */
public final class GuideScreen extends Screen {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFFA0A0A0;
    private static final int PANEL = 0x90000000;
    private static final int KEY_RIGHT = 262;
    private static final int KEY_LEFT = 263;

    /** One step: the scene to show and its explanation; {@code ticks} > 0 advances by itself after that long. */
    public record Step(BlockStructure scene, Component text, int ticks) {
        public Step {
            Objects.requireNonNull(scene, "scene");
            Objects.requireNonNull(text, "text");
        }
    }

    /** A page: a title and its steps. */
    public record Page(Component title, List<Step> steps) {
        public Page {
            Objects.requireNonNull(title, "title");
            steps = List.copyOf(steps);
            if (steps.isEmpty()) {
                throw new IllegalArgumentException("a page needs at least one step");
            }
        }
    }

    private final Screen parent;
    private final List<Page> pages;
    private int page;
    private int step;
    private int stepTicks;
    private boolean autoplay = true;
    private float yaw = 45.0F;
    private float pitch = 30.0F;
    private float zoom = 1.0F;
    private Button previousButton;
    private Button nextButton;

    private GuideScreen(Component title, List<Page> pages, Screen parent) {
        super(title);
        this.pages = List.copyOf(pages);
        this.parent = parent;
    }

    public static Builder builder(Component title) {
        return new Builder(title);
    }

    public static PageBuilder page(Component title) {
        return new PageBuilder(title);
    }

    /** The current page index. */
    public int pageIndex() {
        return page;
    }

    /** The current step index within the page. */
    public int stepIndex() {
        return step;
    }

    public List<Page> pages() {
        return pages;
    }

    /** The step shown now. */
    public Step currentStep() {
        return pages.get(page).steps().get(step);
    }

    /** Goes to the next step (or the next page's first); false at the very end. */
    public boolean next() {
        if (step + 1 < pages.get(page).steps().size()) {
            step++;
        } else if (page + 1 < pages.size()) {
            page++;
            step = 0;
        } else {
            return false;
        }
        stepTicks = 0;
        updateButtons();
        return true;
    }

    /** Goes to the previous step (or the previous page's last); false at the very start. */
    public boolean previous() {
        if (step > 0) {
            step--;
        } else if (page > 0) {
            page--;
            step = pages.get(page).steps().size() - 1;
        } else {
            return false;
        }
        stepTicks = 0;
        updateButtons();
        return true;
    }

    /** Restarts the current page. */
    public void replay() {
        step = 0;
        stepTicks = 0;
        autoplay = true;
        updateButtons();
    }

    @Override
    protected void init() {
        int y = height - 26;
        previousButton = addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            autoplay = false;
            previous();
        }).bounds(8, y, 20, 20).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            autoplay = false;
            next();
        }).bounds(32, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.translatableWithFallback("weaver.guide.replay", "Replay"),
                button -> replay()).bounds(56, y, 60, 20).build());
        addRenderableWidget(Button.builder(Component.translatableWithFallback("gui.done", "Done"), button -> onClose())
                .bounds(width - 68, y, 60, 20).build());
        updateButtons();
    }

    private void updateButtons() {
        if (previousButton != null) {
            previousButton.active = page > 0 || step > 0;
            nextButton.active = page + 1 < pages.size() || step + 1 < pages.get(page).steps().size();
        }
    }

    @Override
    public void tick() {
        super.tick();
        Step current = currentStep();
        if (autoplay && current.ticks() > 0 && ++stepTicks >= current.ticks()) {
            if (step + 1 < pages.get(page).steps().size()) {
                next();
            } else {
                autoplay = false;
            }
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (super.mouseDragged(event, dragX, dragY)) {
            return true;
        }
        yaw += (float) dragX * 1.5F;
        pitch = Math.max(-89.0F, Math.min(89.0F, pitch + (float) dragY * 1.5F));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        zoom = Math.max(0.3F, Math.min(4.0F, zoom * (scrollY > 0 ? 1.1F : 1.0F / 1.1F)));
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == KEY_RIGHT) {
            autoplay = false;
            return next() || true;
        }
        if (event.key() == KEY_LEFT) {
            autoplay = false;
            return previous() || true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        Page current = pages.get(page);
        Step shown = currentStep();
        graphics.centeredText(font, title, width / 2, 6, WHITE);
        graphics.centeredText(font, Component.empty().append(current.title()).append(Component.literal(
                "  (" + (page + 1) + "/" + pages.size() + ", " + (step + 1) + "/" + current.steps().size() + ")")),
                width / 2, 18, GREY);
        int textHeight = Math.max(30, font.split(shown.text(), width - 32).size() * 10 + 10);
        int sceneTop = 32;
        int sceneBottom = height - 34 - textHeight;
        int sceneLeft = 16;
        int sceneRight = width - 16;
        if (sceneBottom - sceneTop > 20) {
            // One scale for the whole page, so a structure built up layer by layer does not jump.
            float extent = 1.0F;
            for (Step each : current.steps()) {
                extent = Math.max(extent, extent(each.scene()));
            }
            float scale = Math.min(sceneRight - sceneLeft, sceneBottom - sceneTop) / (extent * 1.9F) * zoom;
            GuiBlockScene.draw(graphics, sceneLeft, sceneTop, sceneRight, sceneBottom, shown.scene(), yaw, pitch, scale);
        }
        int textTop = height - 30 - textHeight;
        graphics.fill(12, textTop, width - 12, textTop + textHeight - 4, PANEL);
        graphics.textWithWordWrap(font, FormattedText.composite(shown.text()), 18, textTop + 5, width - 36, WHITE);
    }

    private static float extent(BlockStructure structure) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : structure.blocks().keySet()) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return structure.blocks().isEmpty() ? 1.0F : Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)) + 1.0F;
    }

    /** Builds a {@link GuideScreen}. */
    public static final class Builder {
        private final Component title;
        private final List<Page> pages = new ArrayList<>();

        private Builder(Component title) {
            this.title = Objects.requireNonNull(title, "title");
        }

        public Builder page(Page page) {
            pages.add(Objects.requireNonNull(page, "page"));
            return this;
        }

        public Builder page(PageBuilder page) {
            return page(page.build());
        }

        /** The screen, returning to {@code parent} when closed. */
        public GuideScreen build(Screen parent) {
            if (pages.isEmpty()) {
                throw new IllegalStateException("a guide needs at least one page");
            }
            return new GuideScreen(title, pages, parent);
        }

        /** Opens the guide over the current screen. */
        public GuideScreen open() {
            Minecraft minecraft = Minecraft.getInstance();
            GuideScreen screen = build(minecraft.gui.screen());
            minecraft.setScreenAndShow(screen);
            return screen;
        }
    }

    /** Builds a {@link Page}. */
    public static final class PageBuilder {
        private final Component title;
        private final List<Step> steps = new ArrayList<>();

        private PageBuilder(Component title) {
            this.title = Objects.requireNonNull(title, "title");
        }

        /** A step shown until the player moves on. */
        public PageBuilder step(BlockStructure scene, Component text) {
            return step(scene, text, 0);
        }

        /** A step that moves on by itself after {@code ticks} while autoplay is on. */
        public PageBuilder step(BlockStructure scene, Component text, int ticks) {
            steps.add(new Step(scene, text, ticks));
            return this;
        }

        /**
         * Steps that build {@code structure} one layer (Y level) at a time, from the
         * bottom, each shown for {@code ticksPerLayer} (20 when 0 or less); the last
         * step is the whole structure and stays.
         */
        public PageBuilder buildUp(BlockStructure structure, Component text, int ticksPerLayer) {
            Map<Integer, List<Map.Entry<BlockPos, BlockState>>> layers = new TreeMap<>();
            for (Map.Entry<BlockPos, BlockState> entry : structure.blocks().entrySet()) {
                layers.computeIfAbsent(entry.getKey().getY(), y -> new ArrayList<>()).add(entry);
            }
            BlockStructure.Builder growing = BlockStructure.builder();
            int index = 0;
            for (List<Map.Entry<BlockPos, BlockState>> layer : layers.values()) {
                for (Map.Entry<BlockPos, BlockState> entry : layer) {
                    BlockPos pos = entry.getKey();
                    growing.block(pos.getX(), pos.getY(), pos.getZ(), entry.getValue());
                }
                index++;
                boolean last = index == layers.size();
                steps.add(new Step(growing.build(), text, last ? 0 : (ticksPerLayer > 0 ? ticksPerLayer : 20)));
            }
            if (layers.isEmpty()) {
                steps.add(new Step(structure, text, 0));
            }
            return this;
        }

        /** As {@link #buildUp(BlockStructure, Component, int)} with one second per layer. */
        public PageBuilder buildUp(BlockStructure structure, Component text) {
            return buildUp(structure, text, 20);
        }

        public Page build() {
            return new Page(title, steps);
        }
    }
}
