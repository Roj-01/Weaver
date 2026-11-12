package net.weaver.api.game.event.world;

import java.util.Collection;
import java.util.Objects;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.weaver.api.event.Event;

/**
 * Fired when a creative tab has filled its item list ({@code CreativeModeTab#buildContents}),
 * right after vanilla added its items. The client builds the tabs when the
 * creative inventory needs them (and again when enabled features, the
 * operator item setting, or the registries change), so this is posted as a
 * client-side event.
 *
 * <p>{@link #accept(ItemStack)} adds a stack to the end of the tab and to the
 * search tab; {@link #acceptTabOnly} and {@link #acceptSearchOnly} add to one
 * of them. A stack equal (item and components) to one already listed is not
 * added again.</p>
 */
public final class BuildCreativeTabContentsEvent extends Event {
    private final CreativeModeTab tab;
    private final CreativeModeTab.ItemDisplayParameters parameters;
    private final Collection<ItemStack> tabItems;
    private final Collection<ItemStack> searchItems;

    public BuildCreativeTabContentsEvent(CreativeModeTab tab, CreativeModeTab.ItemDisplayParameters parameters,
                                         Collection<ItemStack> tabItems, Collection<ItemStack> searchItems) {
        this.tab = tab;
        this.parameters = parameters;
        this.tabItems = tabItems;
        this.searchItems = searchItems;
    }

    public CreativeModeTab tab() {
        return tab;
    }

    /** Enabled features, operator-items setting, and registry lookup. */
    public CreativeModeTab.ItemDisplayParameters parameters() {
        return parameters;
    }

    public void accept(ItemStack stack) {
        acceptTabOnly(stack);
        acceptSearchOnly(stack);
    }

    public void accept(ItemLike item) {
        accept(new ItemStack(item));
    }

    public void acceptTabOnly(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (!stack.isEmpty() && stack.isItemEnabled(parameters.enabledFeatures())) {
            tabItems.add(stack.copy());
        }
    }

    public void acceptSearchOnly(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (!stack.isEmpty() && searchItems != null && stack.isItemEnabled(parameters.enabledFeatures())) {
            searchItems.add(stack.copy());
        }
    }

    /** The items the tab shows, in order, read-only. */
    public Collection<ItemStack> items() {
        return java.util.Collections.unmodifiableCollection(tabItems);
    }
}
