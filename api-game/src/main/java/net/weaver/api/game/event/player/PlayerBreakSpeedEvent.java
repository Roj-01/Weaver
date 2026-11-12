package net.weaver.api.game.event.player;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.event.Event;

/**
 * Fired when the game computes how fast a player breaks a block
 * ({@code Player#getDestroySpeed}), after tool, enchantment, effect, water,
 * and on-ground modifiers. Fires on both logical sides, every tick while a
 * block is being mined; keep listeners cheap and side-consistent. Set
 * {@link #setSpeed(float)} to change the speed; {@code 0} stops progress.
 */
public final class PlayerBreakSpeedEvent extends Event {
    private final Player player;
    private final BlockState state;
    private final float originalSpeed;
    private float speed;

    public PlayerBreakSpeedEvent(Player player, BlockState state, float speed) {
        this.player = player;
        this.state = state;
        this.originalSpeed = speed;
        this.speed = speed;
    }

    public Player player() {
        return player;
    }

    public BlockState state() {
        return state;
    }

    /** The speed vanilla computed. */
    public float originalSpeed() {
        return originalSpeed;
    }

    public float speed() {
        return speed;
    }

    public void setSpeed(float speed) {
        net.weaver.api.game.event.support.Finite.check(speed, "speed");
        this.speed = Math.max(0.0F, speed);
    }
}
