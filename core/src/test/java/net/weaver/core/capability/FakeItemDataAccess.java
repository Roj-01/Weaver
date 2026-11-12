package net.weaver.core.capability;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.weaver.core.data.ItemDataAccess;
import net.weaver.core.data.ItemDataException;

final class FakeItemDataAccess implements ItemDataAccess
{
    private final Map<FakeItemHolder, Map<String, byte[]>> byHolder =
            new IdentityHashMap<>();

    @Override
    public boolean supports(Object holder)
    {
        return holder instanceof FakeItemHolder;
    }

    @Override
    public boolean isEmpty(Object holder)
    {
        return requireHolder(holder).empty;
    }

    @Override
    public byte[] read(Object holder, String key)
    {
        Objects.requireNonNull(key, "key");
        byte[] data = values(holder).get(key);
        return data == null ? null : data.clone();
    }

    @Override
    public boolean contains(Object holder, String key)
    {
        Objects.requireNonNull(key, "key");
        return values(holder).containsKey(key);
    }

    @Override
    public void write(Object holder, String key, byte[] data)
    {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(data, "data");
        values(holder).put(key, data.clone());
    }

    @Override
    public byte[] remove(Object holder, String key)
    {
        Objects.requireNonNull(key, "key");
        byte[] removed = values(holder).remove(key);
        return removed == null ? null : removed.clone();
    }

    @Override
    public void clear(Object holder)
    {
        values(holder).clear();
    }

    @Override
    public int entryCount(Object holder)
    {
        return values(holder).size();
    }

    @Override
    public int totalBytes(Object holder)
    {
        int total = 0;
        for (byte[] data : values(holder).values())
        {
            total += data.length;
        }
        return total;
    }

    void putRaw(FakeItemHolder holder, String key, byte[] data)
    {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(data, "data");
        values(holder).put(key, data.clone());
    }

    void copy(FakeItemHolder source, FakeItemHolder target)
    {
        Map<String, byte[]> targetValues = values(target);
        targetValues.clear();
        values(source).forEach((key, data) -> targetValues.put(key, data.clone()));
        target.empty = source.empty;
    }

    private Map<String, byte[]> values(Object holder)
    {
        return byHolder.computeIfAbsent(requireHolder(holder),
                ignored -> new LinkedHashMap<>());
    }

    private static FakeItemHolder requireHolder(Object holder)
    {
        if (!(holder instanceof FakeItemHolder fake))
        {
            throw new ItemDataException("Unsupported holder: "
                    + (holder == null ? "null" : holder.getClass().getName()));
        }
        return fake;
    }

    static final class FakeItemHolder
    {
        private boolean empty;

        FakeItemHolder()
        {
        }

        static FakeItemHolder empty()
        {
            FakeItemHolder holder = new FakeItemHolder();
            holder.empty = true;
            return holder;
        }
    }
}
