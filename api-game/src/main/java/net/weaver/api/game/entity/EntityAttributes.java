package net.weaver.api.game.entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.weaver.api.game.event.mixin.AttributeSupplierAccessor;

/**
 * Attributes of living entity types: the supplier of a mod's own type (health,
 * speed, armor, attack damage, ...), and extra attributes added to any type,
 * vanilla types included (a mana attribute on players and zombies).
 *
 * <pre>{@code
 * // During the register phase:
 * EntityAttributes.register(MY_GOLEM, Mob.createMobAttributes()
 *         .add(Attributes.MAX_HEALTH, 80).add(Attributes.MOVEMENT_SPEED, 0.25));
 * EntityAttributes.add(EntityType.ZOMBIE, MY_MANA, 20.0);
 * }</pre>
 *
 * <p>Without a supplier a living entity cannot be created (vanilla throws when it
 * spawns). Register before entities of the type exist. A registration replaces
 * the vanilla supplier of that type; added attributes apply on top of either.</p>
 */
public final class EntityAttributes {
    private static final Map<EntityType<?>, AttributeSupplier> REGISTERED = new ConcurrentHashMap<>();
    private static final Map<EntityType<?>, List<Added>> ADDED = new ConcurrentHashMap<>();
    private static final Map<EntityType<?>, AttributeSupplier> CACHE = new ConcurrentHashMap<>();

    private record Added(Holder<Attribute> attribute, double base) {
    }

    private EntityAttributes() {
    }

    /** Gives {@code type} its attributes. */
    public static void register(EntityType<? extends LivingEntity> type, AttributeSupplier.Builder attributes) {
        register(type, attributes.build());
    }

    /** Gives {@code type} its attributes. */
    public static void register(EntityType<? extends LivingEntity> type, AttributeSupplier attributes) {
        REGISTERED.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(attributes, "attributes"));
        CACHE.remove(type);
    }

    /** Adds (or rebases) one attribute on {@code type}, on top of its supplier. */
    public static void add(EntityType<? extends LivingEntity> type, Holder<Attribute> attribute, double base) {
        ADDED.computeIfAbsent(Objects.requireNonNull(type, "type"), key -> new CopyOnWriteArrayList<>())
                .add(new Added(Objects.requireNonNull(attribute, "attribute"), base));
        CACHE.remove(type);
    }

    /** Whether a mod registered a supplier for {@code type}. */
    public static boolean isRegistered(EntityType<?> type) {
        return REGISTERED.containsKey(type);
    }

    /**
     * The supplier for {@code type} after registrations and additions; null to keep
     * {@code vanilla}. Called by the loader.
     */
    @net.weaver.api.annotation.Internal
    public static AttributeSupplier adjust(EntityType<?> type, AttributeSupplier vanilla) {
        AttributeSupplier own = REGISTERED.get(type);
        List<Added> added = ADDED.get(type);
        if (own == null && (added == null || added.isEmpty())) {
            return null;
        }
        AttributeSupplier cached = CACHE.get(type);
        if (cached != null) {
            return cached;
        }
        AttributeSupplier base = own != null ? own : vanilla;
        AttributeSupplier result = base;
        if (added != null && !added.isEmpty()) {
            Map<Holder<Attribute>, Double> extra = new LinkedHashMap<>();
            for (Added each : added) {
                extra.put(each.attribute(), each.base());
            }
            AttributeSupplier.Builder builder = AttributeSupplier.builder();
            if (base != null) {
                for (Map.Entry<Holder<Attribute>, AttributeInstance> entry
                        : ((AttributeSupplierAccessor) base).weaver$instances().entrySet()) {
                    if (!extra.containsKey(entry.getKey())) {
                        builder.add(entry.getKey(), entry.getValue().getBaseValue());
                    }
                }
            }
            extra.forEach(builder::add);
            result = builder.build();
        }
        if (result != null) {
            CACHE.put(type, result);
        }
        return result;
    }

    /** The types with an added attribute (for diagnostics). */
    public static List<EntityType<?>> extendedTypes() {
        return new ArrayList<>(ADDED.keySet());
    }
}
