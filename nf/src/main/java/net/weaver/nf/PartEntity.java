package net.weaver.nf;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;

/**
 * One hitbox of a {@link Multipart} entity, as NeoForge's {@code PartEntity}: a
 * Weaver {@code PartEntity} that sizes itself (override {@code getDimensions}) and
 * is never sent to clients on its own (its parent is, and the client makes the
 * parts).
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class PartEntity<T extends Entity> extends net.weaver.api.game.entity.PartEntity {
    protected PartEntity(T parent) {
        super((Entity & net.weaver.api.game.entity.MultipartEntity) requireMultipart(parent), "part");
    }

    private static Entity requireMultipart(Entity parent) {
        if (!(parent instanceof net.weaver.api.game.entity.MultipartEntity)) {
            throw new IllegalArgumentException("the parent of a part must implement Multipart: " + parent.getClass().getName());
        }
        return parent;
    }

    public T getParent() {
        return (T) parent();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        throw new UnsupportedOperationException("a part is never sent: its parent is");
    }
}
