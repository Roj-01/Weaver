package net.weaver.core.capability;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import net.weaver.api.capability.AttachmentRegistry;
import net.weaver.api.capability.Capability;
import net.weaver.api.capability.CapabilityCodec;
import net.weaver.api.capability.CapabilityException;
import net.weaver.api.capability.PersistentAttachmentStore;
import net.weaver.core.data.HolderDataLimits;
import net.weaver.core.data.ValueDataAccess;

/**
 * Weak identity storage shared by the runtime holder stores.
 *
 * <p>A store accepts every holder whose class hierarchy contains
 * {@code holderKind} and every non-null capability. Keys are weak references
 * with identity semantics and values are strongly held, matching
 * {@link net.weaver.api.capability.RuntimeAttachmentStore}. Persistence writes
 * only registered capabilities that have a codec and an explicit value; it
 * skips runtime-only capabilities, encoding failures, and bound violations so a
 * save or load never fails because of one value.</p>
 *
 * <p>Stored values this session cannot use (the capability is not registered,
 * has no codec, or fails to decode) are kept as raw bytes per holder and
 * written back unchanged on the next save, so a mod that is missing for one
 * session or a codec bug does not erase saved data. Setting or removing a
 * value for the same id discards the retained bytes for that id.</p>
 */
abstract class IdentityAttachmentStore implements PersistentAttachmentStore
{
    private final Object lock = new Object();
    private final ReferenceQueue<Object> collected = new ReferenceQueue<>();
    private final WeakHashMap<IdentityKey, HolderValues> holders = new WeakHashMap<>();
    private final AttachmentRegistry registry;
    private final String holderKind;
    private final ValueDataAccess access;

    IdentityAttachmentStore(AttachmentRegistry registry, String holderKind,
            ValueDataAccess access)
    {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.holderKind = Objects.requireNonNull(holderKind, "holderKind");
        this.access = Objects.requireNonNull(access, "access");
    }

    /**
     * Returns the class name this store accepts.
     *
     * @return the supported holder class name
     */
    final String holderKind()
    {
        return holderKind;
    }

    /**
     * Returns the object values are keyed by for {@code holder}. A store whose
     * game type has wrapper objects maps a wrapper to the object it wraps, so
     * both see the same values.
     *
     * @param holder the non-null supported holder
     * @return the key object
     */
    Object canonical(Object holder)
    {
        return holder;
    }

    @Override
    public final boolean supportsHolder(Object holder)
    {
        return holder != null && isAssignableTo(holder.getClass(), holderKind);
    }

    @Override
    public boolean supports(Object holder, Capability<?> capability)
    {
        return supportsHolder(holder) && capability != null;
    }

    @Override
    public <T> T get(Object holder, Capability<T> capability)
    {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(capability, "capability");
        synchronized (lock)
        {
            expunge();
            HolderValues values = holders.get(new IdentityKey(canonical(holder)));
            return values == null ? null : cast(capability, values.values.get(capability));
        }
    }

    @Override
    public <T> T set(Object holder, Capability<T> capability, T value)
    {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(value, "value");
        Class<T> type = capability.valueType();
        if (type == null || !type.isInstance(value))
        {
            throw new CapabilityException("Value is not a valid " + capability.id()
                    + " value");
        }
        synchronized (lock)
        {
            expunge();
            HolderValues values = holders.get(new IdentityKey(canonical(holder)));
            if (values == null)
            {
                IdentityKey key = new IdentityKey(canonical(holder), collected);
                values = new HolderValues(key);
                holders.put(key, values);
            }
            values.retained.remove(capability.id());
            return cast(capability, values.values.put(capability, value));
        }
    }

    @Override
    public <T> T remove(Object holder, Capability<T> capability)
    {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(capability, "capability");
        synchronized (lock)
        {
            expunge();
            HolderValues values = holders.get(new IdentityKey(canonical(holder)));
            if (values == null)
            {
                return null;
            }
            Object removed = values.values.remove(capability);
            values.retained.remove(capability.id());
            if (values.isEmpty())
            {
                holders.remove(values.key);
            }
            return cast(capability, removed);
        }
    }

