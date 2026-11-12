package net.weaver.api.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/**
 * A thread-safe registry of payload channels.
 *
 * <p>Each combination of {@link PayloadDirection}, {@link NetworkPhase}, and
 * channel id may be registered once. The registry retains registration order,
 * which makes handshake channel lists and diagnostic dumps deterministic. A
 * registry does not encode, decode, send, or receive anything by itself; the
 * transport consults it when building handshakes and dispatching payloads.</p>
 *
 * <p>{@link #global()} is the process-wide registry used by the Weaver
 * runtime. Construct a separate instance when an isolated registry is needed,
 * for example in a test or an embedded server.</p>
 */
public final class NetworkRegistry
{
    private static final NetworkRegistry GLOBAL = new NetworkRegistry();

    private final Map<RegistrationKey, PayloadRegistration<?>> registrations =
            new LinkedHashMap<>();

    /**
     * Creates an empty, isolated registry.
     */
    public NetworkRegistry()
    {
    }

    /**
     * The process-wide registry used by the Weaver runtime.
     *
     * @return the global registry, never {@code null}
     */
    public static NetworkRegistry global()
    {
        return GLOBAL;
    }

    /**
     * Registers a clientbound channel active during {@link NetworkPhase#PLAY}.
     *
     * @param modId   the owning mod id, which must equal {@code type.modId()}
     * @param type    the payload type, not {@code null}
     * @param handler the inbound handler, not {@code null}
     * @param <T>     the payload type
     * @return the new registration, never {@code null}
     * @throws NullPointerException         when an argument is {@code null}
     * @throws IllegalArgumentException     when {@code modId} does not match the
     *                                      type
     * @throws NetworkRegistrationException when the channel is already
     *                                      registered for this direction and
     *                                      phase
     */
    public synchronized <T> PayloadRegistration<T> registerClientbound(String modId,
                                                                       PayloadType<T> type,
                                                                       PayloadHandler<T> handler)
    {
        return registerClientbound(modId, type, NetworkPhase.PLAY, handler);
    }

    /**
     * Registers a clientbound channel active during one phase.
     *
     * @param modId   the owning mod id, which must equal {@code type.modId()}
     * @param type    the payload type, not {@code null}
     * @param phase   the active phase, not {@code null}
     * @param handler the inbound handler, not {@code null}
     * @param <T>     the payload type
     * @return the new registration, never {@code null}
     * @throws NullPointerException         when an argument is {@code null}
     * @throws IllegalArgumentException     when {@code modId} does not match the
     *                                      type
     * @throws NetworkRegistrationException when the channel is already
     *                                      registered for this direction and
     *                                      phase
     */
    public synchronized <T> PayloadRegistration<T> registerClientbound(String modId,
                                                                       PayloadType<T> type,
                                                                       NetworkPhase phase,
                                                                       PayloadHandler<T> handler)
    {
        return registerClientbound(modId, type, phase, false, handler);
    }

    /**
     * Registers a clientbound channel with explicit optional metadata.
     *
     * @param modId    the owning mod id, which must equal {@code type.modId()}
     * @param type     the payload type, not {@code null}
     * @param phase    the active phase, not {@code null}
     * @param optional whether the channel is optional for policy consumers
     * @param handler  the inbound handler, not {@code null}
     * @param <T>      the payload type
     * @return the new registration, never {@code null}
     * @throws NullPointerException         when an argument is {@code null}
     * @throws IllegalArgumentException     when {@code modId} does not match the
     *                                      type
     * @throws NetworkRegistrationException when the channel is already
     *                                      registered for this direction and
     *                                      phase
     */
    public synchronized <T> PayloadRegistration<T> registerClientbound(String modId,
                                                                       PayloadType<T> type,
                                                                       NetworkPhase phase,
                                                                       boolean optional,
                                                                       PayloadHandler<T> handler)
    {
        return register(PayloadDirection.CLIENTBOUND, modId, type, phase, optional, handler);
    }

    /**
     * Registers a serverbound channel active during {@link NetworkPhase#PLAY}.
     *
     * @param modId   the owning mod id, which must equal {@code type.modId()}
     * @param type    the payload type, not {@code null}
     * @param handler the inbound handler, not {@code null}
     * @param <T>     the payload type
     * @return the new registration, never {@code null}
     * @throws NullPointerException         when an argument is {@code null}
     * @throws IllegalArgumentException     when {@code modId} does not match the
     *                                      type
     * @throws NetworkRegistrationException when the channel is already
     *                                      registered for this direction and
     *                                      phase
     */
    public synchronized <T> PayloadRegistration<T> registerServerbound(String modId,
                                                                       PayloadType<T> type,
                                                                       PayloadHandler<T> handler)
    {
        return registerServerbound(modId, type, NetworkPhase.PLAY, handler);
    }

    /**
     * Registers a serverbound channel active during one phase.
     *
     * @param modId   the owning mod id, which must equal {@code type.modId()}
     * @param type    the payload type, not {@code null}
     * @param phase   the active phase, not {@code null}
     * @param handler the inbound handler, not {@code null}
     * @param <T>     the payload type
     * @return the new registration, never {@code null}
     * @throws NullPointerException         when an argument is {@code null}
     * @throws IllegalArgumentException     when {@code modId} does not match the
     *                                      type
     * @throws NetworkRegistrationException when the channel is already
     *                                      registered for this direction and
     *                                      phase
     */
    public synchronized <T> PayloadRegistration<T> registerServerbound(String modId,
                                                                       PayloadType<T> type,
                                                                       NetworkPhase phase,
                                                                       PayloadHandler<T> handler)
    {
        return registerServerbound(modId, type, phase, false, handler);
    }

