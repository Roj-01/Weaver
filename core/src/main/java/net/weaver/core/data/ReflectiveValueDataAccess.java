package net.weaver.core.data;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.weaver.api.capability.CapabilityException;

/**
 * Reflection-only {@link ValueDataAccess} for the game's value carriers.
 *
 * <p>The bridge resolves {@code net.minecraft.world.level.storage.ValueOutput}
 * and {@code ValueInput} from the carrier's own class loader and caches the
 * resolved members per carrier class. No game types are referenced at compile
 * time. Values are stored through {@code Codec.BYTE_BUFFER}, which the game
 * serializes as a byte array inside the Weaver child.</p>
 *
 * <p>Writing an empty map removes the Weaver child from the output carrier.
 * Reading ignores missing keys and values that violate
 * {@link HolderDataLimits#MAX_VALUE_BYTES}. Every lookup or invocation failure
 * is reported as a {@link CapabilityException}; raw reflection exceptions never
 * escape.</p>
 *
 * <p>This class is not thread-safe for concurrent mutations of the same
 * carrier, but reflection resolution is safe to race: it is published through a
 * volatile field.</p>
 */
public final class ReflectiveValueDataAccess implements ValueDataAccess
{
    private static final String VALUE_OUTPUT_CLASS =
            "net.minecraft.world.level.storage.ValueOutput";
    private static final String VALUE_INPUT_CLASS =
            "net.minecraft.world.level.storage.ValueInput";
    private static final String CODEC_CLASS = "com.mojang.serialization.Codec";

    private volatile Reflection reflection;

    /**
     * Creates a bridge that resolves the game classes lazily from the first
     * supported carrier it sees.
     */
    public ReflectiveValueDataAccess()
    {
    }

    @Override
    public boolean supportsOutput(Object carrier)
    {
        return carrier != null
                && implementsType(carrier.getClass(), VALUE_OUTPUT_CLASS);
    }

    @Override
    public boolean supportsInput(Object carrier)
    {
        return carrier != null
                && implementsType(carrier.getClass(), VALUE_INPUT_CLASS);
    }

    @Override
    public void writeAll(Object output, String rootKey, Map<String, byte[]> values)
    {
        requireOutput(output);
        Objects.requireNonNull(rootKey, "rootKey");
        Objects.requireNonNull(values, "values");
        Reflection r = reflection(output);
        if (values.isEmpty())
        {
            invoke(r.outputDiscard, output, "ValueOutput.discard", rootKey);
            return;
        }
        validate(values);
        Object child = invoke(r.outputChild, output, "ValueOutput.child", rootKey);
        for (Map.Entry<String, byte[]> entry : values.entrySet())
        {
            invoke(r.outputStore, child, "ValueOutput.store", entry.getKey(),
                    r.byteBufferCodec, ByteBuffer.wrap(entry.getValue().clone()));
        }
    }

    @Override
    public Map<String, byte[]> readAll(Object input, String rootKey, Collection<String> keys)
    {
        requireInput(input);
        Objects.requireNonNull(rootKey, "rootKey");
        Objects.requireNonNull(keys, "keys");
        Map<String, byte[]> values = new LinkedHashMap<>();
        Reflection r = reflection(input);
        Object optional = invoke(r.inputChild, input, "ValueInput.child", rootKey);
        if (!(optional instanceof Optional<?> childOptional) || childOptional.isEmpty())
        {
            return values;
        }
        Object child = childOptional.get();
        int entries = 0;
        int total = 0;
        for (String key : keys)
        {
            if (key == null || key.isBlank()
                    || key.length() > HolderDataLimits.MAX_KEY_CHARS)
            {
                continue;
            }
            if (entries >= HolderDataLimits.MAX_ENTRIES)
            {
                break;
            }
            Object read = invoke(r.inputRead, child, "ValueInput.read", key,
                    r.byteBufferCodec);
            if (!(read instanceof Optional<?> valueOptional) || valueOptional.isEmpty())
            {
                continue;
            }
            Object value = valueOptional.get();
            if (!(value instanceof ByteBuffer buffer))
            {
                continue;
            }
            byte[] bytes = toBytes(buffer);
            if (bytes.length <= 0 || bytes.length > HolderDataLimits.MAX_VALUE_BYTES)
            {
                continue;
            }
            if (total + bytes.length > HolderDataLimits.MAX_TOTAL_BYTES)
            {
                break;
            }
            values.put(key, bytes);
            entries++;
            total += bytes.length;
        }
        return values;
    }

