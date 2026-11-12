package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.List;
import java.util.Objects;

/**
 * Posted after a developer hot swap: the loader replaced the code of classes
 * that changed in the watched build output, while the game kept running.
 *
 * <p>Hot swap is a development feature. It runs only when the game was started
 * with {@code -Dweaver.dev.hotswap=true} (the Gradle {@code runClient} and
 * {@code runServer} tasks set it). It replaces method bodies of mod classes that
 * are already loaded; a change to a class's shape (a field or method added or
 * removed), to a mixin class, or to a class the game has not loaded yet is not
 * swapped and is listed in {@link #skipped()} with the reason. A mod can listen
 * to re-run setup that cached values computed by the old code.</p>
 *
 * <p>Posted on {@link net.weaver.api.event.EventBus#GAME} from the watcher
 * thread, with logical side {@link net.weaver.api.event.LogicalSide#BOTH}.
 * Not cancelable.</p>
 */
public final class HotSwapEvent extends Event
{
    /**
     * A class that was not swapped.
     *
     * @param className the binary class name, not {@code null}
     * @param reason    why it was not swapped, not {@code null}
     */
    public record Skipped(String className, String reason)
    {
        /**
         * Validates the fields.
         *
         * @throws NullPointerException when a field is {@code null}
         */
        public Skipped
        {
            Objects.requireNonNull(className, "className");
            Objects.requireNonNull(reason, "reason");
        }
    }

    private final List<String> swapped;
    private final List<Skipped> skipped;

    /**
     * Creates the event for one swap.
     *
     * @param swapped the binary names of the swapped classes, not {@code null}
     * @param skipped the classes that changed but were not swapped, not {@code null}
     */
    public HotSwapEvent(List<String> swapped, List<Skipped> skipped)
    {
        this.swapped = List.copyOf(Objects.requireNonNull(swapped, "swapped"));
        this.skipped = List.copyOf(Objects.requireNonNull(skipped, "skipped"));
    }

    /** The classes whose new code is now running. */
    public List<String> swapped()
    {
        return swapped;
    }

    /** The changed classes that need a restart, with the reason. */
    public List<Skipped> skipped()
    {
        return skipped;
    }

    /** Whether every changed class was swapped. */
    public boolean complete()
    {
        return skipped.isEmpty();
    }
}
