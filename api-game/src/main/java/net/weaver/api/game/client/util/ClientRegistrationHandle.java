package net.weaver.api.game.client.util;

import net.weaver.api.client.ClientRegistration;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A small, thread-safe {@link ClientRegistration} implementation shared by the
 * game-typed client registries.
 *
 * <p>The handle owns an active flag and a removal action. Removal is idempotent
 * and never throws into game code: a failure is reported once to standard error
 * as a {@code [weaver] CLIENT RENDER UNREGISTER FAILED} line and swallowed.
 *
 * <p>{@link #deactivate()} is used by registry resets. It marks the handle
 * inactive without running the removal action, because the reset has already
 * cleared the registry.
 */
public final class ClientRegistrationHandle implements ClientRegistration
{
    private final String id;
    private final Runnable remover;
    private final AtomicBoolean active = new AtomicBoolean(true);

    private ClientRegistrationHandle(String id, Runnable remover)
    {
        this.id = id;
        this.remover = remover;
    }

    /**
     * Creates an active handle.
     *
     * @param id      the stable registration id, never {@code null} or blank
     * @param remover the removal action, never {@code null}
     * @return a new active handle, never {@code null}
     * @throws IllegalArgumentException when {@code id} is {@code null} or blank
     * @throws NullPointerException     when {@code remover} is {@code null}
     */
    public static ClientRegistrationHandle create(String id, Runnable remover)
    {
        if (id == null || id.isBlank())
        {
            throw new IllegalArgumentException("registration id must not be null or blank");
        }
        Objects.requireNonNull(remover, "remover");
        return new ClientRegistrationHandle(id, remover);
    }

    /** {@inheritDoc} */
    @Override
    public String id()
    {
        return id;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isActive()
    {
        return active.get();
    }

    /** {@inheritDoc} */
    @Override
    public void unregister()
    {
        if (!active.compareAndSet(true, false))
        {
            return;
        }
        try
        {
            remover.run();
        }
        catch (Throwable failure)
        {
            System.err.println("[weaver] CLIENT RENDER UNREGISTER FAILED id=" + id
                    + " reason=" + failure);
        }
    }

    /**
     * Marks the handle inactive without running its removal action.
     *
     * <p>Intended for registry resets, where the underlying entries are cleared
     * separately. This method is idempotent and thread-safe.
     */
    public void deactivate()
    {
        active.set(false);
    }
}
