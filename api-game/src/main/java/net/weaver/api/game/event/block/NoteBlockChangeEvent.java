package net.weaver.api.game.event.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.event.Event;

/**
 * Fired on the server when a player is about to change the pitch of a note block
 * by using it ({@code NoteBlock#useWithoutItem}). Cancelable: a canceled event
 * keeps the note. The new note (0 to 24) can be changed.
 */
public final class NoteBlockChangeEvent extends Event {
    private final Level level;
    private final BlockPos pos;
    private final BlockState state;
    private final Player player;
    private final int oldNote;
    private int newNote;

    public NoteBlockChangeEvent(Level level, BlockPos pos, BlockState state, Player player, int oldNote,
                                int newNote) {
        super(true);
        this.level = level;
        this.pos = pos;
        this.state = state;
        this.player = player;
        this.oldNote = oldNote;
        this.newNote = newNote;
    }

    public Level level() {
        return level;
    }

    public BlockPos pos() {
        return pos;
    }

    public BlockState state() {
        return state;
    }

    public Player player() {
        return player;
    }

    public int oldNote() {
        return oldNote;
    }

    public int newNote() {
        return newNote;
    }

    /** Sets the new note, 0 to 24. */
    public void setNewNote(int newNote) {
        if (newNote < 0 || newNote > 24) {
            throw new IllegalArgumentException("note must be 0..24 but was " + newNote);
        }
        this.newNote = newNote;
    }
}
