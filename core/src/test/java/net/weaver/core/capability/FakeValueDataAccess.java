package net.weaver.core.capability;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.weaver.core.data.ValueDataAccess;

final class FakeValueDataAccess implements ValueDataAccess
{
    private final Map<Object, Map<String, Map<String, byte[]>>> roots =
            new IdentityHashMap<>();
    private boolean outputsSupported = true;
    private boolean inputsSupported = true;
    private boolean failWrites;
    private boolean failReads;
    private boolean enumerable = true;

    @Override
    public boolean supportsOutput(Object carrier)
    {
        return outputsSupported && carrier instanceof FakeCarrier;
    }

    @Override
    public boolean supportsInput(Object carrier)
    {
        return inputsSupported && carrier instanceof FakeCarrier;
    }

    @Override
    public void writeAll(Object output, String rootKey, Map<String, byte[]> values)
    {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(rootKey, "rootKey");
        Objects.requireNonNull(values, "values");
        if (failWrites)
        {
            throw new IllegalStateException("write boom");
        }
        Map<String, byte[]> copy = new LinkedHashMap<>();
        values.forEach((key, data) -> copy.put(key, data.clone()));
        if (copy.isEmpty())
        {
            roots(output).remove(rootKey);
        }
        else
        {
            roots(output).put(rootKey, copy);
        }
    }

    @Override
    public Map<String, byte[]> readAll(Object input, String rootKey,
            Collection<String> keys)
    {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(rootKey, "rootKey");
        Objects.requireNonNull(keys, "keys");
        if (failReads)
        {
            throw new IllegalStateException("read boom");
        }
        Map<String, byte[]> stored = roots(input).get(rootKey);
        Map<String, byte[]> values = new LinkedHashMap<>();
        if (stored == null)
        {
            return values;
        }
        for (String key : keys)
        {
            byte[] data = stored.get(key);
            if (data != null)
            {
                values.put(key, data.clone());
            }
        }
        return values;
    }

    @Override
    public Map<String, byte[]> readAllStored(Object input, String rootKey)
    {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(rootKey, "rootKey");
        if (failReads)
        {
            throw new IllegalStateException("read boom");
        }
        if (!enumerable)
        {
            return null;
        }
        Map<String, byte[]> stored = roots(input).get(rootKey);
        Map<String, byte[]> values = new LinkedHashMap<>();
        if (stored != null)
        {
            stored.forEach((key, data) -> values.put(key, data.clone()));
        }
        return values;
    }

    void disableEnumeration()
    {
        enumerable = false;
    }

    void disableOutputs()
    {
        outputsSupported = false;
    }

    void disableInputs()
    {
        inputsSupported = false;
    }

    void failWrites()
    {
        failWrites = true;
    }

    void failReads()
    {
        failReads = true;
    }

    Map<String, byte[]> stored(Object carrier, String rootKey)
    {
        Map<String, byte[]> stored = roots(carrier).get(rootKey);
        return stored == null ? Map.of() : stored;
    }

    void putRaw(Object carrier, String rootKey, String key, byte[] data)
    {
        roots(carrier).computeIfAbsent(rootKey, ignored -> new LinkedHashMap<>())
                .put(key, data.clone());
    }

    private Map<String, Map<String, byte[]>> roots(Object carrier)
    {
        return roots.computeIfAbsent(carrier, ignored -> new LinkedHashMap<>());
    }

    static final class FakeCarrier
    {
    }
}
