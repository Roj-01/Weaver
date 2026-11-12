package net.weaver.core.capability;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.weaver.api.capability.AttachmentChange;
import net.weaver.api.capability.AttachmentChangeListener;
import net.weaver.api.capability.AttachmentRegistry;
import net.weaver.api.capability.Capability;
import net.weaver.api.capability.CapabilityCodec;
import net.weaver.api.capability.CapabilityException;
import net.weaver.api.capability.CapabilitySync;
import net.weaver.api.capability.CapabilitySyncProvider;
import net.weaver.api.network.NetworkConnection;
import net.weaver.api.network.NetworkException;
import net.weaver.api.network.NetworkPhase;
import net.weaver.api.network.NetworkRegistry;
import net.weaver.api.network.PayloadBuffer;
import net.weaver.api.network.PayloadCodec;
import net.weaver.api.network.PayloadContext;
import net.weaver.api.network.PayloadDirection;
import net.weaver.api.network.PayloadType;

/**
 * Clientbound capability synchronization channel.
 *
 * <p>The channel defines the versioned {@code weaver:capability/sync/v1}
 * payload and registers a clientbound {@code PLAY} handler on
 * {@link NetworkRegistry#global()}. It does not create or replace the network
 * bridge; it reuses the bridge's {@link NetworkConnection} and payload types.</p>
 *
 * <p>Sending is opt-in. An explicit {@link #send} transmits any capability
 * that does not declare {@link CapabilitySync#NONE} when its holder resolves
 * through the installed {@link CapabilitySyncProvider} and the capability has a
 * codec. Automatic change forwarding covers {@link CapabilitySync#RUNTIME}
 * only: the registry's {@link AttachmentChangeListener} forwards the change to
 * the recipients returned by {@link #setRecipientsSupplier(Supplier)}. That
 * supplier is the integration point for the loader or game layer, which owns
 * the per-player session list. With no supplier installed, the channel sends
 * nothing.</p>
 *
 * <p>The inbound handler validates the capability id, codec, value type, and
 * size. Unknown ids, missing local holders, and malformed values are logged and
 * skipped; they never disconnect the peer and never replace another holder's
 * value.</p>
 */
public final class CapabilitySyncChannel
{
    /** The owning mod id used in the payload channel. */
    public static final String MOD_ID = "weaver";

    /** The channel path used in the payload channel. */
    public static final String PATH = "capability/sync";

    /** The channel version used in the payload channel. */
    public static final int VERSION = 1;

    private static final PayloadCodec<CapabilitySyncPayload> CODEC =
            new CapabilitySyncPayloadCodec();

    /** The registered payload type, {@code weaver:capability/sync/v1}. */
    public static final PayloadType<CapabilitySyncPayload> TYPE =
            PayloadType.of(MOD_ID, PATH, VERSION, CODEC);

    private static final Object LOCK = new Object();

    private static volatile AttachmentRegistry registry;
    private static volatile CapabilitySyncProvider provider;
    /** Set while a received value is applied, so the change is not sent on again. */
    private static final ThreadLocal<Boolean> APPLYING = new ThreadLocal<>();
    private static volatile Supplier<List<NetworkConnection>> recipientsSupplier = List::of;
    private static volatile AttachmentChangeListener changeListener;

    private CapabilitySyncChannel()
    {
    }

    /**
     * Installs the channel on a registry and installs the sync provider.
     *
     * <p>Reinstalling replaces the registry and provider and reuses one change
     * listener. The network registration is created once per process; a second
     * install reuses the existing registration.</p>
     *
     * @param registry the registry whose changes are forwarded
     * @param provider the non-null holder identity provider
     * @throws NullPointerException when either argument is {@code null}
     */
    public static void install(AttachmentRegistry registry, CapabilitySyncProvider provider)
    {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(provider, "provider");
        synchronized (LOCK)
        {
            AttachmentRegistry previous = CapabilitySyncChannel.registry;
            if (previous != null && changeListener != null)
            {
                previous.removeChangeListener(changeListener);
            }
            CapabilitySyncChannel.registry = registry;
            CapabilitySyncChannel.provider = provider;
            if (changeListener == null)
            {
                changeListener = CapabilitySyncChannel::onAttachmentChanged;
            }
            registry.addChangeListener(changeListener);
            registerNetwork();
        }
    }

    /**
     * Removes the change listener and clears the installed registry and
     * provider.
     *
     * <p>The network registration is left in place because the network API has
     * no unregister operation. A later {@link #install} reuses it.</p>
     */
    public static void uninstall()
    {
        synchronized (LOCK)
        {
            if (registry != null && changeListener != null)
            {
                registry.removeChangeListener(changeListener);
            }
            registry = null;
            provider = null;
            recipientsSupplier = List::of;
        }
    }

