package net.weaver.api.game.client.gui;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import net.minecraft.client.gui.screens.Screen;
import net.weaver.api.config.ConfigManager;

/**
 * The settings screen of each mod, opened from the mod list.
 *
 * <p>A mod with a Weaver configuration ({@link ConfigManager}) gets a generated
 * {@link ConfigScreen} with no code. A mod that wants its own screen registers a
 * factory from the parent screen to its screen; that wins over the generated one.</p>
 */
public final class ConfigScreens {
    private static final Map<String, UnaryOperator<Screen>> FACTORIES = new ConcurrentHashMap<>();

    private ConfigScreens() {
    }

    /**
     * Registers a mod's own settings screen.
     *
     * @param modId   the mod id
     * @param factory makes the screen from the parent screen (the one to return to)
     */
    public static void register(String modId, UnaryOperator<Screen> factory) {
        FACTORIES.put(Objects.requireNonNull(modId, "modId"), Objects.requireNonNull(factory, "factory"));
    }

    /** Whether the mod has a settings screen, its own or a generated one. */
    public static boolean has(String modId) {
        return FACTORIES.containsKey(modId) || ConfigManager.find(modId).isPresent();
    }

    /**
     * The mod's settings screen: its registered one, or a generated one for its
     * configuration. Empty when it has neither, or when its factory fails (logged).
     */
    public static Optional<Screen> create(String modId, Screen parent) {
        UnaryOperator<Screen> factory = FACTORIES.get(modId);
        if (factory != null) {
            try {
                return Optional.ofNullable(factory.apply(parent));
            } catch (RuntimeException | LinkageError failure) {
                System.out.println("[weaver] CONFIG SCREEN FAILED mod=" + modId + " reason=" + failure
                        + " action=generated-screen");
            }
        }
        return ConfigManager.find(modId).map(manager -> (Screen) new ConfigScreen(parent, manager));
    }
}
