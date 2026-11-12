package net.weaver.api.game.extension;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Something an item can do that vanilla only lets one hard-coded item do.
 * {@link ItemExtension#canPerformAction} answers whether a stack can.
 * Actions are interned by name: {@code ItemAction.of("examplemod:drill")}
 * always gives the same object, so mods can define and share their own.
 */
public final class ItemAction {
    private static final Map<String, ItemAction> ACTIONS = new ConcurrentHashMap<>();

    /**
     * Shearing, which vanilla allows only with shears: sheep, mooshrooms, snow golems,
     * copper golems, sulfur cubes, and bogged; honey from beehives; carving pumpkins;
     * disarming tripwire; cutting leads; and shearing from a dispenser.
     */
    public static final ItemAction SHEAR = of("shear");

    private final String name;

    private ItemAction(String name) {
        this.name = name;
    }

    /** The action named {@code name}, created on first use. */
    public static ItemAction of(String name) {
        Objects.requireNonNull(name, "name");
        return ACTIONS.computeIfAbsent(name, ItemAction::new);
    }

    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return "ItemAction[" + name + "]";
    }
}
