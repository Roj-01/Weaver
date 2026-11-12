package net.weaver.api.capability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Registry of capabilities and the attachment stores that hold their values.
 *
 * <p>Each registry owns an ordered list of stores. Higher priority stores are
 * consulted first and equal priorities keep registration order. A new registry
 * always contains a {@link RuntimeAttachmentStore} and then tries to install
 * the core ItemStack stores reflectively, so an API-only classpath keeps
 * working without shared wiring.
 *
 * <p>Read operations fail soft: a store failure is recorded as a diagnostic and
 * the capability default, an empty result, or {@code false} is returned. Write
 * operations propagate store failures.
 */
public final class AttachmentRegistry {

    private static final int MAX_DIAGNOSTICS = 32;

    private final Object lock = new Object();
    private final Map<String, Capability<?>> capabilities = new HashMap<>();
    private final List<Capability<?>> registrationOrder = new ArrayList<>();
    private final List<AttachmentStore> stores = new ArrayList<>();
    private final List<AttachmentChangeListener> changeListeners = new ArrayList<>();
    private final List<String> diagnostics = new ArrayList<>();
    private Consumer<String> diagnosticListener;

    /**
     * Creates an isolated registry.
     *
     * <p>The new instance starts with a {@link RuntimeAttachmentStore} and then
     * attempts to install the core capability stores through
     * {@code net.weaver.core.capability.CapabilityStores}. Every failure from
     * that reflective installation is swallowed so the API can run without the
     * core artifact.
     */
    public AttachmentRegistry() {
        registerStore(new RuntimeAttachmentStore());
        installCoreStores();
    }

    /**
     * Returns the process-wide registry.
     *
     * <p>The instance is created on first use and is shared by the API and the
     * core bridge.
     *
     * @return the global registry
     */
    public static AttachmentRegistry global() {
        return GlobalHolder.INSTANCE;
    }

    /**
     * Registers a capability.
     *
     * <p>Registering the same instance under the same id is idempotent.
     * Registering a different instance under an existing id throws, even when
     * the value type matches.
     *
     * @param capability the non-null capability
     * @throws NullPointerException when {@code capability} is {@code null}
     * @throws CapabilityException  when the id is already registered to a
     *                              different instance
     */
    public void register(Capability<?> capability) {
        Objects.requireNonNull(capability, "capability");
        String id = capability.id();
        synchronized (lock) {
            Capability<?> existing = capabilities.get(id);
            if (existing == capability) {
                return;
            }
            if (existing != null) {
                throw new CapabilityException(
                        "Capability id is already registered: " + id);
            }
            capabilities.put(id, capability);
            registrationOrder.add(capability);
        }
    }

    /**
     * Finds a registered capability by id.
     *
     * @param id the capability id in {@code namespace:path} form
     * @return the capability, or an empty optional when it is not registered
     * @throws NullPointerException when {@code id} is {@code null}
     */
    public Optional<Capability<?>> find(String id) {
        Objects.requireNonNull(id, "id");
        synchronized (lock) {
            return Optional.ofNullable(capabilities.get(id));
        }
    }

    /**
     * Returns an immutable snapshot of the registered capabilities in
     * registration order.
     *
     * @return the registered capabilities
     */
    public List<Capability<?>> registeredCapabilities() {
        synchronized (lock) {
            return List.copyOf(registrationOrder);
        }
    }

    /**
     * Registers an attachment store.
     *
     * <p>Stores are kept in priority order with higher priorities first.
     * Registering the same instance again is idempotent.
     *
     * @param store the non-null store
     * @throws NullPointerException when {@code store} is {@code null}
     */
    public void registerStore(AttachmentStore store) {
        Objects.requireNonNull(store, "store");
        synchronized (lock) {
            for (AttachmentStore existing : stores) {
                if (existing == store) {
                    return;
                }
            }
            int index = 0;
            while (index < stores.size()
                    && stores.get(index).priority() >= store.priority()) {
                index++;
            }
            stores.add(index, store);
        }
    }