    @Override
    public boolean has(Object holder, Capability<?> capability)
    {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(capability, "capability");
        synchronized (lock)
        {
            expunge();
            HolderValues values = holders.get(new IdentityKey(canonical(holder)));
            return values != null && values.values.containsKey(capability);
        }
    }

    @Override
    public void clear(Object holder)
    {
        if (!supportsHolder(holder))
        {
            return;
        }
        synchronized (lock)
        {
            expunge();
            HolderValues values = holders.get(new IdentityKey(canonical(holder)));
            if (values != null)
            {
                holders.remove(values.key);
            }
        }
    }

    @Override
    public boolean hasData(Object holder)
    {
        if (!supportsHolder(holder))
        {
            return false;
        }
        synchronized (lock)
        {
            expunge();
            HolderValues values = holders.get(new IdentityKey(canonical(holder)));
            return values != null && !values.isEmpty();
        }
    }

    @Override
    public void saveTo(Object holder, Object output)
    {
        Objects.requireNonNull(output, "output");
        if (!supportsHolder(holder))
        {
            return;
        }
        if (!access.supportsOutput(output))
        {
            log("SAVE", "output-unsupported", holder, null,
                    output.getClass().getName());
            return;
        }
        Map<String, byte[]> encoded = new LinkedHashMap<>();
        int total = 0;
        for (Capability<?> capability : registry.registeredCapabilities())
        {
            if (capability.codec() == null)
            {
                continue;
            }
            Object value = get(holder, capability);
            if (value == null)
            {
                continue;
            }
            if (encoded.size() >= HolderDataLimits.MAX_ENTRIES)
            {
                log("SAVE", "entry-limit", holder, capability,
                        "max=" + HolderDataLimits.MAX_ENTRIES);
                break;
            }
            byte[] bytes;
            try
            {
                bytes = encode(capability, value);
            }
            catch (RuntimeException | LinkageError | StackOverflowError failure)
            {
                log("SAVE", "encode-failed", holder, capability, failure);
                continue;
            }
            if (total + bytes.length > HolderDataLimits.MAX_TOTAL_BYTES)
            {
                log("SAVE", "total-limit", holder, capability,
                        "max=" + HolderDataLimits.MAX_TOTAL_BYTES);
                break;
            }
            encoded.put(capability.id(), bytes);
            total += bytes.length;
        }
        for (Map.Entry<String, byte[]> entry : retained(holder).entrySet())
        {
            String id = entry.getKey();
            byte[] bytes = entry.getValue();
            if (encoded.containsKey(id))
            {
                continue;
            }
            if (encoded.size() >= HolderDataLimits.MAX_ENTRIES)
            {
                logId("SAVE", "entry-limit", holder, id, "max=" + HolderDataLimits.MAX_ENTRIES);
                break;
            }
            if (total + bytes.length > HolderDataLimits.MAX_TOTAL_BYTES)
            {
                logId("SAVE", "total-limit", holder, id,
                        "max=" + HolderDataLimits.MAX_TOTAL_BYTES);
                continue;
            }
            encoded.put(id, bytes);
            total += bytes.length;
        }
        try
        {
            access.writeAll(output, HolderDataLimits.ROOT_KEY, encoded);
        }
        catch (RuntimeException | LinkageError | StackOverflowError failure)
        {
            log("SAVE", "carrier-failed", holder, null, failure);
        }
    }

