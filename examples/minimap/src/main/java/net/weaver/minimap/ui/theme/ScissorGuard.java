package net.weaver.minimap.ui.theme;

import java.util.Objects;

/**
 * Auto-closable scissor guard.
 *
 * <p>Every enabled scissor must be disabled exactly once. The guard makes the
 * pairing explicit and tolerant of a double close, and its public constructor
 * lets tests supply counting actions without a live graphics context.</p>
 */
public final class ScissorGuard implements AutoCloseable
{
    private final Runnable closer;
    private boolean closed;

    /**
     * Runs the open action and prepares the guard.
     *
     * @param opener the enable action, never {@code null}
     * @param closer the disable action, never {@code null}
     */
    public ScissorGuard(Runnable opener, Runnable closer)
    {
        Objects.requireNonNull(opener, "opener");
        this.closer = Objects.requireNonNull(closer, "closer");
        opener.run();
    }

    /** Whether the guard is still open. */
    public boolean isOpen()
    {
        return !closed;
    }

    /** Whether the guard has been closed. */
    public boolean isClosed()
    {
        return closed;
    }

    @Override
    public void close()
    {
        if (closed)
        {
            return;
        }
        closed = true;
        closer.run();
    }
}