    /**
     * Removes an attachment store instance.
     *
     * @param store the store to remove
     * @return {@code true} when the instance was registered
     */
    public boolean removeStore(AttachmentStore store) {
        synchronized (lock) {
            for (int index = 0; index < stores.size(); index++) {
                if (stores.get(index) == store) {
                    stores.remove(index);
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Tests whether any registered store supports a holder and capability.
     *
     * @param holder     the non-null holder
     * @param capability the non-null capability
     * @return {@code true} when a store supports the pair
     * @throws NullPointerException when {@code holder} or {@code capability} is
     *                              {@code null}
     */
    public boolean supports(Object holder, Capability<?> capability) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(capability, "capability");
        for (AttachmentStore store : storeSnapshot()) {
            if (store.supports(holder, capability)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the value for a holder and capability.
     *
     * <p>Stores are consulted in priority order. When no store has an explicit
     * value, {@link Capability#defaultValue()} is returned. A store failure is
     * recorded as a diagnostic and also returns the default.
     *
     * @param holder     the non-null holder
     * @param capability the non-null capability
     * @param <T>        the value type
     * @return the explicit value, the default value, or {@code null}
     * @throws NullPointerException when {@code holder} or {@code capability} is
     *                              {@code null}
     */
    public <T> T get(Object holder, Capability<T> capability) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(capability, "capability");
        for (AttachmentStore store : storeSnapshot()) {
            if (!store.supports(holder, capability)) {
                continue;
            }
            try {
                T value = store.get(holder, capability);
                if (value != null) {
                    return value;
                }
            } catch (RuntimeException | LinkageError | StackOverflowError failure) {
                storeFailure(store, capability, "get", failure);
                return capability.defaultValue();
            }
        }
        T attached = attachInitial(holder, capability);
        return attached != null ? attached : capability.defaultValue();
    }

    /** One attach rule: holders of a type get a computed first value. */
    private record Attacher(Capability<?> capability, Class<?> holderType, java.util.function.Function<Object, ?> initial) {
    }

    private final java.util.concurrent.CopyOnWriteArrayList<Attacher> attachers =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Attaches {@code capability} to every holder of {@code holderType} on first use:
     * the first {@link #get} of a holder with no explicit value computes
     * {@code initial} from the holder, stores it (so it is saved and synced like any
     * set value, and the same object comes back next time), and returns it. This is
     * the attach step of other loaders' capability systems, done lazily: holders
     * nobody asks about cost nothing.
     *
     * <p>{@link #find} and {@link #has} do not attach; they report only explicit
     * values. When several rules match, the first registered wins. A rule that throws
     * or returns {@code null} is skipped (the failure is recorded as a diagnostic),
     * and the capability default applies.</p>
     *
     * @param capability the capability
     * @param holderType the holder class (an entity class, a block entity class, ...); subclasses match
     * @param initial    the first value for a holder
     * @param <H>        the holder type
     * @param <T>        the value type
     */
    @SuppressWarnings("unchecked")
    public <H, T> void attach(Capability<T> capability, Class<H> holderType,
            java.util.function.Function<? super H, ? extends T> initial) {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(holderType, "holderType");
        Objects.requireNonNull(initial, "initial");
        attachers.add(new Attacher(capability, holderType, (java.util.function.Function<Object, ?>) initial));
    }

    /** The number of attach rules for {@code capability}. */
    public int attacherCount(Capability<?> capability) {
        int count = 0;
        for (Attacher attacher : attachers) {
            if (attacher.capability() == capability) {
                count++;
            }
        }
        return count;
    }

    private final Object attachLock = new Object();

    private <T> T attachInitial(Object holder, Capability<T> capability) {
        if (attachers.isEmpty()) {
            return null;
        }
        boolean matches = false;
        for (Attacher attacher : attachers) {
            if (attacher.capability() == capability && attacher.holderType().isInstance(holder)) {
                matches = true;
                break;
            }
        }
        // Without a store for the pair, a computed value could not be kept: computing it
        // on every get would repeat the work (and the diagnostics) forever.
        if (!matches || !supports(holder, capability)) {
            return null;
        }
        synchronized (attachLock) {
            // Another thread may have attached while this one waited: return its object.
            Optional<T> existing = find(holder, capability);
            if (existing.isPresent()) {
                return existing.get();
            }
            return computeAttached(holder, capability);
        }
    }

    private <T> T computeAttached(Object holder, Capability<T> capability) {
        for (Attacher attacher : attachers) {
            if (attacher.capability() != capability || !attacher.holderType().isInstance(holder)) {
                continue;
            }
            Object computed;
            try {
                computed = attacher.initial().apply(holder);
            } catch (RuntimeException | LinkageError | StackOverflowError failure) {
                recordDiagnostic("attach failed capability=" + capability.id() + " holder="
                        + holder.getClass().getName() + " reason=" + failure);
                continue;
            }
            Class<T> type = capability.valueType();
            if (computed == null || type == null || !type.isInstance(computed)) {
                continue;
            }
            T value = type.cast(computed);
            try {
                set(holder, capability, value);
            } catch (RuntimeException unsupported) {
                recordDiagnostic("attach not stored capability=" + capability.id() + " holder="
                        + holder.getClass().getName() + " reason=" + unsupported);
            }
            return value;
        }
        return null;
    }

    /**
     * Returns the explicit value for a holder and capability.
     *
     * <p>Stores are consulted in priority order. The capability default is
     * never substituted. A store failure is recorded as a diagnostic and
     * yields an empty result.
     *
     * @param holder     the non-null holder
     * @param capability the non-null capability
     * @param <T>        the value type
     * @return the explicit value, or an empty optional when absent
     * @throws NullPointerException when {@code holder} or {@code capability} is
     *                              {@code null}
     */
    public <T> Optional<T> find(Object holder, Capability<T> capability) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(capability, "capability");
        for (AttachmentStore store : storeSnapshot()) {
            if (!store.supports(holder, capability)) {
                continue;
            }
            try {
                T value = store.get(holder, capability);
                if (value != null) {
                    return Optional.of(value);
                }
            } catch (RuntimeException | LinkageError | StackOverflowError failure) {
                storeFailure(store, capability, "find", failure);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Stores a value and returns the previous explicit value.
     *
     * <p>A {@code null} value removes the explicit value. Every other value is
     * checked against {@link Capability#valueType()} before the first
     * supporting store is asked to store it. Store failures propagate.
     *
     * @param holder     the non-null holder
     * @param capability the non-null capability
     * @param value      the value to store, or {@code null} to remove
     * @param <T>        the value type
     * @return the previous explicit value, or {@code null}
     * @throws NullPointerException when {@code holder} or {@code capability} is
     *                              {@code null}
     * @throws CapabilityException  when the value type is wrong or no store
     *                              supports the pair
     */
    public <T> T set(Object holder, Capability<T> capability, T value) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(capability, "capability");
        if (value == null) {
            return remove(holder, capability);
        }
        Class<T> type = capability.valueType();
        if (type == null || !type.isInstance(value)) {
            throw new CapabilityException("Value is not a valid " + capability.id()
                    + " value");
        }
        for (AttachmentStore store : storeSnapshot()) {
            if (store.supports(holder, capability)) {
                T previous = store.set(holder, capability, value);
                notifyChange(new AttachmentChange(holder, capability, previous, value,
                        AttachmentChange.Kind.SET));
                return previous;
            }
        }
        throw new CapabilityException("No attachment store supports capability "
                + capability.id() + " for holder " + holder.getClass().getName());
    }

    /**
     * Removes the explicit value and returns it.
     *
     * <p>The first supporting store is asked to remove the value. Store
     * failures propagate.
     *
     * @param holder     the non-null holder
     * @param capability the non-null capability
     * @param <T>        the value type
     * @return the removed explicit value, or {@code null} when none was present
     * @throws NullPointerException when {@code holder} or {@code capability} is
     *                              {@code null}
     */
    public <T> T remove(Object holder, Capability<T> capability) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(capability, "capability");
        for (AttachmentStore store : storeSnapshot()) {
            if (store.supports(holder, capability)) {
                T previous = store.remove(holder, capability);
                notifyChange(new AttachmentChange(holder, capability, previous, null,
                        AttachmentChange.Kind.REMOVE));
                return previous;
            }
        }
        return null;
    }

    /**
     * Tests whether an explicit value is present.
     *
     * <p>Stores are consulted in priority order. A store failure is recorded as
     * a diagnostic and returns {@code false}.
     *
     * @param holder     the non-null holder
     * @param capability the non-null capability
     * @param <T>        the value type
     * @return {@code true} when an explicit value is present
     * @throws NullPointerException when {@code holder} or {@code capability} is
     *                              {@code null}
     */
    public <T> boolean has(Object holder, Capability<T> capability) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(capability, "capability");
        for (AttachmentStore store : storeSnapshot()) {
            if (!store.supports(holder, capability)) {
                continue;
            }
            try {
                if (store.has(holder, capability)) {
                    return true;
                }
            } catch (RuntimeException | LinkageError | StackOverflowError failure) {
                storeFailure(store, capability, "has", failure);
                return false;
            }
        }
        return false;
    }

    /**
     * Clears every registered store for a holder.
     *
     * <p>Every store is asked to clear the holder, including stores that do not
     * recognize it and should therefore do nothing. A failing store is recorded
     * as a diagnostic and the remaining stores are still cleared.
     *
     * @param holder the non-null holder
     * @throws NullPointerException when {@code holder} is {@code null}
     */
    public void clear(Object holder) {
        Objects.requireNonNull(holder, "holder");
        for (AttachmentStore store : storeSnapshot()) {
            try {
                store.clear(holder);
            } catch (RuntimeException | LinkageError | StackOverflowError failure) {
                recordDiagnostic("Attachment store " + store.getClass().getName()
                        + " failed during clear for " + holder.getClass().getName()
                        + ": " + failure);
            }
        }
        notifyChange(new AttachmentChange(holder, null, null, null,
                AttachmentChange.Kind.CLEAR));
    }

    /**
     * Writes a holder's persistent values into a game output carrier.
     *
     * <p>The first {@link PersistentAttachmentStore} that supports the holder
     * writes every codec-backed explicit value, plus the retained bytes of ids
     * this session could not decode, under the {@code weaver:capabilities}
     * child. A store failure is recorded as a diagnostic.
     *
     * @param holder the non-null holder
     * @param output the game {@code ValueOutput}
     * @return {@code true} when a persistent store handled the holder
     * @throws NullPointerException when an argument is {@code null}
     */
    public boolean saveHolder(Object holder, Object output) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(output, "output");
        PersistentAttachmentStore store = persistentStore(holder);
        if (store == null) {
            return false;
        }
        try {
            store.saveTo(holder, output);
        } catch (RuntimeException | LinkageError | StackOverflowError failure) {
            recordDiagnostic("Attachment store " + store.getClass().getName()
                    + " failed to save " + holder.getClass().getName() + ": " + failure);
        }
        return true;
    }

    /**
     * Reads a holder's persistent values from a game input carrier, as
     * written by {@link #saveHolder}.
     *
     * @param holder the non-null holder
     * @param input  the game {@code ValueInput}
     * @return {@code true} when a persistent store handled the holder
     * @throws NullPointerException when an argument is {@code null}
     */
    public boolean loadHolder(Object holder, Object input) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(input, "input");
        PersistentAttachmentStore store = persistentStore(holder);
        if (store == null) {
            return false;
        }
        try {
            store.loadFrom(holder, input);
        } catch (RuntimeException | LinkageError | StackOverflowError failure) {
            recordDiagnostic("Attachment store " + store.getClass().getName()
                    + " failed to load " + holder.getClass().getName() + ": " + failure);
        }
        return true;
    }

    /**
     * Whether {@link #saveHolder} could write anything for {@code holder}.
     * A cheap check that lets a save hook skip holders with no values.
     *
     * @param holder the non-null holder
     * @return {@code false} only when no persistent store has data for it
     */
    public boolean hasPersistentData(Object holder) {
        Objects.requireNonNull(holder, "holder");
        PersistentAttachmentStore store = persistentStore(holder);
        if (store == null) {
            return false;
        }
        try {
            return store.hasData(holder);
        } catch (RuntimeException | LinkageError | StackOverflowError failure) {
            recordDiagnostic("Attachment store " + store.getClass().getName()
                    + " failed during hasData: " + failure);
            return true;
        }
    }

    private PersistentAttachmentStore persistentStore(Object holder) {
        for (AttachmentStore store : storeSnapshot()) {
            if (store instanceof PersistentAttachmentStore persistent) {
                try {
                    if (persistent.supportsHolder(holder)) {
                        return persistent;
                    }
                } catch (RuntimeException | LinkageError | StackOverflowError failure) {
                    recordDiagnostic("Attachment store " + store.getClass().getName()
                            + " failed during supportsHolder: " + failure);
                }
            }
        }
        return null;
    }

    /**
     * Adds an attachment change listener.
     *
     * <p>Listeners are notified after a successful {@code set}, {@code remove},
     * or {@code clear}. Registering the same instance again is idempotent.
     *
     * @param listener the non-null listener
     * @throws NullPointerException when {@code listener} is {@code null}
     */
    public void addChangeListener(AttachmentChangeListener listener) {
        Objects.requireNonNull(listener, "listener");
        synchronized (lock) {
            if (!changeListeners.contains(listener)) {
                changeListeners.add(listener);
            }
        }
    }

    /**
     * Removes an attachment change listener.
     *
     * @param listener the listener to remove
     * @return {@code true} when the instance was registered
     */
    public boolean removeChangeListener(AttachmentChangeListener listener) {
        if (listener == null) {
            return false;
        }
        synchronized (lock) {
            return changeListeners.remove(listener);
        }
    }

    /**
     * Returns the number of registered attachment change listeners.
     *
     * @return the listener count
     */
    public int changeListenerCount() {
        synchronized (lock) {
            return changeListeners.size();
        }
    }

    /**
     * Returns an immutable diagnostic snapshot, newest last.
     *
     * <p>At most 32 messages are retained; older messages are dropped first.
     *
     * @return the retained diagnostics
     */
    public List<String> diagnostics() {
        synchronized (lock) {
            return List.copyOf(diagnostics);
        }
    }

    /**
     * Clears the retained diagnostic history.
     *
     * <p>The configured diagnostic observer is not changed.
     */
    public void clearDiagnostics() {
        synchronized (lock) {
            diagnostics.clear();
        }
    }

    /**
     * Installs a diagnostic observer.
     *
     * <p>The observer is called in addition to the bounded diagnostic history
     * for every new message. Passing {@code null} resets the observer to a
     * no-op. Observer failures are swallowed.
     *
     * @param listener the observer, or {@code null} to disable it
     */
    public void setDiagnostics(Consumer<String> listener) {
        synchronized (lock) {
            diagnosticListener = listener;
        }
    }

    private List<AttachmentStore> storeSnapshot() {
        synchronized (lock) {
            return new ArrayList<>(stores);
        }
    }

    private void notifyChange(AttachmentChange change) {
        List<AttachmentChangeListener> listeners;
        synchronized (lock) {
            listeners = List.copyOf(changeListeners);
        }
        for (AttachmentChangeListener listener : listeners) {
            try {
                listener.changed(change);
            } catch (Throwable failure) {
                recordDiagnostic("Attachment change listener "
                        + listener.getClass().getName() + " failed: " + failure);
            }
        }
    }

    private void storeFailure(AttachmentStore store, Capability<?> capability,
            String operation, Throwable failure) {
        recordDiagnostic("Attachment store " + store.getClass().getName()
                + " failed during " + operation + " for " + capability.id()
                + ": " + failure);
    }

    private void recordDiagnostic(String message) {
        Consumer<String> listener;
        synchronized (lock) {
            if (diagnostics.size() == MAX_DIAGNOSTICS) {
                diagnostics.remove(0);
            }
            diagnostics.add(message);
            listener = diagnosticListener;
        }
        if (listener != null) {
            try {
                listener.accept(message);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void installCoreStores() {
        try {
            Class<?> stores = Class.forName("net.weaver.core.capability.CapabilityStores",
                    true, AttachmentRegistry.class.getClassLoader());
            stores.getMethod("install", AttachmentRegistry.class).invoke(null, this);
        } catch (Throwable ignored) {
        }
    }

    private static final class GlobalHolder {

        private static final AttachmentRegistry INSTANCE = new AttachmentRegistry();
    }
}
