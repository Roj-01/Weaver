package net.weaver.api.game.event.player;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.weaver.api.event.Event;

/**
 * Fired once when a player tries to sleep in a bed
 * ({@code ServerPlayer#startSleepInBed}). {@link #problem()} is what vanilla
 * found, or {@code null} when nothing stops the player.
 *
 * <p>Set a problem to stop the player sleeping. Clearing the problem to
 * {@code null} lets the player sleep anyway when vanilla's reason was the
 * time of day or nearby monsters ({@code NOT_SAFE}). Other problems (too far
 * away, obstructed, already asleep) cannot be cleared; clearing one keeps
 * vanilla's problem.</p>
 */
public final class CanPlayerSleepEvent extends Event {
    private final ServerPlayer player;
    private final BlockPos pos;
    private final Player.BedSleepingProblem vanillaProblem;
    private Player.BedSleepingProblem problem;

    public CanPlayerSleepEvent(ServerPlayer player, BlockPos pos, Player.BedSleepingProblem problem) {
        this.player = player;
        this.pos = pos;
        this.vanillaProblem = problem;
        this.problem = problem;
    }

    public ServerPlayer player() {
        return player;
    }

    /** The bed position. */
    public BlockPos pos() {
        return pos;
    }

    /** What vanilla found, or {@code null}. */
    public Player.BedSleepingProblem vanillaProblem() {
        return vanillaProblem;
    }

    public Player.BedSleepingProblem problem() {
        return problem;
    }

    public void setProblem(Player.BedSleepingProblem problem) {
        this.problem = problem;
    }
}
