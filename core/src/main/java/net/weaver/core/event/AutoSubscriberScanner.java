package net.weaver.core.event;

import net.weaver.api.event.EventBus;
import net.weaver.api.event.EventBusSubscriber;
import net.weaver.api.event.EventFilter;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.event.Side;
import net.weaver.api.event.SubscribeEvent;
import net.weaver.core.ModJar;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Enumeration;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class AutoSubscriberScanner
{
    private static final String SUBSCRIBER_DESCRIPTOR = Type.getDescriptor(EventBusSubscriber.class);
    private static final String SUBSCRIBE_DESCRIPTOR = Type.getDescriptor(SubscribeEvent.class);

    private AutoSubscriberScanner()
    {
    }

    public static int scan(ModJar mod, String processSide, ClassLoader loader, Consumer<String> log)
    {
        return scan(mod, processSide, loader, log, null);
    }

    public static int scan(ModJar mod, String processSide, ClassLoader loader, Consumer<String> log,
                           Class<?> entrypoint)
    {
        boolean clientAvailable = "client".equalsIgnoreCase(processSide)
                || "both".equalsIgnoreCase(processSide);
        boolean serverAvailable = "server".equalsIgnoreCase(processSide)
                || "both".equalsIgnoreCase(processSide);
        return scan(mod, processSide, clientAvailable, serverAvailable, loader, log, entrypoint,
                EventBus.MOD);
    }

    /**
     * Scans a mod jar for {@link EventBusSubscriber} classes.
     *
     * <p>{@code processSide} filters the physical {@link Side} declared by the
     * annotation. {@code clientAvailable} and {@code serverAvailable} filter the
     * annotation's {@link LogicalSide}: a {@code CLIENT} subscriber requires
     * {@code clientAvailable}, a {@code SERVER} subscriber requires
     * {@code serverAvailable}, and {@code BOTH} never filters. The two
     * availability flags are independent so a client with an integrated server
     * can load logical server subscribers.
     *
     * <p>Subscribers declared with {@link EventBusSubscriber.Bus#MOD} are
     * registered on {@link EventBus#MOD}; use the overload that accepts a local
     * bus to register them on a specific mod's local bus.
     */
    public static int scan(ModJar mod, String processSide, boolean clientAvailable,
                           boolean serverAvailable, ClassLoader loader, Consumer<String> log,
                           Class<?> entrypoint)
    {
        return scan(mod, processSide, clientAvailable, serverAvailable, loader, log, entrypoint,
                EventBus.MOD);
    }

    /**
     * Scans a mod jar for {@link EventBusSubscriber} classes and registers them
     * on the bus selected by the annotation.
     *
     * <p>{@link EventBusSubscriber.Bus#MOD} maps to {@code localBus},
     * {@link EventBusSubscriber.Bus#GLOBAL_MOD} maps to {@link EventBus#MOD},
     * and {@link EventBusSubscriber.Bus#GAME} maps to {@link EventBus#GAME}.
     * A subscriber whose declared {@link LogicalSide} is not {@code BOTH} is
     * registered with a dispatch-time side filter, so it only receives events
     * with a matching logical side even though the bus itself carries both.
     *
     * <p>{@code processSide} filters the physical {@link Side} declared by the
     * annotation. {@code clientAvailable} and {@code serverAvailable} filter the
     * annotation's {@link LogicalSide}: a {@code CLIENT} subscriber requires
     * {@code clientAvailable}, a {@code SERVER} subscriber requires
     * {@code serverAvailable}, and {@code BOTH} never filters.
     *
     * @param mod              the mod jar to scan
     * @param processSide      the physical process side
     * @param clientAvailable  whether the logical client is hosted
     * @param serverAvailable  whether the logical server is hosted
     * @param loader           the class loader used to resolve discovered classes
     * @param log              the diagnostic sink
     * @param entrypoint       the entrypoint class registered on the game bus, or {@code null}
     * @param localBus         the mod-local bus used for {@code Bus.MOD}, not {@code null}
     * @return the number of subscriber classes registered
     */
    public static int scan(ModJar mod, String processSide, boolean clientAvailable,
                           boolean serverAvailable, ClassLoader loader, Consumer<String> log,
                           Class<?> entrypoint, EventBus localBus)
    {
        Objects.requireNonNull(mod, "mod");
        Objects.requireNonNull(loader, "loader");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(localBus, "localBus");
        String entrypointName = entrypoint == null ? null : entrypoint.getName();
        int registered = 0;
        JarFile jar = mod.jarFile();
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements())
        {
            JarEntry entry = entries.nextElement();
            String entryName = entry.getName();
            if (entry.isDirectory() || !entryName.endsWith(".class") || entryName.startsWith("META-INF/"))
            {
                continue;
            }
            try
            {
                String className = entryName.substring(0, entryName.length() - ".class".length())
                        .replace('/', '.');
                Probe probe = inspect(jar, entry);
                if (className.equals(entrypointName))
                {
                    if (probe.subscriber)
                    {
                        log.accept("[weaver] SUBSCRIBER ENTRYPOINT SKIPPED class=" + className
                                + " reason=already-registered-on-game-bus");
                    }
                    continue;
                }
                if (!probe.subscriber || !matchesProcess(probe.side, processSide)
                        || !matchesLogical(probe.logicalSide, clientAvailable, serverAvailable))
                {
                    continue;
                }
                if (!probe.modId.isEmpty() && !probe.modId.equals(mod.metadata().id()))
                {
                    continue;
                }
                Class<?> type = Class.forName(className, false, loader);
                EventBus bus = switch (probe.bus)
                {
                    case MOD -> localBus;
                    case GLOBAL_MOD -> EventBus.MOD;
                    case GAME -> EventBus.GAME;
                };
                EventFilter filter = probe.logicalSide == LogicalSide.BOTH
                        ? EventFilter.all()
                        : EventFilter.logicalSide(probe.logicalSide);
                register(bus, type, probe.instanceMethods, filter);
                registered++;
            }
            catch (Throwable failure)
            {
                log.accept("[weaver] SUBSCRIBER SCAN FAILED entry=" + entryName
                        + " reason=" + failure);
            }
        }
        return registered;
    }

    private static void register(EventBus bus, Class<?> type, boolean instanceMethods,
                                 EventFilter filter)
            throws ReflectiveOperationException
    {
        if (instanceMethods || hasInstanceSubscriberMethods(type))
        {
            bus.register(instantiate(type), filter);
            return;
        }
        bus.register(type, filter);
    }

    private static boolean hasInstanceSubscriberMethods(Class<?> type)
    {
        for (Class<?> current = type; current != null && current != Object.class;
             current = current.getSuperclass())
        {
            for (Method method : current.getDeclaredMethods())
            {
                if (method.isBridge() || method.isSynthetic())
                {
                    continue;
                }
                if (method.isAnnotationPresent(SubscribeEvent.class)
                        && !Modifier.isStatic(method.getModifiers()))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private static Object instantiate(Class<?> type) throws ReflectiveOperationException
    {
        Constructor<?> constructor = type.getDeclaredConstructor();
        if (!constructor.trySetAccessible())
        {
            throw new IllegalStateException("Cannot access no-arg constructor of " + type.getName());
        }
        return constructor.newInstance();
    }

    private static Probe inspect(JarFile jar, JarEntry entry) throws IOException
    {
        try (InputStream input = jar.getInputStream(entry))
        {
            Probe probe = new Probe();
            ClassReader reader = new ClassReader(input.readAllBytes());
            reader.accept(probe, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return probe;
        }
    }

    private static boolean matchesProcess(Side declared, String processSide)
    {
        if (declared == Side.BOTH || "both".equalsIgnoreCase(processSide))
        {
            return true;
        }
        if ("client".equalsIgnoreCase(processSide))
        {
            return declared == Side.CLIENT;
        }
        if ("server".equalsIgnoreCase(processSide))
        {
            return declared == Side.SERVER;
        }
        return false;
    }

    private static boolean matchesLogical(LogicalSide declared, boolean clientAvailable,
                                          boolean serverAvailable)
    {
        return switch (declared)
        {
            case CLIENT -> clientAvailable;
            case SERVER -> serverAvailable;
            case BOTH -> true;
        };
    }

    private static final class Probe extends ClassVisitor
    {
        private boolean subscriber;
        private EventBusSubscriber.Bus bus = EventBusSubscriber.Bus.GAME;
        private Side side = Side.BOTH;
        private LogicalSide logicalSide = LogicalSide.BOTH;
        private String modId = "";
        private boolean instanceMethods;

        private Probe()
        {
            super(Opcodes.ASM9);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible)
        {
            if (!SUBSCRIBER_DESCRIPTOR.equals(descriptor))
            {
                return null;
            }
            subscriber = true;
            return new AnnotationVisitor(Opcodes.ASM9)
            {
                @Override
                public void visit(String name, Object value)
                {
                    if ("modId".equals(name) && value instanceof String text)
                    {
                        modId = text;
                    }
                }

                @Override
                public void visitEnum(String name, String enumDescriptor, String value)
                {
                    if ("bus".equals(name))
                    {
                        bus = EventBusSubscriber.Bus.valueOf(value);
                    }
                    else if ("side".equals(name))
                    {
                        side = Side.valueOf(value);
                    }
                    else if ("logicalSide".equals(name))
                    {
                        logicalSide = LogicalSide.valueOf(value);
                    }
                }
            };
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions)
        {
            if ((access & (Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC)) != 0)
            {
                return null;
            }
            boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
            return new MethodVisitor(Opcodes.ASM9)
            {
                @Override
                public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible)
                {
                    if (!isStatic && SUBSCRIBE_DESCRIPTOR.equals(annotationDescriptor))
                    {
                        instanceMethods = true;
                    }
                    return null;
                }
            };
        }
    }
}
