package net.weaver.api.game.event.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.RegisterRenderStateModifiersEvent;
import net.weaver.api.game.event.client.RegisterRenderStateModifiersEvent.Modifier;
import net.weaver.api.game.event.client.RegisterRenderStateModifiersEvent.Registration;

/** Runs the registered entity render-state modifiers. Client only. */
public final class RenderStateModifiers {
    private static final Modifier<?, ?>[] NONE = new Modifier<?, ?>[0];
    private static final Map<Class<?>, Map<Class<?>, Modifier<?, ?>[]>> CACHE = new ConcurrentHashMap<>();
    private static volatile List<Registration> registrations;

    private RenderStateModifiers() {
    }

    /** Applies the modifiers matching the entity and state classes; a failing modifier is logged and skipped. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void apply(Entity entity, EntityRenderState state, float partialTick) {
        List<Registration> all = registrations;
        if (all == null) {
            all = collect();
        }
        if (all.isEmpty() || entity == null || state == null) {
            return;
        }
        Class<?> entityClass = entity.getClass();
        Class<?> stateClass = state.getClass();
        Modifier<?, ?>[] matching = CACHE.computeIfAbsent(entityClass, key -> new ConcurrentHashMap<>())
                .computeIfAbsent(stateClass, key -> match(all(), entityClass, stateClass));
        for (Modifier modifier : matching) {
            try {
                modifier.modify(entity, state, partialTick);
            } catch (RuntimeException | LinkageError | StackOverflowError failure) {
                System.out.println("[weaver] RENDER STATE MODIFIER FAILED entity=" + entity.getType()
                        + " error=" + failure);
            }
        }
    }

    private static List<Registration> all() {
        return registrations;
    }

    private static synchronized List<Registration> collect() {
        if (registrations == null) {
            List<Registration> collected = new ArrayList<>();
            GameEvents.post(new RegisterRenderStateModifiersEvent(collected), LogicalSide.CLIENT);
            registrations = List.copyOf(collected);
        }
        return registrations;
    }

    private static Modifier<?, ?>[] match(List<Registration> all, Class<?> entityClass, Class<?> stateClass) {
        List<Modifier<?, ?>> found = new ArrayList<>();
        for (Registration registration : all) {
            if (registration.entityClass().isAssignableFrom(entityClass)
                    && registration.stateClass().isAssignableFrom(stateClass)) {
                found.add(registration.modifier());
            }
        }
        return found.isEmpty() ? NONE : found.toArray(new Modifier<?, ?>[0]);
    }
}
