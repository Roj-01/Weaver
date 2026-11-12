package net.weaver.core.client;

import net.weaver.api.client.ClientException;
import net.weaver.api.client.ClientThreadException;
import net.weaver.api.client.KeyMappingSpec;
import net.weaver.core.LifecycleHooks;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reflection-only client adapter for a live Minecraft client.
 *
 * <p>No game type is referenced at compile time. Availability is probed with a
 * non-initializing class lookup and cached. Every reflective failure is
 * reported once and degrades to a safe value; only key-mapping creation raises
 * a {@link ClientException}.
 */
final class ReflectiveClientGameAdapter implements ClientGameAdapter
{
    private static final String MINECRAFT_CLASS = "net.minecraft.client.Minecraft";
    private static final String KEY_MAPPING_CLASS = "net.minecraft.client.KeyMapping";
    private static final String KEY_MAPPING_CATEGORY_CLASS =
            "net.minecraft.client.KeyMapping$Category";
    private static final String IDENTIFIER_CLASS = "net.minecraft.resources.Identifier";
    private static final String INPUT_TYPE_CLASS =
            "com.mojang.blaze3d.platform.InputConstants$Type";
    private static final String INPUT_CONSTANTS_CLASS =
            "com.mojang.blaze3d.platform.InputConstants";
    private static final String COMPONENT_CLASS = "net.minecraft.network.chat.Component";
    private static final String MISC_CATEGORY = "misc";
    private static final String[] KEY_MAPPING_MAP_FIELDS = {"ALL", "MAP"};

    private final Set<String> loggedFailures = ConcurrentHashMap.newKeySet();
    private final Set<Object> optionMappings =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<String, Object> categories = new ConcurrentHashMap<>();
    private final Map<Class<?>, Method> consumeClickMethods = new ConcurrentHashMap<>();
    private final Map<Class<?>, Method> openScreenMethods = new ConcurrentHashMap<>();

    private volatile Boolean available;
    private volatile ClassLoader gameLoader;
    private volatile Class<?> minecraftClass;
    private volatile Method minecraftGetInstance;
    private volatile Method sameThreadMethod;
    private volatile Method literalMethod;
    private volatile Method isKeyDownMethod;
    private volatile Field optionsField;
    private volatile Field keyMappingsField;
    private volatile Field guiField;

    @Override
    public boolean isAvailable()
    {
        Boolean cached = available;
        if (cached != null)
        {
            return cached;
        }
        synchronized (this)
        {
            if (available != null)
            {
                return available;
            }
            for (ClassLoader candidate : candidateLoaders())
            {
                try
                {
                    Class<?> type = Class.forName(MINECRAFT_CLASS, false, candidate);
                    minecraftClass = type;
                    gameLoader = candidate;
                    available = Boolean.TRUE;
                    return true;
                }
                catch (ClassNotFoundException | LinkageError | RuntimeException ignored)
                {
                }
            }
            available = Boolean.FALSE;
            return false;
        }
    }

    @Override
    public boolean canDetectClientThread()
    {
        if (!isAvailable())
        {
            return false;
        }
        try
        {
            Class<?> type = minecraftClass;
            return type != null && type.getMethod("isSameThread") != null;
        }
        catch (Throwable failure)
        {
            logFailure("canDetectClientThread", failure);
            return false;
        }
    }

    @Override
    public void assertClientThread(String operation)
    {
        Object instance = requireMinecraft(operation);
        try
        {
            Method method = sameThreadMethod;
            if (method == null)
            {
                method = instance.getClass().getMethod("isSameThread");
                sameThreadMethod = method;
            }
            Object result = method.invoke(instance);
            if (!Boolean.TRUE.equals(result))
            {
                throw new ClientThreadException("not on the client thread; operation="
                        + operation);
            }
        }
        catch (ClientThreadException failure)
        {
            throw failure;
        }
        catch (Throwable failure)
        {
            throw new ClientThreadException("cannot detect the client thread; operation="
                    + operation, unwrap(failure));
        }
    }

    @Override
    public Object minecraft()
    {
        try
        {
            return requireMinecraft("minecraft");
        }
        catch (Throwable failure)
        {
            logFailure("minecraft", failure);
            return null;
        }
    }

