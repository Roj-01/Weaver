package net.weaver.api.game;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.weaver.api.ModContext;
import net.weaver.api.WeaverMod;

/**
 * Game-typed extension of {@link WeaverMod}.
 *
 * <p>Each typed overload is an optional extension point with an empty default.
 * The inherited {@link Object}-typed method checks the payload and forwards to
 * the typed overload when it matches, so existing mods that override the
 * object-typed methods keep working and new mods can override the typed ones.
 * A payload of a different type is ignored.
 */
public interface GameWeaverMod extends WeaverMod {

    /**
     * A server player joined.
     *
     * @param context the mod context
     * @param player  the joining player
     */
    default void onPlayerJoin(ModContext context, ServerPlayer player) {
    }

    /**
     * A server player left.
     *
     * @param context the mod context
     * @param player  the leaving player
     */
    default void onPlayerLeave(ModContext context, ServerPlayer player) {
    }

    /**
     * A level was created or loaded.
     *
     * @param context the mod context
     * @param level   the loaded level
     */
    default void onLevelLoad(ModContext context, Level level) {
    }

    /**
     * A level is being unloaded.
     *
     * @param context the mod context
     * @param level   the unloading level
     */
    default void onLevelUnload(ModContext context, Level level) {
    }

    @Override
    default void onPlayerJoin(ModContext context, Object player) {
        if (player instanceof ServerPlayer serverPlayer) {
            onPlayerJoin(context, serverPlayer);
        }
    }

    @Override
    default void onPlayerLeave(ModContext context, Object player) {
        if (player instanceof ServerPlayer serverPlayer) {
            onPlayerLeave(context, serverPlayer);
        }
    }

    @Override
    default void onLevelLoad(ModContext context, Object level) {
        if (level instanceof Level gameLevel) {
            onLevelLoad(context, gameLevel);
        }
    }

    @Override
    default void onLevelUnload(ModContext context, Object level) {
        if (level instanceof Level gameLevel) {
            onLevelUnload(context, gameLevel);
        }
    }
}