    /**
     * Registers a serverbound channel with explicit optional metadata.
     *
     * @param modId    the owning mod id, which must equal {@code type.modId()}
     * @param type     the payload type, not {@code null}
     * @param phase    the active phase, not {@code null}
     * @param optional whether the channel is optional for policy consumers
     * @param handler  the inbound handler, not {@code null}
     * @param <T>      the payload type
     * @return the new registration, never {@code null}
     * @throws NullPointerException         when an argument is {@code null}
     * @throws IllegalArgumentException     when {@code modId} does not match the
     *                                      type
     * @throws NetworkRegistrationException when the channel is already
     *                                      registered for this direction and
     *                                      phase
     */
    public synchronized <T> PayloadRegistration<T> registerServerbound(String modId,
                                                                       PayloadType<T> type,
                                                                       NetworkPhase phase,
                                                                       boolean optional,
                                                                       PayloadHandler<T> handler)
    {
        return register(PayloadDirection.SERVERBOUND, modId, type, phase, optional, handler);
    }

    /**
     * Finds the registration for an exact direction, phase, and channel.
     *
     * @param direction the payload direction, not {@code null}
     * @param phase     the connection phase, not {@code null}
     * @param channel   the versioned channel id, not {@code null}
     * @return the matching registration, or an empty optional
     * @throws NullPointerException when an argument is {@code null}
     */
    public synchronized Optional<PayloadRegistration<?>> find(PayloadDirection direction,
                                                              NetworkPhase phase,
                                                              String channel)
    {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(channel, "channel");
        return Optional.ofNullable(registrations.get(
                new RegistrationKey(direction, phase, channel)));
    }

    /**
     * Finds the first registration for a direction and channel in any phase.
     *
     * <p>When the same channel is registered for several phases, the
     * registration that was added first is returned. Registration order is
     * deterministic and preserved by this registry.</p>
     *
     * @param direction the payload direction, not {@code null}
     * @param channel   the versioned channel id, not {@code null}
     * @return the first matching registration, or an empty optional
     * @throws NullPointerException when an argument is {@code null}
     */
    public synchronized Optional<PayloadRegistration<?>> findAnyPhase(PayloadDirection direction,
                                                                     String channel)
    {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(channel, "channel");
        for (PayloadRegistration<?> registration : registrations.values())
        {
            if (registration.direction() == direction
                    && registration.channel().equals(channel))
            {
                return Optional.of(registration);
            }
        }
        return Optional.empty();
    }

    /**
     * Every registration in registration order.
     *
     * @return an immutable snapshot, never {@code null}
     */
    public synchronized List<PayloadRegistration<?>> registrations()
    {
        return List.copyOf(registrations.values());
    }

    /**
     * Every registration for one direction, in registration order.
     *
     * @param direction the payload direction, not {@code null}
     * @return an immutable snapshot, never {@code null}
     * @throws NullPointerException when {@code direction} is {@code null}
     */
    public synchronized List<PayloadRegistration<?>> registrations(PayloadDirection direction)
    {
        Objects.requireNonNull(direction, "direction");
        List<PayloadRegistration<?>> matching = new ArrayList<>();
        for (PayloadRegistration<?> registration : registrations.values())
        {
            if (registration.direction() == direction)
            {
                matching.add(registration);
            }
        }
        return List.copyOf(matching);
    }

    /**
     * Every registered channel id, sorted and without duplicates.
     *
     * @return an immutable sorted list, never {@code null}
     */
    public synchronized List<String> channels()
    {
        TreeSet<String> channels = new TreeSet<>();
        for (PayloadRegistration<?> registration : registrations.values())
        {
            channels.add(registration.channel());
        }
        return List.copyOf(channels);
    }

    /**
     * Removes every registration from this registry.
     */
    public synchronized void clear()
    {
        registrations.clear();
    }

    /**
     * The number of registrations in this registry.
     *
     * @return the registration count
     */
    public synchronized int size()
    {
        return registrations.size();
    }

    private <T> PayloadRegistration<T> register(PayloadDirection direction, String modId,
                                                PayloadType<T> type, NetworkPhase phase,
                                                boolean optional, PayloadHandler<T> handler)
    {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(handler, "handler");
        if (!modId.equals(type.modId()))
        {
            throw new IllegalArgumentException("modId '" + modId
                    + "' does not match payload type owner '" + type.modId() + "'");
        }
        RegistrationKey key = new RegistrationKey(direction, phase, type.channel());
        PayloadRegistration<?> existing = registrations.get(key);
        if (existing != null)
        {
            throw new NetworkRegistrationException("Duplicate network registration "
                    + key.display() + ": existing registration " + existing
                    + " owned by mod '" + existing.modId()
                    + "', new registration owned by mod '" + modId + "'");
        }
        PayloadRegistration<T> registration = new PayloadRegistration<>(
                type, direction, phase, optional, handler);
        registrations.put(key, registration);
        return registration;
    }

    private record RegistrationKey(PayloadDirection direction, NetworkPhase phase,
                                   String channel)
    {
        private String display()
        {
            return direction.name() + "/" + phase.name() + "/" + channel;
        }
    }
}
