package net.weaver.api.game.event.support;

import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;

/**
 * Finds the level behind a menu's {@link ContainerLevelAccess}. Kept out of
 * the mixins because mixin code here does not use lambdas.
 */
public final class MenuLevels {
    private MenuLevels() {
    }

    /**
     * The level the menu's block is in, or {@code null} for
     * {@link ContainerLevelAccess#NULL}, which client-side menus use.
     */
    public static Level levelOf(ContainerLevelAccess access) {
        if (access == null) {
            return null;
        }
        return access.evaluate((level, pos) -> level).orElse(null);
    }
}
