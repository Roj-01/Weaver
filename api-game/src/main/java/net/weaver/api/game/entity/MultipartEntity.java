package net.weaver.api.game.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;

/**
 * An entity made of several hitboxes, like the ender dragon: implement this on
 * the parent and give it {@link PartEntity} parts. Weaver then makes the parts
 * hittable (melee, crosshair, projectiles, area damage) and resolves their ids in
 * packets, on both sides.
 *
 * <pre>{@code
 * public class Serpent extends Monster implements MultipartEntity {
 *     private final SerpentPart[] parts = {new SerpentPart(this, "head", 1, 1), new SerpentPart(this, "tail", 1, 1)};
 *
 *     public PartEntity<?>[] getParts() { return parts; }
 *
 *     public boolean hurtFromPart(PartEntity<?> part, ServerLevel level, DamageSource source, float amount) {
 *         return hurtServer(level, source, part.partName().equals("head") ? amount * 2 : amount);
 *     }
 *
 *     public void aiStep() { super.aiStep(); parts[0].setPos(...); parts[1].setPos(...); }
 * }
 * }</pre>
 */
public interface MultipartEntity {
    /** The parts, always the same array in the same order. */
    PartEntity<?>[] getParts();

    /** A part was hit; return whether the parent took the damage. */
    boolean hurtFromPart(PartEntity<?> part, ServerLevel level, DamageSource source, float amount);
}
