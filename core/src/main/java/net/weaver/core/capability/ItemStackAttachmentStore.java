package net.weaver.core.capability;

import java.util.Objects;
import net.weaver.api.capability.AttachmentStore;
import net.weaver.api.capability.Capability;
import net.weaver.api.capability.CapabilityCodec;
import net.weaver.api.capability.CapabilityException;
import net.weaver.core.data.ItemDataAccess;
import net.weaver.core.data.ReflectiveItemDataAccess;

/**
 * {@link AttachmentStore} that persists capability values on a game item stack.
 *
 * <p>The store only accepts non-empty item stacks and capabilities that declare
 * a codec. Values are encoded through {@link Capability#codec()} and written
 * through an {@link ItemDataAccess}, which keeps them inside the
 * {@code weaver:capabilities} root of {@code minecraft:custom_data}. Runtime
 * holders and runtime-only capabilities are left to the built-in runtime store
 * that the registry always consults last.</p>
 *
 * <p>Decoding is strict: a stored payload that cannot be decoded or whose
 * decoded value does not match {@link Capability#valueType()} raises a
 * {@link CapabilityException}. Previous values returned by
 * {@link #set(Object, Capability, Object)} and
 * {@link #remove(Object, Capability)} fail soft instead, so a corrupt old
 * payload never blocks a new write or a removal.</p>
 */
public final class ItemStackAttachmentStore implements AttachmentStore
{
    private final ItemDataAccess access;

    /**
     * Creates a store backed by the reflective game bridge.
     */
    public ItemStackAttachmentStore()
    {
        this(new ReflectiveItemDataAccess());
    }

    /**
     * Creates a store backed by the supplied item data access.
     *
     * @param access the non-null item data access
     */
    public ItemStackAttachmentStore(ItemDataAccess access)
    {
        this.access = Objects.requireNonNull(access, "access");
    }

    @Override
    public int priority()
    {
        return 100;
    }

    @Override
    public boolean supports(Object holder, Capability<?> capability)
    {
        return access.supports(holder)
                && capability != null
                && capability.codec() != null
                && !access.isEmpty(holder);
    }

    @Override
    public <T> T get(Object holder, Capability<T> capability)
    {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(capability, "capability");
        byte[] encoded = access.read(holder, capability.id());
        return encoded == null ? null : decode(capability, encoded);
    }

    @Override
    public <T> T set(Object holder, Capability<T> capability, T value)
    {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(value, "value");
        byte[] encoded = encode(capability, value);
        byte[] previous = null;
        try
        {
            previous = access.read(holder, capability.id());
        }
        catch (RuntimeException ignored)
        {
            // A broken old payload must not block the new write.
        }
        access.write(holder, capability.id(), encoded);
        return decodeOrNull(capability, previous);
    }

    @Override
    public <T> T remove(Object holder, Capability<T> capability)
    {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(capability, "capability");
        return decodeOrNull(capability, access.remove(holder, capability.id()));
    }

    @Override
    public boolean has(Object holder, Capability<?> capability)
    {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(capability, "capability");
        return access.contains(holder, capability.id());
    }

    @Override
    public void clear(Object holder)
    {
        if (!access.supports(holder))
        {
            return;
        }
        access.clear(holder);
    }

    /**
     * Returns the backing item data access.
     *
     * @return the item data access used by this store
     */
    public ItemDataAccess access()
    {
        return access;
    }

    private static <T> byte[] encode(Capability<T> capability, T value)
    {
        CapabilityCodec<T> codec = requireCodec(capability);
        byte[] encoded;
        try
        {
            encoded = codec.encode(value);
        }
        catch (RuntimeException failure)
        {
            throw new CapabilityException("Could not encode capability "
                    + capability.id() + " value: " + describe(failure), failure);
        }
        if (encoded == null)
        {
            throw new CapabilityException("Codec for capability " + capability.id()
                    + " returned null");
        }
        int max = capability.maxEncodedBytes();
        if (encoded.length <= 0 || encoded.length > max)
        {
            throw new CapabilityException("Encoded value for capability "
                    + capability.id() + " is " + encoded.length
                    + " bytes; expected between 1 and " + max);
        }
        return encoded;
    }

    private static <T> T decode(Capability<T> capability, byte[] encoded)
    {
        CapabilityCodec<T> codec = requireCodec(capability);
        T decoded;
        try
        {
            decoded = codec.decode(encoded);
        }
        catch (RuntimeException failure)
        {
            throw new CapabilityException("Could not decode capability "
                    + capability.id() + " payload: " + describe(failure), failure);
        }
        Class<T> type = capability.valueType();
        if (decoded == null || type == null || !type.isInstance(decoded))
        {
            throw new CapabilityException("Decoded payload for capability "
                    + capability.id() + " is not a declared value");
        }
        return decoded;
    }

    private static <T> T decodeOrNull(Capability<T> capability, byte[] encoded)
    {
        if (encoded == null)
        {
            return null;
        }
        try
        {
            return decode(capability, encoded);
        }
        catch (RuntimeException ignored)
        {
            return null;
        }
    }

    private static <T> CapabilityCodec<T> requireCodec(Capability<T> capability)
    {
        CapabilityCodec<T> codec = capability.codec();
        if (codec == null)
        {
            throw new CapabilityException("Capability " + capability.id()
                    + " has no codec and cannot be stored on an item stack");
        }
        return codec;
    }

    private static String describe(Throwable failure)
    {
        if (failure == null)
        {
            return "unknown cause";
        }
        String message = failure.getMessage();
        return message != null ? message : failure.getClass().getName();
    }
}
