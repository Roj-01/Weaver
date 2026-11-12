package net.weaver.api.game.creative;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * Where a mod tab's items go while the tab is built. Wraps vanilla's
 * {@code CreativeModeTab.Output} and {@code TabVisibility}, which mod code
 * cannot name because they are protected nested types.
 */
public final class CreativeTabItems {
    /** Where an added stack shows. */
    public enum Visibility {
        /** In the tab and in the search tab. */
        TAB_AND_SEARCH("PARENT_AND_SEARCH_TABS"),
        /** In the tab only. */
        TAB_ONLY("PARENT_TAB_ONLY"),
        /** In the search tab only. */
        SEARCH_ONLY("SEARCH_TAB_ONLY");

        private final String vanillaName;

        Visibility(String vanillaName) {
            this.vanillaName = vanillaName;
        }
    }

    private static final Method ACCEPT;
    private static final Object[] VANILLA_VISIBILITY;

    static {
        try {
            ClassLoader loader = CreativeModeTab.class.getClassLoader();
            Class<?> output = Class.forName("net.minecraft.world.item.CreativeModeTab$Output", false, loader);
            Class<?> visibility = Class.forName("net.minecraft.world.item.CreativeModeTab$TabVisibility", false, loader);
            ACCEPT = output.getMethod("accept", ItemStack.class, visibility);
            ACCEPT.setAccessible(true);
            Method valueOf = visibility.getMethod("valueOf", String.class);
            valueOf.setAccessible(true);
            Visibility[] ours = Visibility.values();
            VANILLA_VISIBILITY = new Object[ours.length];
            for (Visibility value : ours) {
                VANILLA_VISIBILITY[value.ordinal()] = valueOf.invoke(null, value.vanillaName);
            }
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private final Object output;

    CreativeTabItems(Object output) {
        this.output = output;
    }

    /** Adds {@code item} to the tab and to the search tab. */
    public void accept(ItemLike item) {
        accept(new ItemStack(item), Visibility.TAB_AND_SEARCH);
    }

    /** Adds {@code stack} to the tab and to the search tab. */
    public void accept(ItemStack stack) {
        accept(stack, Visibility.TAB_AND_SEARCH);
    }

    /** Adds {@code item} where {@code visibility} says. */
    public void accept(ItemLike item, Visibility visibility) {
        accept(new ItemStack(item), visibility);
    }

    /** Adds {@code stack} where {@code visibility} says. */
    public void accept(ItemStack stack, Visibility visibility) {
        try {
            ACCEPT.invoke(output, stack, VANILLA_VISIBILITY[visibility.ordinal()]);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** Adds every stack to the tab and to the search tab. */
    public void acceptAll(Collection<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            accept(stack);
        }
    }
}
