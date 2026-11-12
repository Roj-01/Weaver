package net.weaver.core.data;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Reflection-only {@link ItemDataAccess} for a game {@code ItemStack}.
 *
 * <p>The bridge resolves the game classes from the holder's own class loader
 * and caches the resolved members after the first successful lookup. No game
 * types are referenced at compile time. Every lookup or invocation failure is
 * reported as an {@link ItemDataException}; raw reflection exceptions never
 * escape.</p>
 *
 * <p>Data is stored in the compound at {@link ItemDataLimits#ROOT_KEY} inside
 * the {@code CUSTOM_DATA} component. Other custom-data keys and all other
 * components are preserved. When the root compound becomes empty the root key
 * is removed, and when the custom-data compound becomes empty the
 * {@code CUSTOM_DATA} component is removed from the stack.</p>
 *
 * <p>This class is not thread-safe for concurrent mutations of the same stack,
 * but reflection resolution is safe to race: it is published through a
 * volatile field.</p>
 */
public final class ReflectiveItemDataAccess implements ItemDataAccess
{
    private static final String ITEM_STACK_CLASS = "net.minecraft.world.item.ItemStack";
    private static final String DATA_COMPONENTS_CLASS = "net.minecraft.core.component.DataComponents";
    private static final String CUSTOM_DATA_CLASS = "net.minecraft.world.item.component.CustomData";
    private static final String COMPOUND_TAG_CLASS = "net.minecraft.nbt.CompoundTag";
    private static final String TAG_CLASS = "net.minecraft.nbt.Tag";
    private static final String CUSTOM_DATA_FIELD = "CUSTOM_DATA";

    private volatile Reflection reflection;

    /**
     * Creates a bridge that resolves the game classes lazily from the first
     * supported holder it sees.
     */
    public ReflectiveItemDataAccess()
    {
    }

    @Override
    public boolean supports(Object holder)
    {
        return holder != null && holder.getClass().getName().equals(ITEM_STACK_CLASS);
    }

    @Override
    public boolean isEmpty(Object holder)
    {
        requireHolder(holder);
        Reflection r = reflection(holder);
        return Boolean.TRUE.equals(invoke(r.stackIsEmpty, holder, "ItemStack.isEmpty"));
    }

    @Override
    public byte[] read(Object holder, String key)
    {
        requireHolder(holder);
        requireKey(key);
        Reflection r = reflection(holder);
        Object customData = getCustomData(r, holder);
        if (customData == null)
        {
            return null;
        }
        Object tag = copyTag(r, customData);
        Object root = rootTag(r, tag);
        if (!contains(r, root, key))
        {
            return null;
        }
        return readBytes(r, root, key);
    }

    @Override
    public boolean contains(Object holder, String key)
    {
        requireHolder(holder);
        requireKey(key);
        Reflection r = reflection(holder);
        Object customData = getCustomData(r, holder);
        if (customData == null)
        {
            return false;
        }
        Object tag = copyTag(r, customData);
        Object root = rootTag(r, tag);
        return contains(r, root, key);
    }

    @Override
    public void write(Object holder, String key, byte[] data)
    {
        requireHolder(holder);
        requireKey(key);
        if (data == null)
        {
            throw new IllegalArgumentException("data must not be null");
        }
        if (data.length > ItemDataLimits.MAX_VALUE_BYTES)
        {
            throw new ItemDataException("Value for key '" + key + "' is too large: "
                    + data.length + " bytes (max " + ItemDataLimits.MAX_VALUE_BYTES + ")");
        }
        Reflection r = reflection(holder);
        Object customData = getCustomData(r, holder);
        Object tag = customData == null ? newCompound(r) : copyTag(r, customData);
        Object root = rootTag(r, tag);
        if (!contains(r, root, key))
        {
            int count = toInt(invoke(r.size, root, "CompoundTag.size"), "CompoundTag.size");
            if (count >= ItemDataLimits.MAX_ENTRIES)
            {
                throw new ItemDataException("Capability root already has " + count
                        + " entries (max " + ItemDataLimits.MAX_ENTRIES
                        + "); cannot add key '" + key + "'");
            }
        }
        invoke(r.putByteArray, root, "CompoundTag.putByteArray", key, data.clone());
        int total = toInt(invoke(r.sizeInBytes, root, "CompoundTag.sizeInBytes"),
                "CompoundTag.sizeInBytes");
        if (total > ItemDataLimits.MAX_TOTAL_BYTES)
        {
            throw new ItemDataException("Capability data is too large after writing key '"
                    + key + "': " + total + " bytes (max " + ItemDataLimits.MAX_TOTAL_BYTES + ")");
        }
        invoke(r.put, tag, "CompoundTag.put", ItemDataLimits.ROOT_KEY, root);
        storeTag(r, holder, tag);
    }

    @Override
    public byte[] remove(Object holder, String key)
    {
        requireHolder(holder);
        requireKey(key);
        Reflection r = reflection(holder);
        Object customData = getCustomData(r, holder);
        if (customData == null)
        {
            return null;
        }
        Object tag = copyTag(r, customData);
        Object root = rootTag(r, tag);
        if (!contains(r, root, key))
        {
            return null;
        }
        byte[] removed = readBytes(r, root, key);
        invoke(r.remove, root, "CompoundTag.remove", key);
        if (Boolean.TRUE.equals(invoke(r.compoundIsEmpty, root, "CompoundTag.isEmpty")))
        {
            invoke(r.remove, tag, "CompoundTag.remove", ItemDataLimits.ROOT_KEY);
        }
        else
        {
            invoke(r.put, tag, "CompoundTag.put", ItemDataLimits.ROOT_KEY, root);
        }
        storeTag(r, holder, tag);
        return removed;
    }

    @Override
    public void clear(Object holder)
    {
        requireHolder(holder);
        Reflection r = reflection(holder);
        Object customData = getCustomData(r, holder);
        if (customData == null)
        {
            return;
        }
        Object tag = copyTag(r, customData);
        if (!contains(r, tag, ItemDataLimits.ROOT_KEY))
        {
            return;
        }
        invoke(r.remove, tag, "CompoundTag.remove", ItemDataLimits.ROOT_KEY);
        storeTag(r, holder, tag);
    }

    @Override
    public int entryCount(Object holder)
    {
        requireHolder(holder);
        Reflection r = reflection(holder);
        Object customData = getCustomData(r, holder);
        if (customData == null)
        {
            return 0;
        }
        Object tag = copyTag(r, customData);
        if (!contains(r, tag, ItemDataLimits.ROOT_KEY))
        {
            return 0;
        }
        Object root = rootTag(r, tag);
        return toInt(invoke(r.size, root, "CompoundTag.size"), "CompoundTag.size");
    }

    @Override
    public int totalBytes(Object holder)
    {
        requireHolder(holder);
        final Reflection r;
        try
        {
            r = reflection(holder);
        }
        catch (ItemDataException unavailable)
        {
            return -1;
        }
        Object customData = getCustomData(r, holder);
        if (customData == null)
        {
            return 0;
        }
        Object tag = copyTag(r, customData);
        if (!contains(r, tag, ItemDataLimits.ROOT_KEY))
        {
            return 0;
        }
        Object root = rootTag(r, tag);
        return toInt(invoke(r.sizeInBytes, root, "CompoundTag.sizeInBytes"),
                "CompoundTag.sizeInBytes");
    }

    private Reflection reflection(Object holder)
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
                reflection = resolve(holder);
            }
            return reflection;
        }
    }

    private static Reflection resolve(Object holder)
    {
        ClassLoader loader = holder.getClass().getClassLoader();
        if (loader == null)
        {
            loader = ReflectiveItemDataAccess.class.getClassLoader();
        }
        try
        {
            Class<?> itemStack = Class.forName(ITEM_STACK_CLASS, false, loader);
            Class<?> dataComponents = Class.forName(DATA_COMPONENTS_CLASS, false, loader);
            Class<?> customData = Class.forName(CUSTOM_DATA_CLASS, false, loader);
            Class<?> compoundTag = Class.forName(COMPOUND_TAG_CLASS, false, loader);
            Class<?> tag = Class.forName(TAG_CLASS, false, loader);

            Field customDataField = dataComponents.getField(CUSTOM_DATA_FIELD);
            Class<?> customDataType = customDataField.getType();
            Object customDataToken = customDataField.get(null);

            Method stackGet = itemStack.getMethod("get", customDataType);
            Method stackSet = itemStack.getMethod("set", customDataType, Object.class);
            Method stackRemove = itemStack.getMethod("remove", customDataType);
            Method stackIsEmpty = itemStack.getMethod("isEmpty");

            Method customDataOf = customData.getMethod("of", compoundTag);
            Method copyTag = customData.getMethod("copyTag");

            Constructor<?> compoundConstructor = compoundTag.getConstructor();
            Method getCompoundOrEmpty = compoundTag.getMethod("getCompoundOrEmpty", String.class);
            Method put = compoundTag.getMethod("put", String.class, tag);
            Method putByteArray = compoundTag.getMethod("putByteArray", String.class, byte[].class);
            Method getByteArray = compoundTag.getMethod("getByteArray", String.class);
            Method contains = compoundTag.getMethod("contains", String.class);
            Method remove = compoundTag.getMethod("remove", String.class);
            Method compoundIsEmpty = compoundTag.getMethod("isEmpty");
            Method size = compoundTag.getMethod("size");
            Method sizeInBytes = compoundTag.getMethod("sizeInBytes");

            return new Reflection(customDataToken, stackGet, stackSet, stackRemove,
                    stackIsEmpty, customDataOf, copyTag, compoundConstructor, getCompoundOrEmpty,
                    put, putByteArray, getByteArray, contains, remove, compoundIsEmpty, size,
                    sizeInBytes);
        }
        catch (Throwable failure)
        {
            throw new ItemDataException("Could not resolve Minecraft item data classes from "
                    + loaderName(loader) + ": " + describe(failure), unwrap(failure));
        }
    }

    private static void requireHolder(Object holder)
    {
        if (holder == null || !holder.getClass().getName().equals(ITEM_STACK_CLASS))
        {
            throw new ItemDataException("Unsupported holder "
                    + (holder == null ? "null" : holder.getClass().getName())
                    + "; expected " + ITEM_STACK_CLASS);
        }
    }

    private static void requireKey(String key)
    {
        if (key == null)
        {
            throw new IllegalArgumentException("key must not be null");
        }
        if (key.length() > ItemDataLimits.MAX_KEY_CHARS)
        {
            throw new ItemDataException("Capability key is too long: " + key.length()
                    + " chars (max " + ItemDataLimits.MAX_KEY_CHARS + ")");
        }
    }

    private static Object getCustomData(Reflection r, Object holder)
    {
        return invoke(r.stackGet, holder, "ItemStack.get(CUSTOM_DATA)", r.customDataToken);
    }

    private static Object copyTag(Reflection r, Object customData)
    {
        Object tag = invoke(r.copyTag, customData, "CustomData.copyTag");
        if (tag == null)
        {
            throw new ItemDataException("CustomData.copyTag returned null");
        }
        return tag;
    }

    private static Object rootTag(Reflection r, Object tag)
    {
        Object root = invoke(r.getCompoundOrEmpty, tag, "CompoundTag.getCompoundOrEmpty",
                ItemDataLimits.ROOT_KEY);
        if (root == null)
        {
            throw new ItemDataException("CompoundTag.getCompoundOrEmpty returned null");
        }
        return root;
    }

    private static Object newCompound(Reflection r)
    {
        try
        {
            return r.compoundConstructor.newInstance();
        }
        catch (InvocationTargetException failure)
        {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            throw new ItemDataException("new CompoundTag failed: " + describe(cause), cause);
        }
        catch (Throwable failure)
        {
            throw new ItemDataException("new CompoundTag failed: " + describe(failure),
                    unwrap(failure));
        }
    }

    private static void storeTag(Reflection r, Object holder, Object tag)
    {
        if (Boolean.TRUE.equals(invoke(r.compoundIsEmpty, tag, "CompoundTag.isEmpty")))
        {
            invoke(r.stackRemove, holder, "ItemStack.remove(CUSTOM_DATA)", r.customDataToken);
            return;
        }
        Object customData = invoke(r.customDataOf, null, "CustomData.of", tag);
        invoke(r.stackSet, holder, "ItemStack.set(CUSTOM_DATA)", r.customDataToken, customData);
    }

    private static boolean contains(Reflection r, Object compound, String key)
    {
        return Boolean.TRUE.equals(invoke(r.contains, compound, "CompoundTag.contains", key));
    }

    private static byte[] readBytes(Reflection r, Object root, String key)
    {
        Object result = invoke(r.getByteArray, root, "CompoundTag.getByteArray", key);
        if (!(result instanceof Optional<?> optional))
        {
            throw new ItemDataException("CompoundTag.getByteArray returned " + typeName(result));
        }
        if (optional.isEmpty())
        {
            return null;
        }
        Object value = optional.get();
        if (!(value instanceof byte[] bytes))
        {
            throw new ItemDataException("CompoundTag.getByteArray contained " + typeName(value));
        }
        return bytes.clone();
    }

    private static int toInt(Object value, String action)
    {
        if (value instanceof Integer integer)
        {
            return integer;
        }
        throw new ItemDataException(action + " returned " + typeName(value));
    }

    private static Object invoke(Method method, Object target, String action, Object... arguments)
    {
        try
        {
            return method.invoke(target, arguments);
        }
        catch (InvocationTargetException failure)
        {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            throw new ItemDataException(action + " failed: " + describe(cause), cause);
        }
        catch (Throwable failure)
        {
            throw new ItemDataException(action + " failed: " + describe(failure),
                    unwrap(failure));
        }
    }

    private static String loaderName(ClassLoader loader)
    {
        return loader == null ? "<bootstrap>" : loader.getName();
    }

    private static String typeName(Object value)
    {
        return value == null ? "null" : value.getClass().getName();
    }

    private static Throwable unwrap(Throwable failure)
    {
        if (failure instanceof InvocationTargetException invocation && invocation.getCause() != null)
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

    private record Reflection(Object customDataToken, Method stackGet,
                              Method stackSet, Method stackRemove, Method stackIsEmpty,
                              Method customDataOf, Method copyTag,
                              Constructor<?> compoundConstructor, Method getCompoundOrEmpty,
                              Method put, Method putByteArray, Method getByteArray,
                              Method contains, Method remove, Method compoundIsEmpty, Method size,
                              Method sizeInBytes)
    {
    }
}