    @Override
    public Object currentScreen()
    {
        try
        {
            Object instance = requireMinecraft("currentScreen");
            Object gui = readGui(instance);
            if (gui != null)
            {
                try
                {
                    return gui.getClass().getMethod("screen").invoke(gui);
                }
                catch (NoSuchMethodException ignored)
                {
                }
                catch (Throwable failure)
                {
                    logFailure("currentScreen.screen", failure);
                }
            }
            Field field = findField(instance.getClass(), "screen");
            if (field != null && field.trySetAccessible())
            {
                return field.get(instance);
            }
            return null;
        }
        catch (Throwable failure)
        {
            logFailure("currentScreen", failure);
            return null;
        }
    }

    @Override
    public Object createKeyMapping(KeyMappingSpec spec)
    {
        Objects.requireNonNull(spec, "spec");
        try
        {
            Class<?> typeClass = loadClass(INPUT_TYPE_CLASS, "createKeyMapping");
            Class<?> categoryClass = loadClass(KEY_MAPPING_CATEGORY_CLASS, "createKeyMapping");
            Class<?> keyMappingClass = loadClass(KEY_MAPPING_CLASS, "createKeyMapping");
            Object inputType = typeClass.getField(spec.mouse() ? "MOUSE" : "KEYBOARD").get(null);
            Object category = category(spec);
            Constructor<?> constructor = keyMappingClass.getConstructor(String.class, typeClass,
                    int.class, categoryClass, int.class);
            Object mapping = constructor.newInstance(spec.translationKey(), inputType,
                    spec.keyCode(), category, spec.order());
            appendToOptions(mapping);
            return mapping;
        }
        catch (ClientException failure)
        {
            throw failure;
        }
        catch (Throwable failure)
        {
            throw new ClientException("failed to create key mapping id=" + spec.id()
                    + " mod=" + spec.modId(), unwrap(failure));
        }
    }

    @Override
    public void removeKeyMapping(Object mapping)
    {
        if (mapping == null)
        {
            return;
        }
        try
        {
            removeFromOptions(mapping);
        }
        catch (Throwable failure)
        {
            logFailure("removeKeyMapping.options", failure);
        }
        try
        {
            removeFromStaticMaps(mapping);
        }
        catch (Throwable failure)
        {
            logFailure("removeKeyMapping.staticMaps", failure);
        }
        try
        {
            Class<?> keyMappingClass = loadClass(KEY_MAPPING_CLASS, "removeKeyMapping");
            keyMappingClass.getMethod("resetMapping").invoke(null);
        }
        catch (Throwable failure)
        {
            logFailure("removeKeyMapping.reset", failure);
        }
    }

    @Override
    public boolean consumeClick(Object mapping)
    {
        if (mapping == null)
        {
            return false;
        }
        try
        {
            appendToOptions(mapping);
            Method method = consumeClickMethods.get(mapping.getClass());
            if (method == null)
            {
                method = mapping.getClass().getMethod("consumeClick");
                consumeClickMethods.put(mapping.getClass(), method);
            }
            return Boolean.TRUE.equals(method.invoke(mapping));
        }
        catch (Throwable failure)
        {
            logFailure("consumeClick", failure);
            return false;
        }
    }

    @Override
    public int currentModifiers()
    {
        try
        {
            Class<?> constants = loadClass(INPUT_CONSTANTS_CLASS, "currentModifiers");
            Method keyDown = isKeyDownMethod;
            if (keyDown == null)
            {
                keyDown = constants.getMethod("isKeyDown", int.class);
                isKeyDownMethod = keyDown;
            }
            int modifiers = 0;
            if (keyDown(constants, keyDown, "KEY_LSHIFT")
                    || keyDown(constants, keyDown, "KEY_RSHIFT"))
            {
                modifiers |= constant(constants, "MOD_SHIFT");
            }
            if (keyDown(constants, keyDown, "KEY_LCONTROL")
                    || keyDown(constants, keyDown, "KEY_RCONTROL"))
            {
                modifiers |= constant(constants, "MOD_CONTROL");
            }
            if (keyDown(constants, keyDown, "KEY_LALT")
                    || keyDown(constants, keyDown, "KEY_RALT"))
            {
                modifiers |= constant(constants, "MOD_ALT");
            }
            if (keyDown(constants, keyDown, "KEY_LGUI")
                    || keyDown(constants, keyDown, "KEY_RGUI"))
            {
                modifiers |= constant(constants, "MOD_SUPER");
            }
            return modifiers;
        }
        catch (Throwable failure)
        {
            logFailure("currentModifiers", failure);
            return 0;
        }
    }

