package net.weaver.api.registry;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public final class RegistryEntry<T>
{
    private enum State
    {
        UNBOUND,
        BOUND,
        FAILED
    }

    private final RegistryKey<T> key;
    private volatile State state = State.UNBOUND;
    private volatile Object value;
    private volatile Object holder;
    private volatile Throwable failure;

    RegistryEntry(RegistryKey<T> key)
    {
        this.key = Objects.requireNonNull(key, "key");
    }

    public RegistryKey<T> key()
    {
        return key;
    }

    public String id()
    {
        return key.id();
    }

    @SuppressWarnings("unchecked")
    public T get()
    {
        if (state == State.BOUND)
        {
            return (T) value;
        }
        if (state == State.FAILED)
        {
            throw new RegistryException("Registry entry " + id() + " failed to bind", failure);
        }
        throw new RegistryException("Registry entry " + id() + " is not bound");
    }

    @SuppressWarnings("unchecked")
    public T getOrNull()
    {
        return state == State.BOUND ? (T) value : null;
    }

    public Optional<T> optional()
    {
        return Optional.ofNullable(getOrNull());
    }

    public boolean isPresent()
    {
        return state == State.BOUND;
    }

    public boolean isBound()
    {
        return state == State.BOUND;
    }

    public Object holder()
    {
        return state == State.BOUND ? holder : null;
    }

    public <H> H holder(Class<H> type)
    {
        Objects.requireNonNull(type, "type");
        final Object currentHolder = holder();
        if (currentHolder == null)
        {
            throw new RegistryException("Registry entry " + id() + " has no holder");
        }
        if (!type.isInstance(currentHolder))
        {
            throw new RegistryException("Registry entry " + id() + " holder is not a "
                    + type.getName());
        }
        return type.cast(currentHolder);
    }

    public <H> Optional<H> holderOptional(Class<H> type)
    {
        Objects.requireNonNull(type, "type");
        final Object currentHolder = holder();
        if (currentHolder == null || !type.isInstance(currentHolder))
        {
            return Optional.empty();
        }
        return Optional.of(type.cast(currentHolder));
    }

    public void ifPresent(Consumer<? super T> consumer)
    {
        Objects.requireNonNull(consumer, "consumer");
        if (state == State.BOUND)
        {
            consumer.accept(getOrNull());
        }
    }

    void bind(Object value, Object holder)
    {
        this.value = Objects.requireNonNull(value, "value");
        this.holder = holder;
        this.failure = null;
        this.state = State.BOUND;
    }

    void fail(Throwable failure)
    {
        this.value = null;
        this.holder = null;
        this.failure = Objects.requireNonNull(failure, "failure");
        this.state = State.FAILED;
    }

    boolean isUnbound()
    {
        return state == State.UNBOUND;
    }

    Throwable failure()
    {
        return failure;
    }
}
