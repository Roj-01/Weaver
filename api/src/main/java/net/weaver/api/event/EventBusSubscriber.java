package net.weaver.api.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class for automatic event-subscriber discovery. Static methods are
 * registered directly; classes with instance methods are instantiated through a
 * no-argument constructor.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EventBusSubscriber {

    Bus bus() default Bus.GAME;

    /**
     * Physical load filter: whether this class is loaded into a client process,
     * a server process, or both. This does not describe which logical side the
     * listeners serve.
     */
    Side side() default Side.BOTH;

    /**
     * Logical availability filter: whether the logical client, logical server,
     * or both must be present for this class to be loaded. On a client with an
     * integrated server, both {@code CLIENT} and {@code SERVER} are available
     * even though {@link #side()} is the physical {@code CLIENT}. {@code BOTH}
     * never filters.
     */
    LogicalSide logicalSide() default LogicalSide.BOTH;

    String modId() default "";

    /**
     * The bus an annotated subscriber is registered on.
     *
     * <p>The ordinals are part of the annotation contract and must stay
     * stable: {@link #GAME} is {@code 0}, {@link #MOD} is {@code 1}, and
     * {@link #GLOBAL_MOD} is {@code 2}.
     */
    enum Bus {
        /** The global game bus, {@link EventBus#GAME}. */
        GAME,
        /** The per-mod local bus for this subscriber's {@link EventBusSubscriber#modId()}. */
        MOD,
        /** The global aggregate mod bus, {@link EventBus#MOD}. */
        GLOBAL_MOD
    }
}