    @Override
    public void loadFrom(Object holder, Object input)
    {
        Objects.requireNonNull(input, "input");
        if (!supportsHolder(holder))
        {
            return;
        }
        if (!access.supportsInput(input))
        {
            log("LOAD", "input-unsupported", holder, null,
                    input.getClass().getName());
            return;
        }
        List<Capability<?>> capabilities = registry.registeredCapabilities();
        List<String> ids = capabilities.stream()
                .filter(capability -> capability.codec() != null)
                .map(Capability::id)
                .toList();
        Map<String, byte[]> stored;
        try
        {
            stored = access.readAllStored(input, HolderDataLimits.ROOT_KEY);
            if (stored == null)
            {
                stored = access.readAll(input, HolderDataLimits.ROOT_KEY, ids);
            }
        }
        catch (RuntimeException | LinkageError | StackOverflowError failure)
        {
            log("LOAD", "carrier-failed", holder, null, failure);
            return;
        }
        // Keep the raw bytes of every stored id this session cannot use (the
        // capability is unregistered, runtime-only, or fails to decode), so
        // the next save writes them back instead of erasing them.
        Map<String, byte[]> retained = new LinkedHashMap<>(stored);
        int total = 0;
        for (Capability<?> capability : capabilities)
        {
            byte[] bytes = stored.get(capability.id());
            if (bytes == null || bytes.length == 0 || capability.codec() == null)
            {
                continue;
            }
            if (total + bytes.length > HolderDataLimits.MAX_TOTAL_BYTES)
            {
                log("LOAD", "total-limit", holder, capability,
                        "max=" + HolderDataLimits.MAX_TOTAL_BYTES);
                break;
            }
            total += bytes.length;
            try
            {
                Object value = decode(capability, bytes);
                apply(holder, capability, value);
                retained.remove(capability.id());
            }
            catch (RuntimeException | LinkageError | StackOverflowError failure)
            {
                log("LOAD", "decode-failed", holder, capability, failure);
            }
        }
        retain(holder, retained);
    }

    private void retain(Object holder, Map<String, byte[]> retained)
    {
        synchronized (lock)
        {
            expunge();
            HolderValues values = holders.get(new IdentityKey(canonical(holder)));
            if (values == null)
            {
                if (retained.isEmpty())
                {
                    return;
                }
                IdentityKey key = new IdentityKey(canonical(holder), collected);
                values = new HolderValues(key);
                holders.put(key, values);
            }
            values.retained.clear();
            for (Map.Entry<String, byte[]> entry : retained.entrySet())
            {
                if (entry.getKey() != null && entry.getValue() != null
                        && entry.getValue().length > 0)
                {
                    values.retained.put(entry.getKey(), entry.getValue().clone());
                }
            }
            if (values.isEmpty())
            {
                holders.remove(values.key);
            }
        }
    }

