package net.weaver.api.game.fluid;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.Fluid;

/**
 * The registered {@link FluidType}s, by fluid tag. Register during mod
 * initialization, before any world loads: entities created before a
 * registration do not feel that fluid's current.
 */
public final class FluidTypes {
    private static volatile Map<TagKey<Fluid>, FluidType> types = Map.of();

    private FluidTypes() {
    }

    /**
     * Gives the fluids in {@code tag} the behaviour of {@code type}.
     *
     * @throws IllegalArgumentException for the water or lava tags, which keep vanilla behaviour
     * @throws IllegalStateException when the tag already has a type
     */
    public static synchronized void register(TagKey<Fluid> tag, FluidType type) {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(type, "type");
        if (tag.equals(FluidTags.WATER) || tag.equals(FluidTags.LAVA)) {
            throw new IllegalArgumentException("water and lava keep vanilla behaviour: " + tag);
        }
        Map<TagKey<Fluid>, FluidType> next = new LinkedHashMap<>(types);
        if (next.putIfAbsent(tag, type) != null) {
            throw new IllegalStateException("fluid type already registered for " + tag);
        }
        // Registration order matters ("first registered wins"); Map.copyOf would not keep it.
        types = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(next));
    }

    /** Every registration (read-only). */
    public static Map<TagKey<Fluid>, FluidType> all() {
        return types;
    }

    /** The type of a fluid, or null. */
    public static FluidType of(Fluid fluid) {
        if (fluid == null) {
            return null;
        }
        for (Map.Entry<TagKey<Fluid>, FluidType> entry : types.entrySet()) {
            if (fluid.builtInRegistryHolder().is(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** The tag of the first registered type the entity is in, or null. */
    public static TagKey<Fluid> inside(Entity entity) {
        for (TagKey<Fluid> tag : types.keySet()) {
            if (entity.getFluidHeight(tag) > 0.0) {
                return tag;
            }
        }
        return null;
    }

    /** The type the entity can swim in, or null. */
    public static FluidType swimmable(Entity entity) {
        TagKey<Fluid> tag = inside(entity);
        FluidType type = tag == null ? null : types.get(tag);
        return type != null && type.canSwim() ? type : null;
    }

    /** The deepest swimmable registered fluid around the entity, 0 when none. */
    public static double swimHeight(Entity entity) {
        double height = 0.0;
        for (Map.Entry<TagKey<Fluid>, FluidType> entry : types.entrySet()) {
            if (entry.getValue().canSwim()) {
                height = Math.max(height, entity.getFluidHeight(entry.getKey()));
            }
        }
        return height;
    }

    /** Whether the entity's eyes are in a registered fluid it can drown in. */
    public static boolean drowns(Entity entity) {
        for (Map.Entry<TagKey<Fluid>, FluidType> entry : types.entrySet()) {
            if (entry.getValue().canDrown() && entity.isEyeInFluid(entry.getKey())) {
                return true;
            }
        }
        return false;
    }

    /** The bucket empty sound of a fluid's type, or null for vanilla's. */
    public static SoundEvent emptySound(Fluid fluid) {
        FluidType type = of(fluid);
        return type == null ? null : type.emptySound();
    }

    /** Vanilla's tags with current plus every registered tag; read by the loader. */
    public static Set<TagKey<Fluid>> withCurrents(Set<TagKey<Fluid>> vanilla) {
        if (types.isEmpty()) {
            return vanilla;
        }
        Set<TagKey<Fluid>> all = new HashSet<>(vanilla);
        all.addAll(types.keySet());
        return Set.copyOf(all);
    }
}
