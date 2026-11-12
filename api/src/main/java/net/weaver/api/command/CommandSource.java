package net.weaver.api.command;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * An immutable wrapper around a raw game command source.
 *
 * <p>The raw source is kept opaque. {@link #isAllowed(PermissionNode)} delegates
 * to {@link PermissionService} with the raw source, so the installed
 * {@link PermissionResolver} decides the subject and op level. Feedback
 * consumers may be {@code null}; missing feedback is silently discarded.
 *
 * <p>Instances are immutable and thread-safe. {@link #sendSuccess(String)} and
 * {@link #sendFailure(String)} are safe to call from any thread and never
 * throw: a failing feedback consumer is ignored. The raw game source itself
 * remains subject to the game's own threading rules.
 */
public final class CommandSource
{
    private final Object raw;
    private final String name;
    private final String displayName;
    private final Consumer<String> success;
    private final Consumer<String> failure;

    /**
     * Creates a command source.
     *
     * @param raw         the raw game command source, not {@code null}
     * @param name        the source name used for diagnostics, not {@code null}
     * @param displayName the display name, not {@code null}
     * @param success     the success feedback consumer, or {@code null}
     * @param failure     the failure feedback consumer, or {@code null}
     * @throws NullPointerException when {@code raw}, {@code name}, or
     *                              {@code displayName} is {@code null}
     */
    public CommandSource(Object raw, String name, String displayName,
            Consumer<String> success, Consumer<String> failure)
    {
        this.raw = Objects.requireNonNull(raw, "raw");
        this.name = Objects.requireNonNull(name, "name");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.success = success;
        this.failure = failure;
    }

    /**
     * Creates a source with no feedback consumers.
     *
     * <p>The source and display names are {@code String.valueOf(raw)}.
     *
     * @param raw the raw game command source, not {@code null}
     * @return the source, never {@code null}
     * @throws NullPointerException when {@code raw} is {@code null}
     */
    public static CommandSource of(Object raw)
    {
        Objects.requireNonNull(raw, "raw");
        String name = String.valueOf(raw);
        return new CommandSource(raw, name, name, null, null);
    }

    /**
     * The raw game command source.
     *
     * @return the raw source, never {@code null}
     */
    public Object raw()
    {
        return raw;
    }

    /**
     * The diagnostic source name.
     *
     * @return the name, never {@code null}
     */
    public String name()
    {
        return name;
    }

    /**
     * The display name.
     *
     * @return the display name, never {@code null}
     */
    public String displayName()
    {
        return displayName;
    }

    /**
     * Evaluates a permission node for this source.
     *
     * @param node the node to evaluate, not {@code null}
     * @return {@code true} when the node is allowed
     * @throws NullPointerException when {@code node} is {@code null}
     */
    public boolean isAllowed(PermissionNode node)
    {
        return PermissionService.isAllowed(raw, node);
    }

    /**
     * Sends a success message to the source.
     *
     * <p>Safe from any thread and never throws. A missing consumer is a no-op,
     * and a consumer failure is ignored.
     *
     * @param message the message, or {@code null} for an empty message
     */
    public void sendSuccess(String message)
    {
        deliver(success, message);
    }

    /**
     * Sends a failure message to the source.
     *
     * <p>Safe from any thread and never throws. A missing consumer is a no-op,
     * and a consumer failure is ignored.
     *
     * @param message the message, or {@code null} for an empty message
     */
    public void sendFailure(String message)
    {
        deliver(failure, message);
    }

    private static void deliver(Consumer<String> consumer, String message)
    {
        if (consumer == null)
        {
            return;
        }
        try
        {
            consumer.accept(message != null ? message : "");
        }
        catch (Throwable ignored)
        {
        }
    }
}
