package net.weaver.clientproof;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Items;
import net.weaver.api.game.creative.CreativeScreenPages;
import net.weaver.api.game.creative.CreativeTabPages;

/**
 * Drives the creative screen through its pages: open it, turn to page 2 (the
 * mod tab from {@link ProofCreativeTab} gets selected), turn again (wraps to
 * page 1), close. The player gets creative building client-side only for the
 * check. Client only.
 */
final class ClientCreativeTabProof {
    private static final int WAIT_TICKS = 10;
    private static int step;
    private static boolean instabuild;

    private ClientCreativeTabProof() {
    }

    /**
     * One step per client tick. Returns {@code "ok"} when done, a failure
     * reason, or {@code null} while still running.
     */
    static String tick(Minecraft minecraft) {
        CreativeModeTab tab = ProofCreativeTab.tab();
        if (minecraft.player == null || minecraft.player.connection == null || tab == null) {
            return null;
        }
        int current = step++;
        if (current < WAIT_TICKS) {
            return null;
        }
        if (current == WAIT_TICKS) {
            if (CreativeTabPages.pageCount() != 2 || CreativeTabPages.pageOf(tab) != 1) {
                return "pages=" + CreativeTabPages.pageCount() + " tabPage=" + CreativeTabPages.pageOf(tab);
            }
            instabuild = minecraft.player.getAbilities().instabuild;
            minecraft.player.getAbilities().instabuild = true;
            minecraft.gui.setScreen(new CreativeModeInventoryScreen(minecraft.player,
                    minecraft.player.connection.enabledFeatures(), false));
            return null;
        }
        if (current == WAIT_TICKS + 3) {
            if (!(minecraft.gui.screen() instanceof CreativeModeInventoryScreen screen)) {
                return restore(minecraft, "screen=" + minecraft.gui.screen());
            }
            long buttons = screen.children().stream().filter(child -> child instanceof Button).count();
            CreativeScreenPages.turn(screen, 1);
            boolean onModPage = CreativeTabPages.currentPage() == 1;
            boolean filled = tab.getDisplayItems().stream().anyMatch(stack -> stack.is(Items.AMETHYST_SHARD));
            if (buttons < 2 || !onModPage || !filled) {
                return restore(minecraft, "buttons=" + buttons + " page=" + CreativeTabPages.currentPage()
                        + " filled=" + filled);
            }
            return null;
        }
        if (current == WAIT_TICKS + 6) {
            if (!(minecraft.gui.screen() instanceof CreativeModeInventoryScreen screen)) {
                return restore(minecraft, "closed=" + minecraft.gui.screen());
            }
            CreativeScreenPages.turn(screen, 1);
            int page = CreativeTabPages.currentPage();
            return restore(minecraft, page == 0 ? "ok" : "wrapPage=" + page);
        }
        return null;
    }

    private static String restore(Minecraft minecraft, String result) {
        minecraft.gui.setScreen(null);
        minecraft.player.getAbilities().instabuild = instabuild;
        return result;
    }
}