    @Override
    public void openScreen(Object screen)
    {
        if (screen == null)
        {
            return;
        }
        try
        {
            Object instance = requireMinecraft("openScreen");
            Method method = openScreenMethods.get(screen.getClass());
            if (method == null)
            {
                method = findScreenMethod(instance.getClass(), screen.getClass());
                openScreenMethods.put(screen.getClass(), method);
            }
            method.invoke(instance, screen);
        }
        catch (Throwable failure)
        {
            logFailure("openScreen", failure);
        }
    }

    @Override
    public int guiWidth(Object graphics)
    {
        return guiDimension(graphics, "guiWidth", "getGuiScaledWidth");
    }

    @Override
    public int guiHeight(Object graphics)
    {
        return guiDimension(graphics, "guiHeight", "getGuiScaledHeight");
    }

    @Override
    public Object literal(String text)
    {
        Objects.requireNonNull(text, "text");
        try
        {
            Method method = literalMethod;
            if (method == null)
            {
                Class<?> componentClass = loadClass(COMPONENT_CLASS, "literal");
                method = componentClass.getMethod("literal", String.class);
                literalMethod = method;
            }
            return method.invoke(null, text);
        }
        catch (Throwable failure)
        {
            logFailure("literal", failure);
            return null;
        }
    }

    private static boolean keyDown(Class<?> constants, Method keyDown, String field)
            throws ReflectiveOperationException
    {
        Object value = constants.getField(field).get(null);
        if (!(value instanceof Integer code))
        {
            return false;
        }
        return Boolean.TRUE.equals(keyDown.invoke(null, code));
    }

    private static int constant(Class<?> constants, String field)
            throws ReflectiveOperationException
    {
        Object value = constants.getField(field).get(null);
        return value instanceof Integer number ? number : 0;
    }

    private Object category(KeyMappingSpec spec) throws ReflectiveOperationException
    {
        boolean misc = MISC_CATEGORY.equalsIgnoreCase(spec.categoryId());
        String cacheKey = misc ? MISC_CATEGORY : spec.modId() + ":" + spec.categoryId();
        Object cached = categories.get(cacheKey);
        if (cached != null)
        {
            return cached;
        }
        Class<?> categoryClass = loadClass(KEY_MAPPING_CATEGORY_CLASS,
                "createKeyMapping.category");
        Object category;
        if (misc)
        {
            category = categoryClass.getField("MISC").get(null);
        }
        else
        {
            Class<?> identifierClass = loadClass(IDENTIFIER_CLASS, "createKeyMapping.category");
            Method fromNamespaceAndPath = identifierClass.getMethod("fromNamespaceAndPath",
                    String.class, String.class);
            Object identifier = fromNamespaceAndPath.invoke(null, spec.modId(),
                    spec.categoryId());
            Method register = categoryClass.getMethod("register", identifierClass);
            category = register.invoke(null, identifier);
        }
        if (category == null)
        {
            throw new ClientException("key mapping category is null for id="
                    + spec.categoryId());
        }
        categories.put(cacheKey, category);
        return category;
    }

