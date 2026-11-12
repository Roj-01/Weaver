package net.weaver.api.network;

import java.util.Objects;

/**
 * The immutable identity of one versioned payload channel.
 *
 * <p>A type combines the owning mod id, a path, a positive channel version, and
 * the codec used on the wire. Its {@link #channel()} is the stable wire id in
 * the form {@code <modId>:<path>/v<version>}, for example
 * {@code example:telemetry/v1}. Two types are equal exactly when their channel
 * ids are equal; the codec does not participate in equality.</p>
 *
 * @param <T> the payload type
 */
public final class PayloadType<T>
{
    private final String modId;
    private final String path;
    private final int version;
    private final String channel;
    private final PayloadCodec<T> codec;

    private PayloadType(String modId, String path, int version, String channel,
                        PayloadCodec<T> codec)
    {
        this.modId = modId;
        this.path = path;
        this.version = version;
        this.channel = channel;
        this.codec = codec;
    }

    /**
     * Creates a type with the default channel version
     * {@link NetworkProtocol#CHANNEL_VERSION}.
     *
     * @param modId the owning mod id matching {@code [a-z0-9_.-]+}, not
     *              {@code null}
     * @param path  the channel path matching {@code [a-z0-9_./-]+} without a
     *              leading or trailing slash, not {@code null}
     * @param codec the wire codec, not {@code null}
     * @param <T>   the payload type
     * @return a new payload type, never {@code null}
     * @throws NullPointerException     when an argument is {@code null}
     * @throws IllegalArgumentException when the id, path, or resulting channel
     *                                  is invalid
     */
    public static <T> PayloadType<T> of(String modId, String path, PayloadCodec<T> codec)
    {
        return of(modId, path, NetworkProtocol.CHANNEL_VERSION, codec);
    }

    /**
     * Creates a type with an explicit channel version.
     *
     * @param modId   the owning mod id matching {@code [a-z0-9_.-]+}, not
     *                {@code null}
     * @param path    the channel path matching {@code [a-z0-9_./-]+} without a
     *                leading or trailing slash, not {@code null}
     * @param version the positive channel version
     * @param codec   the wire codec, not {@code null}
     * @param <T>     the payload type
     * @return a new payload type, never {@code null}
     * @throws NullPointerException     when an argument is {@code null}
     * @throws IllegalArgumentException when the id, path, version, or resulting
     *                                  channel is invalid
     */
    public static <T> PayloadType<T> of(String modId, String path, int version,
                                        PayloadCodec<T> codec)
    {
        NetworkValidation.requireModId(modId, "modId");
        NetworkValidation.requirePath(path);
        if (version < 1)
        {
            throw new IllegalArgumentException("version must be >= 1 but was " + version);
        }
        Objects.requireNonNull(codec, "codec");
        String channel = modId + ":" + path + "/v" + version;
        if (channel.length() > NetworkProtocol.MAX_CHANNEL_CHARS)
        {
            throw new IllegalArgumentException("channel id must be at most "
                    + NetworkProtocol.MAX_CHANNEL_CHARS + " chars but was "
                    + channel.length() + ": " + channel);
        }
        return new PayloadType<>(modId, path, version, channel, codec);
    }

    /**
     * The id of the mod that owns this channel.
     *
     * @return the mod id, never {@code null}
     */
    public String modId()
    {
        return modId;
    }

    /**
     * The namespace of this channel, which is always {@link #modId()}.
     *
     * @return the mod id, never {@code null}
     */
    public String namespace()
    {
        return modId;
    }

    /**
     * The path portion of the channel id.
     *
     * @return the path, never {@code null}
     */
    public String path()
    {
        return path;
    }

    /**
     * The positive channel version.
     *
     * @return the version, always at least {@code 1}
     */
    public int version()
    {
        return version;
    }

    /**
     * The complete wire channel id in the form
     * {@code <modId>:<path>/v<version>}.
     *
     * @return the channel id, never {@code null}
     */
    public String channel()
    {
        return channel;
    }

    /**
     * The codec used for this channel.
     *
     * @return the codec, never {@code null}
     */
    public PayloadCodec<T> codec()
    {
        return codec;
    }

    /**
     * Compares payload types by channel id.
     *
     * @param other the object to compare with
     * @return {@code true} when the other object is a payload type with the
     *         same channel id
     */
    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (!(other instanceof PayloadType<?> type))
        {
            return false;
        }
        return channel.equals(type.channel);
    }

    /**
     * Returns the channel id's hash code.
     *
     * @return the hash code
     */
    @Override
    public int hashCode()
    {
        return channel.hashCode();
    }

    /**
     * Returns the channel id.
     *
     * @return the channel id, never {@code null}
     */
    @Override
    public String toString()
    {
        return channel;
    }
}