    /**
     * Installs the recipient supplier used for change notifications.
     *
     * <p>The loader or game layer owns the live connection list and must call
     * this after it knows the sessions. The supplier should return a stable
     * snapshot; it is evaluated once per change notification. Passing
     * {@code null} resets the supplier to an empty list, so no change is
     * sent.</p>
     *
     * @param supplier the recipient supplier, or {@code null} for none
     */
    public static void setRecipientsSupplier(
            Supplier<List<NetworkConnection>> supplier)
    {
        recipientsSupplier = supplier == null ? List::of : supplier;
    }

    /**
     * Returns the installed sync provider.
     *
     * @return the provider, or {@code null} when the channel is not installed
     */
    public static CapabilitySyncProvider provider()
    {
        return provider;
    }

    /**
     * Returns whether the channel has an installed registry and provider.
     *
     * @return {@code true} when installed
     */
    public static boolean isInstalled()
    {
        return registry != null && provider != null;
    }

    /**
     * Sends one holder value to one connection.
     *
     * <p>A {@code null} value sends a removal payload. The method returns
     * {@code false} without sending when the connection or holder is missing,
     * the channel is not installed, the capability has no codec, the provider
     * cannot identify the holder, or the capability declares
     * {@link CapabilitySync#NONE}.</p>
     *
     * @param connection the destination connection
     * @param holder     the holder whose value is sent
     * @param capability the capability being sent
     * @param value      the value to encode, or {@code null} to remove it
     * @return {@code true} when the payload was handed to the connection
     */
    public static boolean send(NetworkConnection connection, Object holder,
            Capability<?> capability, Object value)
    {
        if (connection == null || holder == null || capability == null)
        {
            return false;
        }
        CapabilitySyncProvider current = provider;
        if (current == null)
        {
            return false;
        }
        if (capability.sync() == CapabilitySync.NONE)
        {
            return false;
        }
        if (capability.codec() == null)
        {
            return false;
        }
        String kind;
        String key;
        try
        {
            kind = current.holderKind(holder);
            key = current.holderKey(holder);
        }
        catch (RuntimeException failure)
        {
            log("SEND", "provider-failed", capability.id(), failure);
            return false;
        }
        if (kind == null || key == null)
        {
            return false;
        }
        byte[] encoded;
        try
        {
            encoded = encode(capability, value);
        }
        catch (RuntimeException failure)
        {
            log("SEND", "encode-failed", capability.id(), failure);
            return false;
        }
        CapabilitySyncPayload payload;
        try
        {
            payload = new CapabilitySyncPayload(kind, key, capability.id(), encoded);
        }
        catch (RuntimeException failure)
        {
            log("SEND", "payload-invalid", capability.id(), failure);
            return false;
        }
        return sendPayload(connection, payload);
    }

    static void handle(CapabilitySyncPayload payload, PayloadContext context)
    {
        if (payload == null)
        {
            return;
        }
        AttachmentRegistry currentRegistry = registry;
        CapabilitySyncProvider currentProvider = provider;
        if (currentRegistry == null)
        {
            return;
        }
        Optional<Capability<?>> found = currentRegistry.find(payload.capabilityId());
        if (found.isEmpty())
        {
            return;
        }
        Capability<?> capability = found.get();
        if (capability.sync() == CapabilitySync.NONE || capability.codec() == null)
        {
            return;
        }
        Object holder;
        try
        {
            holder = currentProvider == null ? null
                    : currentProvider.resolve(payload.holderKind(), payload.holderKey());
        }
        catch (RuntimeException failure)
        {
            log("RECEIVE", "resolve-failed", capability.id(), failure);
            return;
        }
        if (holder == null)
        {
            log("RECEIVE", "holder-unavailable", capability.id(), payload.holderKey());
            return;
        }
        try
        {
            if (payload.removes())
            {
                currentRegistry.remove(holder, capability);
                return;
            }
            byte[] encoded = payload.encoded();
            if (encoded.length > capability.maxEncodedBytes())
            {
                log("RECEIVE", "oversize", capability.id(),
                        encoded.length + ">" + capability.maxEncodedBytes());
                return;
            }
            Object value = decode(capability, encoded);
            APPLYING.set(Boolean.TRUE);
            try
            {
                set(currentRegistry, holder, capability, value);
            }
            finally
            {
                APPLYING.remove();
            }
        }
        catch (RuntimeException failure)
        {
            log("RECEIVE", "apply-failed", capability.id(), failure);
        }
    }

