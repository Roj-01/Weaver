package net.weaver.api.game.event.client;

import java.util.Objects;
import net.minecraft.world.InteractionHand;
import net.weaver.api.event.Event;

/**
 * Fired on the client when the attack, use, or pick-block key acts
 * ({@code Minecraft#startAttack}, {@code #startUseItem},
 * {@code #pickBlockOrEntity}), before vanilla checks the target. Cancelable:
 * a canceled event does nothing for that key press. Holding the attack key on
 * a block is reported by {@link ClientLeftClickBlockEvent} instead.
 */
public final class ClientInteractionInputEvent extends Event {
    /** Which key acted. */
    public enum Kind {
        ATTACK,
        USE,
        PICK_BLOCK
    }

    private final Kind kind;
    private final InteractionHand hand;

    public ClientInteractionInputEvent(Kind kind, InteractionHand hand) {
        super(true);
        this.kind = Objects.requireNonNull(kind, "kind");
        this.hand = hand;
    }

    public Kind kind() {
        return kind;
    }

    /** The hand; vanilla tries the off hand too when the main hand does nothing. */
    public InteractionHand hand() {
        return hand;
    }
}
