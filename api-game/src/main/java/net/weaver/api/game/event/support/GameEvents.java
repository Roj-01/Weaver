package net.weaver.api.game.event.support;

import net.minecraft.world.level.Level;
import net.weaver.api.event.Event;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.LogicalSide;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Posts the game events fired by Weaver's built-in event mixins
 * ({@code weaver.events.mixins.json}).
 *
 * <p>Events go to {@link EventBus#GAME} with the logical side of the level they
 * happen in. The bus already isolates listener exceptions; this helper also
 * guards against a failure in the bus itself so a broken listener setup can
 * never break the game code the mixin sits in. Each failing event type is
 * reported once.</p>
 */
public final class GameEvents {
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private GameEvents() {
    }

    /**
     * Posts {@code event} for something that happens in {@code level}.
     *
     * @param event the event, not {@code null}
     * @param level the level, or {@code null} when unknown (posted as server side)
     * @param <E>   the event type
     * @return the same event, after every listener ran
     */
    public static <E extends Event> E post(E event, Level level) {
        LogicalSide side = level != null && level.isClientSide() ? LogicalSide.CLIENT : LogicalSide.SERVER;
        return post(event, side);
    }

    /**
     * Posts {@code event} on the given logical side.
     *
     * @param event the event, not {@code null}
     * @param side  the logical side
     * @param <E>   the event type
     * @return the same event, after every listener ran
     */
    public static <E extends Event> E post(E event, LogicalSide side) {
        try {
            event.setLogicalSide(side);
            EventBus.GAME.post(event);
        } catch (Throwable failure) {
            if (REPORTED.add(event.getClass().getName())) {
                System.err.println("[weaver] GAME EVENT POST FAILED event="
                        + event.getClass().getSimpleName() + " reason=" + failure);
            }
        }
        return event;
    }

    /**
     * Whether any listener is registered for {@code type}. Hooks on hot paths
     * (neighbor updates, crop ticks, block drops) check this first so they do
     * not build an event nobody listens to.
     */
    public static boolean hasListeners(Class<? extends Event> type) {
        try {
            return EventBus.GAME.hasListeners(type);
        } catch (Throwable failure) {
            return false;
        }
    }

    /** Whether {@code event} was canceled by a listener. */
    public static boolean canceled(Event event) {
        return event.isCancelable() && event.isCanceled();
    }
}
