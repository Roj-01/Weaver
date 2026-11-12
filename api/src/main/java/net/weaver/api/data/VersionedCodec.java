package net.weaver.api.data;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import net.weaver.api.capability.CapabilityCodec;
import net.weaver.api.capability.CapabilityException;

/**
 * A codec that records which version of a value's format it wrote, so a mod can
 * change its format and still read old saves: old data is read by the reader
 * for its version and becomes a current value, which is written in the current
 * format the next time the holder is saved.
 *
 * <pre>{@code
 * CapabilityCodec<Energy> codec = VersionedCodec.builder(2, Energy.CODEC_V2)
 *         .reader(0, bytes -> Energy.fromOldInt(bytes))   // data saved before versioning
 *         .reader(1, Energy::readV1)
 *         .build();
 * }</pre>
 *
 * <p>Encoded data is a 4-byte marker, the version (one byte, 0 to 255), then the
 * current codec's bytes: allow {@link #OVERHEAD} more bytes in the capability's
 * size limit. Data with no marker (written before the capability used a
 * versioned codec) is version 0. Data from a newer version than the current one
 * (a world opened with an older mod) is rejected with a clear reason instead of
 * being misread.</p>
 */
public final class VersionedCodec<T> implements CapabilityCodec<T> {
    /** The bytes a versioned payload adds to the current codec's payload. */
    public static final int OVERHEAD = 5;

    private static final byte[] MARKER = {(byte) 0xF7, 'W', 'V', (byte) 0xC1};

    private final int version;
    private final CapabilityCodec<T> current;
    private final Map<Integer, Function<byte[], T>> readers;

    private VersionedCodec(int version, CapabilityCodec<T> current, Map<Integer, Function<byte[], T>> readers) {
        this.version = version;
        this.current = current;
        this.readers = readers;
    }

    /**
     * Starts a versioned codec.
     *
     * @param version the current format version, 1 to 255
     * @param current the codec of the current format
     */
    public static <T> Builder<T> builder(int version, CapabilityCodec<T> current) {
        if (version < 1 || version > 255) {
            throw new IllegalArgumentException("version must be 1..255 but was " + version);
        }
        return new Builder<>(version, Objects.requireNonNull(current, "current"));
    }

    /** The current format version. */
    public int version() {
        return version;
    }

    @Override
    public byte[] encode(T value) {
        byte[] payload = current.encode(value);
        byte[] out = new byte[OVERHEAD + payload.length];
        System.arraycopy(MARKER, 0, out, 0, MARKER.length);
        out[MARKER.length] = (byte) version;
        System.arraycopy(payload, 0, out, OVERHEAD, payload.length);
        return out;
    }

    @Override
    public T decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        int found;
        byte[] payload;
        if (encoded.length >= OVERHEAD && Arrays.equals(encoded, 0, MARKER.length, MARKER, 0, MARKER.length)) {
            found = encoded[MARKER.length] & 0xFF;
            payload = Arrays.copyOfRange(encoded, OVERHEAD, encoded.length);
        } else {
            found = 0;
            payload = encoded;
        }
        if (found == version) {
            return current.decode(payload);
        }
        if (found > version) {
            throw new CapabilityException("data was written by format version " + found
                    + ", newer than this mod's version " + version);
        }
        Function<byte[], T> reader = readers.get(found);
        if (reader == null) {
            throw new CapabilityException("no reader for format version " + found + " (current " + version + ")");
        }
        T value = reader.apply(payload);
        if (value == null) {
            throw new CapabilityException("the reader for format version " + found + " returned null");
        }
        return value;
    }

    /** Builds a {@link VersionedCodec}. */
    public static final class Builder<T> {
        private final int version;
        private final CapabilityCodec<T> current;
        private final Map<Integer, Function<byte[], T>> readers = new TreeMap<>();

        private Builder(int version, CapabilityCodec<T> current) {
            this.version = version;
            this.current = current;
        }

        /**
         * Adds the reader for an older format. Version 0 is data written before the
         * capability used a versioned codec.
         */
        public Builder<T> reader(int oldVersion, Function<byte[], T> reader) {
            if (oldVersion < 0 || oldVersion >= version) {
                throw new IllegalArgumentException("an old version must be 0.." + (version - 1) + " but was " + oldVersion);
            }
            readers.put(oldVersion, Objects.requireNonNull(reader, "reader"));
            return this;
        }

        /** The codec. */
        public VersionedCodec<T> build() {
            return new VersionedCodec<>(version, current, Map.copyOf(readers));
        }
    }
}
