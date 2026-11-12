package net.weaver.api.game.event.client;

import java.util.Objects;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.weaver.api.event.Event;

/**
 * Fired on the client when the local player starts or keeps mining a block
 * ({@code MultiPlayerGameMode#startDestroyBlock} and
 * {@code #continueDestroyBlock}, every tick while the button is held).
 * Cancelable: a canceled event does nothing this tick and sends nothing to
 * the server.
 */
public final class ClientLeftClickBlockEvent extends Event {
    /** Whether mining starts or continues. */
    public enum Phase {
        START,
        CONTINUE
    }

    private final Phase phase;
    private final LocalPlayer player;
    private final BlockPos pos;
    private final Direction face;

    public ClientLeftClickBlockEvent(Phase phase, LocalPlayer player, BlockPos pos, Direction face) {
        super(true);
        this.phase = Objects.requireNonNull(phase, "phase");
        this.player = player;
        this.pos = pos;
        this.face = face;
    }

    public Phase phase() {
        return phase;
    }

    public LocalPlayer player() {
        return player;
    }

    public BlockPos pos() {
        return pos;
    }

    public Direction face() {
        return face;
    }
}
