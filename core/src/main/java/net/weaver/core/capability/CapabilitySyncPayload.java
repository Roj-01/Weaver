package net.weaver.core.capability;

import java.util.Objects;
import net.weaver.api.capability.CapabilityId;
import net.weaver.api.network.NetworkProtocol;

/**
 * One clientbound capability snapshot.
 *
 * <p>A payload identifies the holder by kind and key, the capability by id, and
 * carries the encoded value. An empty {@link #encoded()} array is a removal:
 * the receiving side drops the explicit value instead of decoding it.</p>
 *
 * @param holderKind   the provider-defined holder kind, never {@code null}
 * @param holderKey    the provider-defined holder key, never {@code null}
 * @param capabilityId the capability id in {@code namespace:path} form
 * @param encoded      the encoded value, or an empty array for a removal
 */
public record CapabilitySyncPayload(String holderKind, String holderKey,
        String capabilityId, byte[] encoded)
{
    /** Maximum holder-kind length in characters. */
    public static final int MAX_KIND_CHARS = 128;

    /** Maximum holder-key length in characters. */
    public static final int MAX_KEY_CHARS = 256;

    /** Maximum capability-id length in characters. */
    public static final int MAX_ID_CHARS = 128;

    /**
     * Validates and defensively copies the payload.
     *
     * @throws NullPointerException     when a text component or {@code encoded}
     *                                  is {@code null}
     * @throws IllegalArgumentException when a text component is blank or too
     *                                  long, the id is invalid, or the encoded
     *                                  value exceeds the network payload limit
     */
    public CapabilitySyncPayload
    {
        holderKind = requireText(holderKind, "holderKind", MAX_KIND_CHARS);
        holderKey = requireText(holderKey, "holderKey", MAX_KEY_CHARS);
        CapabilityId parsed = CapabilityId.parse(
                Objects.requireNonNull(capabilityId, "capabilityId"));
        capabilityId = parsed.id();
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length > NetworkProtocol.MAX_PAYLOAD_BYTES)
        {
            throw new IllegalArgumentException("encoded value of " + encoded.length
                    + " bytes exceeds the maximum payload of "
                    + NetworkProtocol.MAX_PAYLOAD_BYTES);
        }
        encoded = encoded.clone();
    }

    /**
     * Returns a defensive copy of the encoded value.
     *
     * @return the encoded bytes, never {@code null}
     */
    @Override
    public byte[] encoded()
    {
        return encoded.clone();
    }

    /**
     * Returns whether this payload removes the value instead of setting it.
     *
     * @return {@code true} when the encoded array is empty
     */
    public boolean removes()
    {
        return encoded.length == 0;
    }

    private static String requireText(String value, String name, int maxChars)
    {
        Objects.requireNonNull(value, name);
        if (value.isBlank())
        {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (value.length() > maxChars)
        {
            throw new IllegalArgumentException(name + " must be at most " + maxChars
                    + " chars but was " + value.length());
        }
        return value;
    }
}
