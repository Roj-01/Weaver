package net.weaver.nf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.events.ModLifecycleEvent;

/**
 * Attribute suppliers for entity types, as NeoForge's
 * {@code EntityAttributeCreationEvent}: {@code register(event -> event.put(type, supplier))}.
 * Backed by Weaver's {@code EntityAttributes}.
 *
 * <p>As NeoForge fires its event once the registries are filled, a registration
 * made before the game's bootstrap is done (in {@code onInitialize} or
 * {@code onRegister}, where the mod's entity types are not registered and the
 * game's own attributes are not bound yet) runs at the first common setup; one
 * made later runs at once. So the registration may read its
 * {@code DeferredHolder}s and vanilla's {@code Attributes}.</p>
 */
public final class EntityAttributes {
    /** Where attribute suppliers are put. */
    public interface Event {
        void put(EntityType<? extends LivingEntity> type, AttributeSupplier attributes);
    }

    private static final List<Consumer<Event>> PENDING = new ArrayList<>();
    private static boolean ready;

    static {
        // (every mod's lifecycle event reaches the global buses; the first common setup is after the bootstrap)
        EventBus.GAME.addListener(ModLifecycleEvent.class, event -> {
            if (event.phase() == ModLifecycleEvent.Phase.COMMON_SETUP) {
                runPending();
            }
        });
    }

    private EntityAttributes() {
    }

    /**
     * Runs {@code registration} with an event that registers every supplier put into
     * it: at the first common setup, or now if that has passed.
     */
    public static void register(Consumer<Event> registration) {
        synchronized (EntityAttributes.class) {
            if (!ready) {
                PENDING.add(registration);
                return;
            }
        }
        run(registration);
    }

    private static void runPending() {
        List<Consumer<Event>> due;
        synchronized (EntityAttributes.class) {
            if (ready) {
                return;
            }
            ready = true;
            due = new ArrayList<>(PENDING);
            PENDING.clear();
        }
        for (Consumer<Event> registration : due) {
            run(registration);
        }
    }

    private static void run(Consumer<Event> registration) {
        try {
            registration.accept(net.weaver.api.game.entity.EntityAttributes::register);
        } catch (RuntimeException | LinkageError failure) {
            System.err.println("[weaver-nf] ENTITY ATTRIBUTES FAILED reason=" + failure);
            failure.printStackTrace();
        }
    }

    /** The supplier of {@code type} (vanilla's or a mod's), or null. */
    @SuppressWarnings("unchecked")
    public static AttributeSupplier get(EntityType<?> type) {
        return has(type) ? DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type) : null;
    }

    public static boolean has(EntityType<?> type) {
        return DefaultAttributes.hasSupplier(type) || net.weaver.api.game.entity.EntityAttributes.isRegistered(type);
    }
}