    private void appendToOptions(Object mapping)
    {
        if (mapping == null || optionMappings.contains(mapping))
        {
            return;
        }
        try
        {
            Object instance = requireMinecraft("createKeyMapping.options");
            Object options = readOptions(instance);
            if (options == null)
            {
                return;
            }
            Field field = keyMappingsField;
            if (field == null)
            {
                field = findField(options.getClass(), "keyMappings");
                if (field == null)
                {
                    logFailure("options.keyMappings", new NoSuchFieldException("keyMappings"));
                    return;
                }
                keyMappingsField = field;
            }
            Object current = field.get(options);
            if (!(current instanceof Object[] array))
            {
                logFailure("options.keyMappings", new IllegalStateException("not an array"));
                return;
            }
            for (Object existing : array)
            {
                if (existing == mapping)
                {
                    optionMappings.add(mapping);
                    return;
                }
            }
            Class<?> component = array.getClass().getComponentType();
            Object extended = Array.newInstance(component, array.length + 1);
            System.arraycopy(array, 0, extended, 0, array.length);
            Array.set(extended, array.length, mapping);
            if (!field.trySetAccessible())
            {
                logFailure("options.keyMappings",
                        new IllegalAccessException("field is not accessible"));
                return;
            }
            field.set(options, extended);
            optionMappings.add(mapping);
        }
        catch (Throwable failure)
        {
            logFailure("options.keyMappings", failure);
        }
    }

    private void removeFromOptions(Object mapping) throws ReflectiveOperationException
    {
        Object instance = requireMinecraft("removeKeyMapping");
        Object options = readOptions(instance);
        if (options == null)
        {
            return;
        }
        Field field = keyMappingsField;
        if (field == null)
        {
            field = findField(options.getClass(), "keyMappings");
            if (field == null)
            {
                return;
            }
            keyMappingsField = field;
        }
        Object current = field.get(options);
        if (!(current instanceof Object[] array))
        {
            return;
        }
        int index = -1;
        for (int i = 0; i < array.length; i++)
        {
            if (array[i] == mapping)
            {
                index = i;
                break;
            }
        }
        if (index < 0)
        {
            return;
        }
        Class<?> component = array.getClass().getComponentType();
        Object reduced = Array.newInstance(component, array.length - 1);
        System.arraycopy(array, 0, reduced, 0, index);
        System.arraycopy(array, index + 1, reduced, index, array.length - index - 1);
        if (!field.trySetAccessible())
        {
            logFailure("removeKeyMapping.options",
                    new IllegalAccessException("field is not accessible"));
            return;
        }
        field.set(options, reduced);
        optionMappings.remove(mapping);
    }

    private void removeFromStaticMaps(Object mapping) throws ReflectiveOperationException
    {
        Class<?> keyMappingClass = loadClass(KEY_MAPPING_CLASS, "removeKeyMapping");
        for (String fieldName : KEY_MAPPING_MAP_FIELDS)
        {
            try
            {
                Field field = keyMappingClass.getDeclaredField(fieldName);
                if (!field.trySetAccessible())
                {
                    continue;
                }
                Object value = field.get(null);
                if (!(value instanceof Map<?, ?> map))
                {
                    continue;
                }
                Iterator<?> iterator = map.entrySet().iterator();
                while (iterator.hasNext())
                {
                    Map.Entry<?, ?> entry = (Map.Entry<?, ?>) iterator.next();
                    Object entryValue = entry.getValue();
                    if (entryValue == mapping)
                    {
                        iterator.remove();
                        continue;
                    }
                    if (entryValue instanceof List<?> list)
                    {
                        list.removeIf(candidate -> candidate == mapping);
                        if (list.isEmpty())
                        {
                            iterator.remove();
                        }
                    }
                }
            }
            catch (NoSuchFieldException ignored)
            {
            }
            catch (Throwable failure)
            {
                logFailure("removeKeyMapping." + fieldName, failure);
            }
        }
    }

    private Object readGui(Object instance) throws ReflectiveOperationException
    {
        Field field = guiField;
        if (field == null)
        {
            field = findField(instance.getClass(), "gui");
            if (field == null)
            {
                return null;
            }
            guiField = field;
        }
        if (!field.trySetAccessible())
        {
            return null;
        }
        return field.get(instance);
    }

    private Object readOptions(Object instance) throws ReflectiveOperationException
    {
        Field field = optionsField;
        if (field == null)
        {
            field = findField(instance.getClass(), "options");
            if (field == null)
            {
                return null;
            }
            optionsField = field;
        }
        if (!field.trySetAccessible())
        {
            return null;
        }
        return field.get(instance);
    }

