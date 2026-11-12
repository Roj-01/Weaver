package net.weaver.api.game.creative;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.weaver.api.game.event.mixin.client.ContainerScreenAccessor;
import net.weaver.api.game.event.mixin.client.CreativeScreenInvoker;
import net.weaver.api.game.event.mixin.client.ScreenWidgetInvoker;

/**
 * The creative screen side of {@link CreativeTabPages}: page buttons, the page
 * label, and page changes. Kept out of the mixin because mixin code here does
 * not use lambdas. Client only.
 */
public final class CreativeScreenPages {
    private static final int BUTTON_SIZE = 20;
    private static final int BUTTON_OFFSET = 50;
    private static final int LABEL_OFFSET = 44;

    private CreativeScreenPages() {
    }

    /** Adds the previous and next page buttons when there is more than one page. */
    public static void addButtons(CreativeModeInventoryScreen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        if (CreativeTabPages.pageCount() <= 1 || minecraft.player == null || !minecraft.player.hasInfiniteMaterials()) {
            return;
        }
        ContainerScreenAccessor bounds = (ContainerScreenAccessor) screen;
        int left = bounds.weaverLeftPos();
        int top = bounds.weaverTopPos() - BUTTON_OFFSET;
        Button previous = Button.builder(Component.literal("<"), button -> turn(screen, -1))
                .bounds(left, top, BUTTON_SIZE, BUTTON_SIZE).build();
        Button next = Button.builder(Component.literal(">"), button -> turn(screen, 1))
                .bounds(left + bounds.weaverImageWidth() - BUTTON_SIZE, top, BUTTON_SIZE, BUTTON_SIZE).build();
        ScreenWidgetInvoker widgets = (ScreenWidgetInvoker) screen;
        widgets.weaverAddRenderableWidget(previous);
        widgets.weaverAddRenderableWidget(next);
    }

    /** Draws "page / count" above the screen when there is more than one page. */
    public static void drawLabel(CreativeModeInventoryScreen screen, GuiGraphicsExtractor graphics) {
        int count = CreativeTabPages.pageCount();
        if (count <= 1) {
            return;
        }
        ContainerScreenAccessor bounds = (ContainerScreenAccessor) screen;
        Component label = Component.literal((CreativeTabPages.currentPage() + 1) + " / " + count);
        graphics.centeredText(Minecraft.getInstance().font, label,
                bounds.weaverLeftPos() + bounds.weaverImageWidth() / 2, bounds.weaverTopPos() - LABEL_OFFSET,
                0xFFFFFFFF);
    }

    /**
     * Turns {@code delta} pages (wrapping around, skipping pages with no tab
     * to show) and selects the first shown tab there, so the selected tab is
     * always on the current page.
     */
    public static void turn(CreativeModeInventoryScreen screen, int delta) {
        int count = CreativeTabPages.pageCount();
        if (count <= 1) {
            return;
        }
        // Skip pages whose tabs are all hidden; page 1 always has a vanilla tab.
        int page = CreativeTabPages.currentPage();
        for (int step = 0; step < count; step++) {
            page = Math.floorMod(page + delta, count);
            CreativeModeTab first = firstShown(page);
            if (first != null) {
                CreativeTabPages.setCurrentPage(page);
                ((CreativeScreenInvoker) screen).weaverSelectTab(first);
                return;
            }
        }
    }

    /** Keeps the current page on the selected tab; right-aligned vanilla tabs show on every page. */
    public static void follow(CreativeModeTab selected) {
        if (selected == null || CreativeTabPages.pageCount() <= 1) {
            return;
        }
        if (CreativeTabPages.isModTab(selected) || !selected.isAlignedRight()) {
            CreativeTabPages.setCurrentPage(CreativeTabPages.pageOf(selected));
        }
    }

    private static CreativeModeTab firstShown(int page) {
        if (page == 0) {
            return CreativeModeTabs.getDefaultTab();
        }
        for (CreativeModeTab tab : CreativeTabPages.tabsOn(page)) {
            if (tab.shouldDisplay()) {
                return tab;
            }
        }
        return null;
    }
}
