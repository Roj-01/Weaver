package net.weaver.api.config;

import net.weaver.api.event.Side;
import net.weaver.api.network.NetworkException;
import net.weaver.api.network.NetworkProtocol;
import net.weaver.api.network.PayloadBuffer;
import net.weaver.api.network.PayloadCodec;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One server-authoritative configuration snapshot travelling to a client.
 *
 * <p>The document carries the owning mod id, the sending side, the schema
 * version, and the encoded key/value map produced from a
 * {@link ConfigManager#document() manager snapshot}. Values use the same
 * JSON-compatible encode model as {@link ConfigDocument}: booleans, strings,
 * integral and decimal numbers, lists, and string-keyed maps.</p>
 *
 * <p>The wire codec in {@link #CODEC} is bounded and strict. It validates every
 * count before allocating, rejects duplicate keys, limits nesting depth, uses
 * strict UTF-8 decoding through {@link PayloadBuffer}, and consumes exactly the
 * bytes it writes. A document is immutable after construction; nested lists and
 * maps are defensively frozen.</p>
 *
 * @param modId         the owning mod id, matching {@code [a-z0-9_.-]+}
 * @param side          the side that produced the document
 * @param schemaVersion the schema version of the encoded values, at least
 *                      {@code 1}
 * @param values        the encoded values in insertion order
 */
public record ConfigSyncDocument(String modId, Side side, int schemaVersion,
                                 Map<String, Object> values)
{
    /**
     * The wire format version used by {@link #CODEC}.
     */
    public static final int FORMAT_VERSION = 1;

    /**
     * The maximum number of top-level values in one document.
     */
    public static final int MAX_KEYS = 1024;

    /**
     * The maximum length of one key, in Java chars.
     */
    public static final int MAX_KEY_CHARS = 256;

    /**
     * The maximum length of one string value, in Java chars.
     */
    public static final int MAX_VALUE_CHARS = 32_768;

    /**
     * The maximum number of elements in one wire list or nested map.
     */
    public static final int MAX_COLLECTION_ELEMENTS = 8192;

    /**
     * The maximum nesting depth of lists and maps.
     */
    public static final int MAX_DEPTH = 8;

    private static final Pattern MOD_ID = Pattern.compile("[a-z0-9_.-]+");
    private static final int TAG_NULL = 0;
    private static final int TAG_BOOLEAN = 1;
    private static final int TAG_LONG = 2;
    private static final int TAG_DOUBLE = 3;
    private static final int TAG_STRING = 4;
    private static final int TAG_LIST = 5;
    private static final int TAG_MAP = 6;

    /**
     * The stable wire codec for configuration sync documents.
     */
    public static final PayloadCodec<ConfigSyncDocument> CODEC = new PayloadCodec<>()
    {
        @Override
        public void encode(ConfigSyncDocument value, PayloadBuffer out)
        {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(out, "out");
            out.writeByte(FORMAT_VERSION);
            out.writeString(value.modId(), NetworkProtocol.MAX_MOD_ID_CHARS);
            out.writeByte(value.side().ordinal());
            out.writeVarInt(value.schemaVersion());
            out.writeVarInt(value.values().size());
            for (Map.Entry<String, Object> entry : value.values().entrySet())
            {
                out.writeString(entry.getKey(), MAX_KEY_CHARS);
                writeValue(entry.getValue(), out, 0);
            }
        }

        @Override
        public ConfigSyncDocument decode(PayloadBuffer in)
        {
            Objects.requireNonNull(in, "in");
            try
            {
                int formatVersion = in.readUnsignedByte();
                if (formatVersion != FORMAT_VERSION)
                {
                    throw new NetworkException("unsupported config sync format version "
                            + formatVersion + "; expected " + FORMAT_VERSION);
                }
                String modId = in.readString(NetworkProtocol.MAX_MOD_ID_CHARS);
                int sideOrdinal = in.readUnsignedByte();
                Side[] sides = Side.values();
                if (sideOrdinal >= sides.length)
                {
                    throw new NetworkException("invalid config sync side ordinal "
                            + sideOrdinal);
                }
                Side side = sides[sideOrdinal];
                int schemaVersion = in.readVarInt();
                if (schemaVersion < 1)
                {
                    throw new NetworkException("config sync schema version must be at least 1 "
                            + "but was " + schemaVersion);
                }
                int count = in.readVarInt();
                if (count < 0 || count > MAX_KEYS)
                {
                    throw new NetworkException("config sync value count " + count
                            + " is outside 0.." + MAX_KEYS);
                }
                Map<String, Object> values = new LinkedHashMap<>(count);
                for (int index = 0; index < count; index++)
                {
                    String key = in.readString(MAX_KEY_CHARS);
                    if (values.containsKey(key))
                    {
                        throw new NetworkException("duplicate config sync key '" + key + "'");
                    }
                    values.put(key, readValue(in, 0));
                }
                ConfigSyncDocument document = new ConfigSyncDocument(modId, side,
                        schemaVersion, values);
                in.ensureFullyRead();
                return document;
            }
            catch (IllegalArgumentException failure)
            {
                throw new NetworkException("invalid config sync document: "
                        + failure.getMessage(), failure);
            }
        }
    };

    /**
     * Validates the components and freezes the encoded value map.
     *
     * @throws NullPointerException     when {@code modId}, {@code side}, or
     *                                  {@code values} is {@code null}, or a key
     *                                  is {@code null}
     * @throws IllegalArgumentException when the mod id, schema version, key
     *                                  count, or key length is invalid
     */
    public ConfigSyncDocument
    {
        Objects.requireNonNull(modId, "modId");
        if (modId.length() > NetworkProtocol.MAX_MOD_ID_CHARS
                || !MOD_ID.matcher(modId).matches())
        {
            throw new IllegalArgumentException("config sync mod id '" + modId
                    + "' must match [a-z0-9_.-]+" + " and be at most "
                    + NetworkProtocol.MAX_MOD_ID_CHARS + " chars");
        }
        Objects.requireNonNull(side, "side");
        if (schemaVersion < 1)
        {
            throw new IllegalArgumentException("config sync schema version must be at least 1 "
                    + "but was " + schemaVersion);
        }
        Objects.requireNonNull(values, "values");
        if (values.size() > MAX_KEYS)
        {
            throw new IllegalArgumentException("config sync document contains " + values.size()
                    + " keys, exceeding the limit of " + MAX_KEYS);
        }
        ConfigDocument frozen = new ConfigDocument(schemaVersion, values);
        for (String key : frozen.values().keySet())
        {
            if (key.length() > MAX_KEY_CHARS)
            {
                throw new IllegalArgumentException("config sync key of " + key.length()
                        + " chars exceeds the limit of " + MAX_KEY_CHARS);
            }
        }
        values = frozen.values();
    }

    /**
     * Creates a document from a manager or store snapshot.
     *
     * @param modId    the owning mod id, not {@code null}
     * @param side     the side that produced the snapshot, not {@code null}
     * @param document the encoded snapshot, not {@code null}
     * @return a new document, never {@code null}
     * @throws NullPointerException     when an argument is {@code null}
     * @throws IllegalArgumentException when the mod id or schema version is
     *                                  invalid
     */
    public static ConfigSyncDocument from(String modId, Side side, ConfigDocument document)
    {
        Objects.requireNonNull(document, "document");
        return new ConfigSyncDocument(modId, side, document.schemaVersion(),
                document.values());
    }

    /**
     * Returns the encoded values as a {@link ConfigDocument}.
     *
     * @return a defensive snapshot, never {@code null}
     */
    public ConfigDocument document()
    {
        return new ConfigDocument(schemaVersion, values);
    }

    private static void writeValue(Object value, PayloadBuffer out, int depth)
    {
        if (depth > MAX_DEPTH)
        {
            throw new NetworkException("config sync value exceeds the nesting limit of "
                    + MAX_DEPTH);
        }
        if (value == null)
        {
            out.writeByte(TAG_NULL);
            return;
        }
        if (value instanceof Boolean flag)
        {
            out.writeByte(TAG_BOOLEAN);
            out.writeBoolean(flag);
            return;
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long)
        {
            out.writeByte(TAG_LONG);
            out.writeLong(((Number) value).longValue());
            return;
        }
        if (value instanceof BigInteger integer)
        {
            try
            {
                out.writeByte(TAG_LONG);
                out.writeLong(integer.longValueExact());
            }
            catch (ArithmeticException failure)
            {
                throw new NetworkException("config sync integer is outside the long range",
                        failure);
            }
            return;
        }
        if (value instanceof Float || value instanceof Double)
        {
            out.writeByte(TAG_DOUBLE);
            out.writeLong(Double.doubleToRawLongBits(((Number) value).doubleValue()));
            return;
        }
        if (value instanceof BigDecimal decimal)
        {
            out.writeByte(TAG_DOUBLE);
            out.writeLong(Double.doubleToRawLongBits(decimal.doubleValue()));
            return;
        }
        if (value instanceof String text)
        {
            out.writeByte(TAG_STRING);
            out.writeString(text, MAX_VALUE_CHARS);
            return;
        }
        if (value instanceof List<?> list)
        {
            if (list.size() > MAX_COLLECTION_ELEMENTS)
            {
                throw new NetworkException("config sync list of " + list.size()
                        + " elements exceeds the limit of " + MAX_COLLECTION_ELEMENTS);
            }
            out.writeByte(TAG_LIST);
            out.writeVarInt(list.size());
            for (Object element : list)
            {
                writeValue(element, out, depth + 1);
            }
            return;
        }
        if (value instanceof Map<?, ?> map)
        {
            if (map.size() > MAX_COLLECTION_ELEMENTS)
            {
                throw new NetworkException("config sync map of " + map.size()
                        + " entries exceeds the limit of " + MAX_COLLECTION_ELEMENTS);
            }
            out.writeByte(TAG_MAP);
            out.writeVarInt(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet())
            {
                if (!(entry.getKey() instanceof String key))
                {
                    throw new NetworkException("config sync map keys must be strings but found "
                            + (entry.getKey() == null ? "null"
                            : entry.getKey().getClass().getName()));
                }
                out.writeString(key, MAX_KEY_CHARS);
                writeValue(entry.getValue(), out, depth + 1);
            }
            return;
        }
        throw new NetworkException("unsupported config sync value type: "
                + value.getClass().getName());
    }

    private static Object readValue(PayloadBuffer in, int depth)
    {
        if (depth > MAX_DEPTH)
        {
            throw new NetworkException("config sync value exceeds the nesting limit of "
                    + MAX_DEPTH);
        }
        int tag = in.readUnsignedByte();
        return switch (tag)
        {
            case TAG_NULL -> null;
            case TAG_BOOLEAN -> in.readBoolean();
            case TAG_LONG -> in.readLong();
            case TAG_DOUBLE -> Double.longBitsToDouble(in.readLong());
            case TAG_STRING -> in.readString(MAX_VALUE_CHARS);
            case TAG_LIST -> readList(in, depth + 1);
            case TAG_MAP -> readMap(in, depth + 1);
            default -> throw new NetworkException("unsupported config sync value tag " + tag);
        };
    }

    private static List<Object> readList(PayloadBuffer in, int depth)
    {
        int size = in.readVarInt();
        if (size < 0 || size > MAX_COLLECTION_ELEMENTS)
        {
            throw new NetworkException("config sync list size " + size
                    + " is outside 0.." + MAX_COLLECTION_ELEMENTS);
        }
        List<Object> list = new ArrayList<>(size);
        for (int index = 0; index < size; index++)
        {
            list.add(readValue(in, depth));
        }
        return list;
    }

    private static Map<String, Object> readMap(PayloadBuffer in, int depth)
    {
        int size = in.readVarInt();
        if (size < 0 || size > MAX_COLLECTION_ELEMENTS)
        {
            throw new NetworkException("config sync map size " + size
                    + " is outside 0.." + MAX_COLLECTION_ELEMENTS);
        }
        Map<String, Object> map = new LinkedHashMap<>(size);
        for (int index = 0; index < size; index++)
        {
            String key = in.readString(MAX_KEY_CHARS);
            if (map.containsKey(key))
            {
                throw new NetworkException("duplicate config sync map key '" + key + "'");
            }
            map.put(key, readValue(in, depth));
        }
        return map;
    }
}
