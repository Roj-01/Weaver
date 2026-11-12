package net.weaver.api.game.event.client;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import net.minecraft.util.debug.DebugSubscription;
import net.weaver.api.event.Event;

/**
 * Fired on the client each time it works out which debug data to ask the
 * server for ({@code ClientDebugSubscriber#requestedSubscriptions}, every client
 * tick). Add a subscription to receive its debug values, for example to draw a
 * mod's own debug renderer. The server only sends values to operators or in
 * single player.
 */
public final class AddDebugSubscriptionFlagsEvent extends Event {
    private final Set<DebugSubscription<?>> subscriptions;

    public AddDebugSubscriptionFlagsEvent(Set<DebugSubscription<?>> subscriptions) {
        this.subscriptions = subscriptions;
    }

    /** Requests a subscription. */
    public void add(DebugSubscription<?> subscription) {
        subscriptions.add(Objects.requireNonNull(subscription, "subscription"));
    }

    /** Requests a subscription when {@code flag} is true. */
    public void addIf(DebugSubscription<?> subscription, boolean flag) {
        if (flag) {
            add(subscription);
        }
    }

    /** The subscriptions requested so far (read-only). */
    public Set<DebugSubscription<?>> subscriptions() {
        return Collections.unmodifiableSet(subscriptions);
    }
}