    /**
     * Reads the whole Weaver child as a {@code String -> ByteBuffer} map.
     *
     * <p>Uses {@code Codec.unboundedMap(Codec.STRING, Codec.BYTE_BUFFER)}. When
     * that codec is unavailable, the child is absent, or any entry fails to
     * decode, this returns {@code null} so the caller falls back to keyed
     * reads.</p>
     */
    @Override
    public Map<String, byte[]> readAllStored(Object input, String rootKey)
    {
        requireInput(input);
        Objects.requireNonNull(rootKey, "rootKey");
        Reflection r = reflection(input);
        if (r.storedMapCodec == null)
        {
            return null;
        }
        Object read = invoke(r.inputRead, input, "ValueInput.read", rootKey, r.storedMapCodec);
        if (!(read instanceof Optional<?> mapOptional) || mapOptional.isEmpty()
                || !(mapOptional.get() instanceof Map<?, ?> stored))
        {
            return null;
        }
        Map<String, byte[]> values = new LinkedHashMap<>();
        int total = 0;
        for (Map.Entry<?, ?> entry : stored.entrySet())
        {
            if (!(entry.getKey() instanceof String key) || key.isBlank()
                    || key.length() > HolderDataLimits.MAX_KEY_CHARS
                    || !(entry.getValue() instanceof ByteBuffer buffer))
            {
                continue;
            }
            if (values.size() >= HolderDataLimits.MAX_ENTRIES)
            {
                break;
            }
            byte[] bytes = toBytes(buffer);
            if (bytes.length <= 0 || bytes.length > HolderDataLimits.MAX_VALUE_BYTES
                    || total + bytes.length > HolderDataLimits.MAX_TOTAL_BYTES)
            {
                continue;
            }
            values.put(key, bytes);
            total += bytes.length;
        }
        return values;
    }

    private Reflection reflection(Object carrier)
    {
        Reflection current = reflection;
        if (current != null)
        {
            return current;
        }
        synchronized (this)
        {
            if (reflection == null)
            {
                reflection = resolve(carrier);
            }
            return reflection;
        }
    }

    private static Reflection resolve(Object carrier)
    {
        ClassLoader loader = carrier.getClass().getClassLoader();
        if (loader == null)
        {
            loader = ReflectiveValueDataAccess.class.getClassLoader();
        }
        try
        {
            Class<?> valueOutput = Class.forName(VALUE_OUTPUT_CLASS, false, loader);
            Class<?> valueInput = Class.forName(VALUE_INPUT_CLASS, false, loader);
            Class<?> codec = Class.forName(CODEC_CLASS, false, loader);

            Method outputChild = valueOutput.getMethod("child", String.class);
            Method outputStore = valueOutput.getMethod("store", String.class, codec,
                    Object.class);
            Method outputDiscard = valueOutput.getMethod("discard", String.class);
            Object byteBufferCodec = codec.getField("BYTE_BUFFER").get(null);

            Method inputChild = valueInput.getMethod("child", String.class);
            Method inputRead = valueInput.getMethod("read", String.class, codec);
            Object storedMapCodec = storedMapCodec(codec, byteBufferCodec);

            return new Reflection(outputChild, outputStore, outputDiscard,
                    byteBufferCodec, inputChild, inputRead, storedMapCodec);
        }
        catch (Throwable failure)
        {
            throw new CapabilityException("Could not resolve Minecraft value classes from "
                    + loaderName(loader) + ": " + describe(failure), unwrap(failure));
        }
    }

    private static Object storedMapCodec(Class<?> codec, Object byteBufferCodec)
    {
        try
        {
            Object stringCodec = codec.getField("STRING").get(null);
            Method unboundedMap = codec.getMethod("unboundedMap", codec, codec);
            return unboundedMap.invoke(null, stringCodec, byteBufferCodec);
        }
        catch (Throwable unavailable)
        {
            return null;
        }
    }

