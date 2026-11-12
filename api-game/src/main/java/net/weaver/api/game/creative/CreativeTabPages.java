package net.weaver.api.game.creative;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;

/**
 * Creative inventory pages for mod tabs.
 *
 * <p>Vanilla gives every tab a fixed slot (top or bottom row, column 0 to 6)
 * and its 14 tabs use them all. Weaver keeps vanilla's tabs on page 1 exactly
 * as they are and lays mod tabs (any tab whose registry id is outside
 * {@code minecraft}) onto pages 2 and up, ten per page: columns 0 to 4 of the
 * top row, then of the bottom row, in registry order. The row and column a
 * mod tab was built with are ignored. Vanilla's right-aligned tabs (saved
 * hotbars, search, operator items, survival inventory) stay on every page.</p>
 *
 * <p>So a mod registers a tab in the vanilla registry with any position;
 * {@link ModCreativeTabs} has a builder and an item callback mod code can
 * compile against.</p>
 *
 * <p>The creative screen shows page arrows when there is more than one page.
 * The layout is computed on both sides from the registry, so server and
 * client agree as long as they have the same tabs (which the join-time
 * registry check enforces).</p>
 */
public final class CreativeTabPages {
    /** Mod tabs per page: five columns in each of the two rows. */
    public static final int SLOTS_PER_PAGE = 10;
    private static final int COLUMNS = 5;

    private record Layout(int registrySize, Map<CreativeModeTab, Integer> slots, int pageCount) {
    }

    private static volatile Layout layout;
    private static volatile int currentPage;

    private CreativeTabPages() {
    }

    /** Whether {@code tab} is a mod tab (registry id outside {@code minecraft}). */
    public static boolean isModTab(CreativeModeTab tab) {
        return layout().slots().containsKey(tab);
    }

    /** The number of pages; 1 when there are no mod tabs. */
    public static int pageCount() {
        return layout().pageCount();
    }

    /** The page {@code tab} is on: 0 for vanilla tabs, 1 and up for mod tabs. */
    public static int pageOf(CreativeModeTab tab) {
        Integer slot = layout().slots().get(tab);
        return slot == null ? 0 : 1 + slot / SLOTS_PER_PAGE;
    }

    /** The page the creative screen shows, 0-based. */
    public static int currentPage() {
        return Math.min(currentPage, pageCount() - 1);
    }

    public static void setCurrentPage(int page) {
        currentPage = Math.max(0, Math.min(page, pageCount() - 1));
    }

    /** The row Weaver assigns, or {@code vanilla} for a vanilla tab. */
    public static CreativeModeTab.Row row(CreativeModeTab tab, CreativeModeTab.Row vanilla) {
        Integer slot = slotOf(tab);
        if (slot == null) {
            return vanilla;
        }
        return slot % SLOTS_PER_PAGE < COLUMNS ? CreativeModeTab.Row.TOP : CreativeModeTab.Row.BOTTOM;
    }

    /** The column Weaver assigns, or {@code vanilla} for a vanilla tab. */
    public static int column(CreativeModeTab tab, int vanilla) {
        Integer slot = slotOf(tab);
        return slot == null ? vanilla : slot % COLUMNS;
    }

    /** Whether a tab is drawn from the right edge; never for mod tabs. */
    public static boolean alignedRight(CreativeModeTab tab, boolean vanilla) {
        return slotOf(tab) == null && vanilla;
    }

    /**
     * The tabs the creative screen should list on the current page: on page
     * 1 the vanilla tabs, on later pages that page's mod tabs plus vanilla's
     * right-aligned tabs. With a single page, {@code all} unchanged.
     */
    public static List<CreativeModeTab> visible(List<CreativeModeTab> all) {
        if (pageCount() <= 1) {
            return all;
        }
        int page = currentPage();
        List<CreativeModeTab> shown = new ArrayList<>();
        for (CreativeModeTab tab : all) {
            int tabPage = pageOf(tab);
            if (tabPage == page || (page > 0 && tabPage == 0 && tab.isAlignedRight())) {
                shown.add(tab);
            }
        }
        return shown;
    }

    /** The mod tabs on {@code page}, in slot order. */
    public static List<CreativeModeTab> tabsOn(int page) {
        List<CreativeModeTab> tabs = new ArrayList<>();
        for (Map.Entry<CreativeModeTab, Integer> entry : layout().slots().entrySet()) {
            if (1 + entry.getValue() / SLOTS_PER_PAGE == page) {
                tabs.add(entry.getKey());
            }
        }
        tabs.sort(java.util.Comparator.comparingInt(tab -> layout().slots().get(tab)));
        return tabs;
    }

    /**
     * Checks vanilla's rule (no two tabs in one slot) for the vanilla tabs
     * only; mod tab slots are assigned and cannot collide.
     *
     * @throws IllegalArgumentException on a duplicate vanilla position
     */
    public static void validate() {
        Set<String> positions = new HashSet<>();
        for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
            if (slotOf(tab) != null) {
                continue;
            }
            String position = tab.row() + ":" + tab.column();
            if (!positions.add(position)) {
                throw new IllegalArgumentException("Duplicate position: " + tab.getDisplayName().getString()
                        + " at " + position);
            }
        }
    }

    private static Integer slotOf(CreativeModeTab tab) {
        return layout().slots().get(tab);
    }

    private static Layout layout() {
        Layout current = layout;
        int size = BuiltInRegistries.CREATIVE_MODE_TAB.size();
        if (current != null && current.registrySize() == size) {
            return current;
        }
        Map<CreativeModeTab, Integer> slots = new HashMap<>();
        int next = 0;
        for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
            Identifier id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if (id != null && !Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace())) {
                slots.put(tab, next++);
            }
        }
        int pages = 1 + (next + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE;
        Layout built = new Layout(size, Map.copyOf(slots), pages);
        layout = built;
        return built;
    }
}
