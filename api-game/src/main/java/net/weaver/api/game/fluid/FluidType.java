package net.weaver.api.game.fluid;

import net.minecraft.sounds.SoundEvent;

/**
 * How entities and players experience a modded fluid, so it does not have to
 * pretend to be water. Register one per fluid tag with
 * {@link FluidTypes#register}; put your still and flowing fluids in that tag
 * ({@code data/<ns>/tags/fluid/<name>.json}).
 *
 * <p>Applied by Weaver to every entity in the fluid (not to water or lava,
 * which keep vanilla behaviour):
 * <ul>
 *   <li>{@link #currentStrength}: how hard flowing fluid pushes entities (water 0.014);</li>
 *   <li>{@link #speed}, {@link #drag}, {@link #verticalDrag}: swimming movement per tick
 *       (water 0.02, 0.8, 0.8; lower drag is thicker, like honey);</li>
 *   <li>{@link #canSwim}: entities swim (and players rise by jumping) instead of
 *       walking on the bottom;</li>
 *   <li>{@link #canDrown}: entities lose air with their eyes inside;</li>
 *   <li>{@link #resetsFallDistance}: falling into it cancels fall damage;</li>
 *   <li>{@link #fogColor} (RGB, -1 for none), {@link #fogStart}, {@link #fogEnd}: the
 *       fog when the camera is inside (client);</li>
 *   <li>{@link #emptySound}: the sound of emptying a bucket of it (null: vanilla's).</li>
 * </ul>
 *
 * Putting out or setting fire is the fluid's own {@code Fluid#entityInside}
 * (apply {@code InsideBlockEffectType.EXTINGUISH} or {@code LAVA_IGNITE}); the
 * bucket fill sound is its {@code getPickupSound}. Boats float only on water.
 */
public record FluidType(double currentStrength, double speed, double drag, double verticalDrag, boolean canSwim,
        boolean canDrown, boolean resetsFallDistance, int fogColor, float fogStart, float fogEnd, SoundEvent emptySound) {

    /** A builder starting from water-like values. */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private double currentStrength = 0.014;
        private double speed = 0.02;
        private double drag = 0.8;
        private double verticalDrag = 0.8;
        private boolean canSwim = true;
        private boolean canDrown = true;
        private boolean resetsFallDistance = true;
        private int fogColor = -1;
        private float fogStart = 0.0F;
        private float fogEnd = 16.0F;
        private SoundEvent emptySound;

        private Builder() {
        }

        public Builder currentStrength(double value) {
            this.currentStrength = value;
            return this;
        }

        public Builder speed(double value) {
            this.speed = value;
            return this;
        }

        /** Horizontal and vertical drag together (0..1, multiplied into the motion each tick). */
        public Builder drag(double horizontal, double vertical) {
            this.drag = clamp(horizontal);
            this.verticalDrag = clamp(vertical);
            return this;
        }

        public Builder canSwim(boolean value) {
            this.canSwim = value;
            return this;
        }

        public Builder canDrown(boolean value) {
            this.canDrown = value;
            return this;
        }

        public Builder resetsFallDistance(boolean value) {
            this.resetsFallDistance = value;
            return this;
        }

        /** Fog inside the fluid: RGB color and its start and end distance in blocks. */
        public Builder fog(int rgb, float start, float end) {
            this.fogColor = rgb & 0xFFFFFF;
            this.fogStart = start;
            this.fogEnd = Math.max(start + 0.1F, end);
            return this;
        }

        public Builder emptySound(SoundEvent sound) {
            this.emptySound = sound;
            return this;
        }

        public FluidType build() {
            return new FluidType(currentStrength, speed, drag, verticalDrag, canSwim, canDrown, resetsFallDistance,
                    fogColor, fogStart, fogEnd, emptySound);
        }

        private static double clamp(double value) {
            return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.8;
        }
    }
}