    private int guiDimension(Object graphics, String graphicsMethod, String windowMethod)
    {
        if (graphics != null)
        {
            try
            {
                Method method = graphics.getClass().getMethod(graphicsMethod);
                Object result = method.invoke(graphics);
                if (result instanceof Integer value)
                {
                    return value;
                }
            }
            catch (NoSuchMethodException ignored)
            {
            }
            catch (Throwable failure)
            {
                logFailure(graphicsMethod, failure);
            }
        }
        try
        {
            Object instance = requireMinecraft(graphicsMethod);
            Object window = instance.getClass().getMethod("getWindow").invoke(instance);
            if (window != null)
            {
                Object result = window.getClass().getMethod(windowMethod).invoke(window);
                if (result instanceof Integer value)
                {
                    return value;
                }
            }
        }
        catch (Throwable failure)
        {
            logFailure(windowMethod, failure);
        }
        return 0;
    }

    private Object requireMinecraft(String operation)
    {
        Class<?> type = requireMinecraftClass(operation);
        try
        {
            Method method = minecraftGetInstance;
            if (method == null)
            {
                method = type.getMethod("getInstance");
                minecraftGetInstance = method;
            }
            Object instance = method.invoke(null);
            if (instance == null)
            {
                throw new ClientThreadException("Minecraft.getInstance() returned null; "
                        + "operation=" + operation);
            }
            return instance;
        }
        catch (ClientThreadException failure)
        {
            throw failure;
        }
        catch (Throwable failure)
        {
            throw new ClientThreadException("cannot obtain the client instance; operation="
                    + operation, unwrap(failure));
        }
    }

    private Class<?> requireMinecraftClass(String operation)
    {
        if (!isAvailable())
        {
            throw new ClientThreadException("client is unavailable; operation=" + operation);
        }
        Class<?> type = minecraftClass;
        if (type == null)
        {
            throw new ClientThreadException("client class is unavailable; operation="
                    + operation);
        }
        return type;
    }

    private Class<?> loadClass(String name, String operation) throws ClassNotFoundException
    {
        if (!isAvailable())
        {
            throw new ClientException("client is unavailable; operation=" + operation);
        }
        ClassLoader loader = gameLoader;
        if (loader == null)
        {
            loader = ReflectiveClientGameAdapter.class.getClassLoader();
        }
        if (loader == null)
        {
            loader = ClassLoader.getSystemClassLoader();
        }
        return Class.forName(name, true, loader);
    }

    private static Method findScreenMethod(Class<?> clientClass, Class<?> screenClass)
            throws NoSuchMethodException
    {
        for (Method method : clientClass.getMethods())
        {
            if (!method.getName().equals("setScreenAndShow") || method.getParameterCount() != 1)
            {
                continue;
            }
            if (method.getParameterTypes()[0].isAssignableFrom(screenClass))
            {
                return method;
            }
        }
        throw new NoSuchMethodException("setScreenAndShow(" + screenClass.getName() + ")");
    }

    private static Field findField(Class<?> type, String name)
    {
        for (Class<?> current = type; current != null && current != Object.class;
             current = current.getSuperclass())
        {
            try
            {
                return current.getDeclaredField(name);
            }
            catch (NoSuchFieldException ignored)
            {
            }
        }
        return null;
    }

    private static List<ClassLoader> candidateLoaders()
    {
        List<ClassLoader> loaders = new ArrayList<>(3);
        addLoader(loaders, ReflectiveClientGameAdapter.class.getClassLoader());
        addLoader(loaders, Thread.currentThread().getContextClassLoader());
        addLoader(loaders, ClassLoader.getSystemClassLoader());
        return loaders;
    }

    private static void addLoader(List<ClassLoader> loaders, ClassLoader loader)
    {
        if (loader != null && !loaders.contains(loader))
        {
            loaders.add(loader);
        }
    }

    private void logFailure(String operation, Throwable failure)
    {
        if (loggedFailures.add(operation))
        {
            logLine("[weaver] CLIENT ADAPTER " + operation + " FAILED reason="
                    + describe(failure));
        }
    }

    private static void logLine(String message)
    {
        try
        {
            LifecycleHooks.logLine(message);
        }
        catch (Throwable ignored)
        {
        }
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
        Throwable cause = unwrap(failure);
        if (cause == null)
        {
            return "unknown";
        }
        String message = cause.getMessage();
        return message != null ? message : cause.getClass().getName();
    }
}