    private static void registerNetwork()
    {
        try
        {
            NetworkRegistry network = NetworkRegistry.global();
            boolean present = network.find(PayloadDirection.CLIENTBOUND,
                    NetworkPhase.PLAY, TYPE.channel()).isPresent();
            if (!present)
            {
                network.registerClientbound(MOD_ID, TYPE, NetworkPhase.PLAY, true,
                        CapabilitySyncChannel::handle);
            }
        }
        catch (RuntimeException failure)
        {
            CapabilityLog.log("[weaver] CAPABILITY SYNC REGISTRATION FAILED channel="
                    + TYPE.channel() + " reason=" + describe(failure));
        }
    }

    private static void onAttachmentChanged(AttachmentChange change)
    {
        Capability<?> capability = change.capability();
        if (change.kind() == AttachmentChange.Kind.CLEAR || capability == null)
        {
            return;
        }
        if (capability.sync() != CapabilitySync.RUNTIME)
        {
            return;
        }
        if (capability.codec() == null || APPLYING.get() != null)
        {
            return;
        }
        CapabilitySyncProvider current = provider;
        try
        {
            if (current != null && current.isRemoteCopy(change.holder()))
            {
                return;
            }
        }
        catch (RuntimeException failure)
        {
            log("SEND", "remote-check-failed", capability.id(), failure);
            return;
        }
        List<NetworkConnection> recipients;
        try
        {
            recipients = recipientsSupplier.get();
        }
        catch (RuntimeException failure)
        {
            log("SEND", "recipient-lookup-failed", capability.id(), failure);
            return;
        }
        if (recipients == null || recipients.isEmpty())
        {
            return;
        }
        for (NetworkConnection connection : recipients)
        {
            if (connection == null || !connection.isOpen())
            {
                continue;
            }
            try
            {
                if (current != null && !current.shouldReceive(change.holder(), connection))
                {
                    continue;
                }
            }
            catch (RuntimeException failure)
            {
                // Fall back to sending: a missing update is worse than an extra one.
            }
            send(connection, change.holder(), capability, change.newValue());
        }
    }

    private static boolean sendPayload(NetworkConnection connection,
            CapabilitySyncPayload payload)
    {
        try
        {
            connection.send(TYPE, payload);
            return true;
        }
        catch (NetworkException failure)
        {
            log("SEND", "network-failed", payload.capabilityId(), failure);
            return false;
        }
        catch (RuntimeException failure)
        {
            log("SEND", "send-failed", payload.capabilityId(), failure);
            return false;
        }
    }

    private static byte[] encode(Capability<?> capability, Object value)
    {
        if (value == null)
        {
            return new byte[0];
        }
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
        catch (RuntimeException failure)
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
        catch (RuntimeException failure)
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

    @SuppressWarnings("unchecked")
    private static <T> void set(AttachmentRegistry registry, Object holder,
            Capability<?> capability, Object value)
    {
        registry.set(holder, (Capability<T>) capability, (T) value);
    }

    private static void log(String phase, String reason, String capabilityId,
            Object detail)
    {
        String suffix = detail instanceof Throwable failure
                ? describe(failure)
                : String.valueOf(detail);
        CapabilityLog.log("[weaver] CAPABILITY SYNC " + phase + " SKIPPED channel="
                + TYPE.channel() + " capability=" + capabilityId + " reason=" + reason
                + " detail=" + suffix);
    }

    private static String describe(Throwable failure)
    {
        String message = failure.getMessage();
        return message != null ? message : failure.getClass().getName();
    }

    private static final class CapabilitySyncPayloadCodec
            implements PayloadCodec<CapabilitySyncPayload>
    {
        @Override
        public void encode(CapabilitySyncPayload value, PayloadBuffer out)
        {
            out.writeString(value.holderKind(), CapabilitySyncPayload.MAX_KIND_CHARS);
            out.writeString(value.holderKey(), CapabilitySyncPayload.MAX_KEY_CHARS);
            out.writeString(value.capabilityId(), CapabilitySyncPayload.MAX_ID_CHARS);
            out.writeByteArray(value.encoded());
        }

        @Override
        public CapabilitySyncPayload decode(PayloadBuffer in)
        {
            String holderKind = in.readString(CapabilitySyncPayload.MAX_KIND_CHARS);
            String holderKey = in.readString(CapabilitySyncPayload.MAX_KEY_CHARS);
            String capabilityId = in.readString(CapabilitySyncPayload.MAX_ID_CHARS);
            byte[] encoded = in.readByteArray();
            return new CapabilitySyncPayload(holderKind, holderKey, capabilityId,
                    encoded);
        }
    }
}
