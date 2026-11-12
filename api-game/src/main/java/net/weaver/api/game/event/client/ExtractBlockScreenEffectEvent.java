package net.weaver.api.game.event.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.event.Event;

/**
 * Fired on the client each frame for every full-screen effect the view will
 * show ({@code LevelExtractor#extractPlayerState}): the texture of a block the
 * camera is inside, the fire overlay, and the underwater overlay. Cancelable: a
 * canceled event hides that overlay for this frame. For {@link Type#BLOCK},
 * {@link #setBlockState} changes which block texture is drawn.
 */
public final class ExtractBlockScreenEffectEvent extends Event {
    /** The kind of overlay. */
    public enum Type {
        /** The camera is inside a solid block. */
        BLOCK,
        /** The player is burning. */
        FIRE,
        /** The camera is underwater. */
        WATER
    }

    private final LocalPlayer player;
    private final Type type;
    private final PlayerRenderState renderState;
    private BlockState blockState;

    public ExtractBlockScreenEffectEvent(LocalPlayer player, Type type, BlockState blockState, PlayerRenderState renderState) {
        super(true);
        this.player = player;
        this.type = type;
        this.blockState = blockState;
        this.renderState = renderState;
    }

    public LocalPlayer player() {
        return player;
    }

    public Type type() {
        return type;
    }

    /** The block whose texture is drawn, for {@link Type#BLOCK}; null otherwise. */
    public BlockState blockState() {
        return blockState;
    }

    /** Changes the block whose texture is drawn; only honored for {@link Type#BLOCK}. */
    public void setBlockState(BlockState blockState) {
        this.blockState = java.util.Objects.requireNonNull(blockState, "blockState");
    }

    /** The player render state being filled for this frame. */
    public PlayerRenderState renderState() {
        return renderState;
    }
}
