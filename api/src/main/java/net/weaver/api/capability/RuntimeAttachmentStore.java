package net.weaver.api.capability;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Built-in runtime storage for capability values on any non-null holder.
 *
 * <p>Holders are identified by identity, so two equal but distinct holder
 * instances are separate holders. Keys are weakly held and values are strongly
 * held, so a holder that is no longer reachable can be collected; its values
 * are dropped with it. All operations are synchronized on a private lock.
 */
public final class RuntimeAttachmentStore implements AttachmentStore {

    private final Object lock = new Object();
    private final ReferenceQueue<Object> collected = new ReferenceQueue<>();
    private final WeakHashMap<IdentityKey, HolderValues> holders = new WeakHashMap<>();

    /**
     * Creates an empty runtime store.
     */
    public RuntimeAttachmentStore() {
    }

    @Override
    public int priority() {
        return Integer.MIN_VALUE;
    }

    @Override
    public boolean supports(Object holder, Capability<?> capability) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(capability, "capability");
        return true;
    }

    @Override
    public <T> T get(Object holder, Capability<T> capability) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(capability, "capability");
        synchronized (lock) {
            expunge();
            HolderValues values = holders.get(new IdentityKey(holder));
            return values == null ? null : cast(capability, values.values.get(capability));
        }
    }

    @Override
    public <T> T set(Object holder, Capability<T> capability, T value) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(value, "value");
        Class<T> type = capability.valueType();
        if (type == null || !type.isInstance(value)) {
            throw new CapabilityException("Value is not a valid " + capability.id()
                    + " value");
        }
        synchronized (lock) {
            expunge();
            HolderValues values = holders.get(new IdentityKey(holder));
            if (values == null) {
                IdentityKey key = new IdentityKey(holder, collected);
                values = new HolderValues(key);
                holders.put(key, values);
            }
            return cast(capability, values.values.put(capability, value));
        }
    }

    @Override
    public <T> T remove(Object holder, Capability<T> capability) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(capability, "capability");
        synchronized (lock) {
            expunge();
            IdentityKey key = new IdentityKey(holder);
            HolderValues values = holders.get(key);
            if (values == null) {
                return null;
            }
            Object removed = values.values.remove(capability);
            if (values.values.isEmpty()) {
                holders.remove(values.key);
            }
            return cast(capability, removed);
        }
    }

    @Override
    public boolean has(Object holder, Capability<?> capability) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(capability, "capability");
        synchronized (lock) {
            expunge();
            HolderValues values = holders.get(new IdentityKey(holder));
            return values != null && values.values.containsKey(capability);
        }
    }

    @Override
    public void clear(Object holder) {
        Objects.requireNonNull(holder, "holder");
        synchronized (lock) {
            expunge();
            HolderValues values = holders.get(new IdentityKey(holder));
            if (values != null) {
                holders.remove(values.key);
            }
        }
    }

    /**
     * Returns the number of weakly-held holders with stored values.
     *
     * <p>This is a diagnostic method. Holders collected before the call are
     * removed before the count is taken.
     *
     * @return the current holder count
     */
    public int holderCount() {
        synchronized (lock) {
            expunge();
            return holders.size();
        }
    }

    /**
     * Returns the total number of explicit values across all holders.
     *
     * <p>This is a diagnostic method. Holders collected before the call are
     * removed before the count is taken.
     *
     * @return the current explicit value count
     */
    public int size() {
        synchronized (lock) {
            expunge();
            int total = 0;
            for (HolderValues values : holders.values()) {
                total += values.values.size();
            }
            return total;
        }
    }

    private void expunge() {
        Object reference;
        while ((reference = collected.poll()) != null) {
            final Object collectedKey = reference;
            holders.entrySet().removeIf(entry -> entry.getKey() == collectedKey);
        }
    }

    private static <T> T cast(Capability<T> capability, Object value) {
        if (value == null) {
            return null;
        }
        Class<T> type = capability.valueType();
        if (type == null || !type.isInstance(value)) {
            throw new CapabilityException("Stored value is not a valid "
                    + capability.id() + " value");
        }
        return type.cast(value);
    }

    private static final class HolderValues {

        private final IdentityKey key;
        private final Map<Capability<?>, Object> values = new HashMap<>();

        private HolderValues(IdentityKey key) {
            this.key = key;
        }
    }

    private static final class IdentityKey extends WeakReference<Object> {

        private final int hash;

        private IdentityKey(Object holder) {
            super(holder);
            this.hash = System.identityHashCode(holder);
        }

        private IdentityKey(Object holder, ReferenceQueue<Object> queue) {
            super(holder, queue);
            this.hash = System.identityHashCode(holder);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof IdentityKey key)) {
                return false;
            }
            Object holder = get();
            return holder != null && holder == key.get();
        }
    }
}
