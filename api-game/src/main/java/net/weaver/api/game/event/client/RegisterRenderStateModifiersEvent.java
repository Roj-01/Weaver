package net.weaver.api.game.event.client;

import java.util.List;
import java.util.Objects;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.weaver.api.event.Event;

/**
 * Fired once on the client, the first time an entity render state is
 * extracted, to register modifiers that run after vanilla fills a render state
 * ({@code EntityRenderer#createRenderState}). A modifier registered for an
 * entity class also runs for its subclasses whose render state is of the given
 * state class. Modifiers run on the render thread for every matching entity every
 * frame: keep them cheap.
 */
public final class RegisterRenderStateModifiersEvent extends Event {
    /** Changes a render state after vanilla extraction. */
    @FunctionalInterface
    public interface Modifier<E extends Entity, S extends EntityRenderState> {
        void modify(E entity, S state, float partialTick);
    }

    /** One registration. */
    public record Registration(Class<? extends Entity> entityClass, Class<? extends EntityRenderState> stateClass,
            Modifier<?, ?> modifier) {
    }

    private final List<Registration> registrations;

    public RegisterRenderStateModifiersEvent(List<Registration> registrations) {
        this.registrations = registrations;
    }

    public <E extends Entity, S extends EntityRenderState> void register(Class<E> entityClass, Class<S> stateClass,
            Modifier<? super E, ? super S> modifier) {
        registrations.add(new Registration(Objects.requireNonNull(entityClass, "entityClass"),
                Objects.requireNonNull(stateClass, "stateClass"), Objects.requireNonNull(modifier, "modifier")));
    }
}
