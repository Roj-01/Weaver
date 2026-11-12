package net.weaver.api.game.event.player;

import com.mojang.authlib.GameProfile;
import java.util.Optional;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.weaver.api.event.Event;

/**
 * Fired on the server when a client reports a click on a custom click action
 * (a {@code custom} click event in a chat message, book, or dialog;
 * {@code ServerCommonPacketListenerImpl#handleCustomClickAction}). A
 * listener that handles {@link #id()} should cancel the event; a canceled
 * event is not passed on to vanilla, which only logs it.
 */
public final class CustomClickActionEvent extends Event {
    private final ServerPlayer player;
    private final GameProfile profile;
    private final Identifier id;
    private final Optional<Tag> payload;

    public CustomClickActionEvent(ServerPlayer player, GameProfile profile, Identifier id, Optional<Tag> payload) {
        super(true);
        this.player = player;
        this.profile = profile;
        this.id = id;
        this.payload = payload;
    }

    /** The player, or {@code null} during the configuration phase (before joining). */
    public ServerPlayer player() {
        return player;
    }

    public GameProfile profile() {
        return profile;
    }

    public Identifier id() {
        return id;
    }

    public Optional<Tag> payload() {
        return payload;
    }
}