    private Map<String, byte[]> retained(Object holder)
    {
        synchronized (lock)
        {
            expunge();
            HolderValues values = holders.get(new IdentityKey(canonical(holder)));
            return values == null || values.retained.isEmpty()
                    ? Map.of()
                    : new LinkedHashMap<>(values.retained);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void apply(Object holder, Capability<?> capability, Object value)
    {
        set(holder, (Capability) capability, value);
    }

    private static byte[] encode(Capability<?> capability, Object value)
    {
        Class<?> type = capability.valueType();
        if (type == null || !type.isInstance(value))
        {
            throw new CapabilityException("Value is not a valid " + capability.id()
                    + " value");
        }
        CapabilityCodec<?> codec = capability.codec();
        if (codec == null)
        {
            throw new CapabilityException("Capability " + capability.id()
                    + " has no codec");
        }
        byte[] encoded;
        try
        {
            encoded = encodeValue(codec, value);
        }
        catch (RuntimeException | LinkageError | StackOverflowError failure)
        {
            throw new CapabilityException("Could not encode capability "
                    + capability.id() + ": " + describe(failure), failure);
        }
        if (encoded == null || encoded.length <= 0
                || encoded.length > capability.maxEncodedBytes())
        {
            throw new CapabilityException("Encoded value for capability "
                    + capability.id() + " has an invalid size: "
                    + (encoded == null ? "null" : encoded.length + " bytes"));
        }
        return encoded;
    }

    private static Object decode(Capability<?> capability, byte[] encoded)
    {
        CapabilityCodec<?> codec = capability.codec();
        if (codec == null)
        {
            throw new CapabilityException("Capability " + capability.id()
                    + " has no codec");
        }
        Object decoded;
        try
        {
            decoded = decodeValue(codec, encoded);
        }
        catch (RuntimeException | LinkageError | StackOverflowError failure)
        {
            throw new CapabilityException("Could not decode capability "
                    + capability.id() + ": " + describe(failure), failure);
        }
        Class<?> type = capability.valueType();
        if (decoded == null || type == null || !type.isInstance(decoded))
        {
            throw new CapabilityException("Decoded payload for capability "
                    + capability.id() + " is not a declared value");
        }
        return decoded;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static byte[] encodeValue(CapabilityCodec<?> codec, Object value)
    {
        return ((CapabilityCodec) codec).encode(value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object decodeValue(CapabilityCodec<?> codec, byte[] encoded)
    {
        return ((CapabilityCodec) codec).decode(encoded);
    }

    private void log(String phase, String reason, Object holder,
            Capability<?> capability, Object detail)
    {
        logId(phase, reason, holder, capability == null ? null : capability.id(), detail);
    }

    private void logId(String phase, String reason, Object holder,
            String capabilityId, Object detail)
    {
        String holderName = holder == null ? "null" : holder.getClass().getName();
        String capabilityName = capabilityId == null ? "none" : capabilityId;
        String suffix = detail instanceof Throwable failure
                ? describe(failure)
                : String.valueOf(detail);
        CapabilityLog.log("[weaver] CAPABILITY " + phase + " SKIPPED holder="
                + holderName + " kind=" + holderKind + " capability=" + capabilityName
                + " reason=" + reason + " detail=" + suffix);
    }

    private static String describe(Throwable failure)
    {
        String message = failure.getMessage();
        return message != null ? message : failure.getClass().getName();
    }

    private void expunge()
    {
        Object reference;
        while ((reference = collected.poll()) != null)
        {
            final Object collectedKey = reference;
            holders.entrySet().removeIf(entry -> entry.getKey() == collectedKey);
        }
    }

    private static <T> T cast(Capability<T> capability, Object value)
    {
        if (value == null)
        {
            return null;
        }
        Class<T> type = capability.valueType();
        if (type == null || !type.isInstance(value))
        {
            throw new CapabilityException("Stored value is not a valid "
                    + capability.id() + " value");
        }
        return type.cast(value);
    }

    private static boolean isAssignableTo(Class<?> type, String className)
    {
        Class<?> current = type;
        while (current != null)
        {
            if (current.getName().equals(className))
            {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private static final class HolderValues
    {
        private final IdentityKey key;
        private final Map<Capability<?>, Object> values = new HashMap<>();
        /** Raw stored bytes for ids without a live value this session. */
        private final Map<String, byte[]> retained = new LinkedHashMap<>();

        private HolderValues(IdentityKey key)
        {
            this.key = key;
        }

        private boolean isEmpty()
        {
            return values.isEmpty() && retained.isEmpty();
        }
    }

    private static final class IdentityKey extends WeakReference<Object>
    {
        private final int hash;

        private IdentityKey(Object holder)
        {
            super(holder);
            this.hash = System.identityHashCode(holder);
        }

        private IdentityKey(Object holder, ReferenceQueue<Object> queue)
        {
            super(holder, queue);
            this.hash = System.identityHashCode(holder);
        }

        @Override
        public int hashCode()
        {
            return hash;
        }

        @Override
        public boolean equals(Object other)
        {
            if (this == other)
            {
                return true;
            }
            if (!(other instanceof IdentityKey key))
            {
                return false;
            }
            Object holder = get();
            return holder != null && holder == key.get();
        }
    }
}
