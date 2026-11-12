package net.weaver.api.game.event.world;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRule;
import net.weaver.api.event.Event;

/**
 * Fired on the server after a game rule changed ({@code MinecraftServer#onGameRuleChanged}),
 * from the {@code /gamerule} command or code, once vanilla has reacted to it.
 */
public final class GameRuleChangedEvent extends Event {
    private final MinecraftServer server;
    private final GameRule<?> rule;
    private final Object value;

    public GameRuleChangedEvent(MinecraftServer server, GameRule<?> rule, Object value) {
        this.server = server;
        this.rule = rule;
        this.value = value;
    }

    public MinecraftServer server() {
        return server;
    }

    public GameRule<?> rule() {
        return rule;
    }

    /** The new value; its type matches the rule's. */
    public Object value() {
        return value;
    }
}
