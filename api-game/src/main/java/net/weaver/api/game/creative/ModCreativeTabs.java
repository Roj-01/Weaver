package net.weaver.api.game.creative;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.world.item.CreativeModeTab;

/**
 * Helpers for building a mod creative tab.
 *
 * <pre>{@code
 * Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id, ModCreativeTabs.builder()
 *         .title(Component.translatable("itemGroup.examplemod"))
 *         .icon(() -> new ItemStack(RUBY))
 *         .displayItems(ModCreativeTabs.items(items -> {
 *             items.accept(RUBY);
 *             items.accept(RUBY_BLOCK);
 *         }))
 *         .build());
 * }</pre>
 *
 * <p>Vanilla's {@code displayItems} callback takes a protected type that mod
 * code cannot compile against, so {@link #items} adapts a callback that takes
 * {@link CreativeTabItems} instead. The tab's page and slot come from
 * {@link CreativeTabPages}.</p>
 */
public final class ModCreativeTabs {
    private ModCreativeTabs() {
    }

    /** A tab builder; the row and column it carries are replaced by the page layout. */
    public static CreativeModeTab.Builder builder() {
        return CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0);
    }

    /** A {@code displayItems} callback that fills the tab through {@link CreativeTabItems}. */
    public static CreativeModeTab.DisplayItemsGenerator items(Consumer<CreativeTabItems> filler) {
        return items((parameters, items) -> filler.accept(items));
    }

    /**
     * Like {@link #items(Consumer)}, also given the display parameters
     * (enabled features, operator permission, registry lookup).
     */
    public static CreativeModeTab.DisplayItemsGenerator items(
            BiConsumer<CreativeModeTab.ItemDisplayParameters, CreativeTabItems> filler) {
        InvocationHandler handler = new Generator(filler);
        return (CreativeModeTab.DisplayItemsGenerator) Proxy.newProxyInstance(
                CreativeModeTab.DisplayItemsGenerator.class.getClassLoader(),
                new Class<?>[] {CreativeModeTab.DisplayItemsGenerator.class}, handler);
    }

    private record Generator(BiConsumer<CreativeModeTab.ItemDisplayParameters, CreativeTabItems> filler)
            implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "accept" -> {
                    filler.accept((CreativeModeTab.ItemDisplayParameters) args[0], new CreativeTabItems(args[1]));
                    yield null;
                }
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "ModCreativeTabs.items(" + filler + ")";
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }
    }
}
