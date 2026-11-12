package net.weaver.nf;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.weaver.api.game.entity.MultipartEntity;

/**
 * An entity made of several hitboxes, as NeoForge's {@code isMultipartEntity()} and
 * {@code getParts()}: implement it on the parent, with {@link PartEntity} parts.
 * It is a Weaver {@code MultipartEntity}, so Weaver makes the parts hittable and
 * resolves their ids on both sides. A part that does not override
 * {@code hurtServer} passes its damage to the parent as a whole.
 */
public interface Multipart extends MultipartEntity {
    default boolean isMultipartEntity() {
        return true;
    }

    @Override
    PartEntity<?>[] getParts();

    @Override
    default boolean hurtFromPart(net.weaver.api.game.entity.PartEntity<?> part, ServerLevel level, DamageSource source,
            float amount) {
        return ((Entity) this).hurtServer(level, source, amount);
    }
}
