package net.weaver.api.game.event.player;

import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.players.NameAndId;
import net.weaver.api.event.Event;

/**
 * Fired when a player is about to be made an operator or lose operator status
 * ({@code PlayerList#op} and {@code PlayerList#deop}, used by {@code /op},
 * {@code /deop}, and the server console). The player may be offline.
 * Cancelable: a canceled event leaves the operator list unchanged.
 */
public final class PermissionsChangedEvent extends Event {
    private final NameAndId player;
    private final LevelBasedPermissionSet newPermissions;
    private final boolean operator;

    public PermissionsChangedEvent(NameAndId player, LevelBasedPermissionSet newPermissions, boolean operator) {
        super(true);
        this.player = player;
        this.newPermissions = newPermissions;
        this.operator = operator;
    }

    /** The player's name and id. */
    public NameAndId player() {
        return player;
    }

    /** The permission set the player gets. */
    public LevelBasedPermissionSet newPermissions() {
        return newPermissions;
    }

    /** {@code true} for op, {@code false} for deop. */
    public boolean operator() {
        return operator;
    }
}