    private static void validate(Map<String, byte[]> values)
    {
        if (values.size() > HolderDataLimits.MAX_ENTRIES)
        {
            throw new CapabilityException("Capability root has " + values.size()
                    + " entries (max " + HolderDataLimits.MAX_ENTRIES + ")");
        }
        int total = 0;
        for (Map.Entry<String, byte[]> entry : values.entrySet())
        {
            String key = entry.getKey();
            byte[] data = entry.getValue();
            if (key == null || key.isBlank())
            {
                throw new CapabilityException("Capability key must not be blank");
            }
            if (key.length() > HolderDataLimits.MAX_KEY_CHARS)
            {
                throw new CapabilityException("Capability key is too long: "
                        + key.length() + " chars (max " + HolderDataLimits.MAX_KEY_CHARS
                        + ")");
            }
            if (data == null || data.length <= 0)
            {
                throw new CapabilityException("Value for key '" + key
                        + "' must not be empty");
            }
            if (data.length > HolderDataLimits.MAX_VALUE_BYTES)
            {
                throw new CapabilityException("Value for key '" + key + "' is too large: "
                        + data.length + " bytes (max " + HolderDataLimits.MAX_VALUE_BYTES
                        + ")");
            }
            total += data.length;
        }
        if (total > HolderDataLimits.MAX_TOTAL_BYTES)
        {
            throw new CapabilityException("Capability data is too large: " + total
                    + " bytes (max " + HolderDataLimits.MAX_TOTAL_BYTES + ")");
        }
    }

    private static void requireOutput(Object carrier)
    {
        if (carrier == null || !implementsType(carrier.getClass(), VALUE_OUTPUT_CLASS))
        {
            throw new CapabilityException("Unsupported output carrier "
                    + (carrier == null ? "null" : carrier.getClass().getName())
                    + "; expected " + VALUE_OUTPUT_CLASS);
        }
    }

    private static void requireInput(Object carrier)
    {
        if (carrier == null || !implementsType(carrier.getClass(), VALUE_INPUT_CLASS))
        {
            throw new CapabilityException("Unsupported input carrier "
                    + (carrier == null ? "null" : carrier.getClass().getName())
                    + "; expected " + VALUE_INPUT_CLASS);
        }
    }

    private static boolean implementsType(Class<?> type, String interfaceName)
    {
        Class<?> current = type;
        while (current != null)
        {
            if (current.getName().equals(interfaceName))
            {
                return true;
            }
            if (implementsInterface(current, interfaceName))
            {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private static boolean implementsInterface(Class<?> type, String interfaceName)
    {
        for (Class<?> implemented : type.getInterfaces())
        {
            if (implemented.getName().equals(interfaceName)
                    || implementsInterface(implemented, interfaceName))
            {
                return true;
            }
        }
        return false;
    }

    private static byte[] toBytes(ByteBuffer buffer)
    {
        ByteBuffer copy = buffer.duplicate();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }

    private static Object invoke(Method method, Object target, String action,
            Object... arguments)
    {
        try
        {
            return method.invoke(target, arguments);
        }
        catch (InvocationTargetException failure)
        {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            throw new CapabilityException(action + " failed: " + describe(cause), cause);
        }
        catch (Throwable failure)
        {
            throw new CapabilityException(action + " failed: " + describe(failure),
                    unwrap(failure));
        }
    }

    private static String loaderName(ClassLoader loader)
    {
        return loader == null ? "<bootstrap>" : loader.getName();
    }

    private static Throwable unwrap(Throwable failure)
    {
        if (failure instanceof InvocationTargetException invocation
                && invocation.getCause() != null)
        {
            return invocation.getCause();
        }
        return failure;
    }

    private static String describe(Throwable failure)
    {
        if (failure == null)
        {
            return "unknown cause";
        }
        Throwable cause = unwrap(failure);
        String message = cause.getMessage();
        return message != null ? message : cause.getClass().getName();
    }

    private record Reflection(Method outputChild, Method outputStore, Method outputDiscard,
                              Object byteBufferCodec, Method inputChild, Method inputRead,
                              Object storedMapCodec)
    {
    }
}
