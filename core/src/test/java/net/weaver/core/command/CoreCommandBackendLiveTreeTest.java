package net.weaver.core.command;

import net.weaver.api.command.CommandRegistration;
import net.weaver.api.command.CommandRegistry;
import net.weaver.api.command.CommandSpec;
import net.weaver.api.command.PermissionService;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.events.CommandRegisterEvent;
import net.weaver.api.event.events.ServerTickEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreCommandBackendLiveTreeTest
{
    private URLClassLoader loader;
    private Object dispatcher;
    private CoreCommandBackend backend;

    @BeforeEach
    void setUp() throws Exception
    {
        Path jar = findBrigadierJar();
        Assumptions.assumeTrue(jar != null, "Brigadier jar is not available");
        loader = new URLClassLoader(new URL[]{jar.toUri().toURL()},
                ClassLoader.getPlatformClassLoader());
        dispatcher = newDispatcher();
        backend = new CoreCommandBackend(new BrigadierRegistrar(loader));
        backend.install();
        PermissionService.reset();
        CommandRegistry.resetTreeSnapshot();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        if (backend != null)
        {
            backend.uninstall();
            backend = null;
        }
        EventBus.GAME.unregister(this);
        PermissionService.reset();
        CommandRegistry.reset();
        CommandRegistry.resetTreeSnapshot();
        if (loader != null)
        {
            loader.close();
            loader = null;
        }
    }

    @Test
    void unregisterRemovesTheLiveRootAndAliases() throws Exception
    {
        CommandSpec spec = CommandSpec.command("weaverlive")
                .alias("wl")
                .executor(context -> 1)
                .build();
        CommandRegistration registration = backend.register(spec);
        EventBus.GAME.post(new CommandRegisterEvent(new FakeCommands(dispatcher)));

        assertNotNull(rootChild("weaverlive"));
        assertNotNull(rootChild("wl"));
        assertEquals(1, execute("weaverlive", "source"));

        registration.unregister();

        assertNull(rootChild("weaverlive"));
        assertNull(rootChild("wl"));
        assertTrue(backend.commandNames().isEmpty());
        assertThrows(Throwable.class, () -> execute("weaverlive", "source"));
    }

    @Test
    void reRegisteringTheSameNameAfterRemovalWorks() throws Exception
    {
        CommandRegistration first = backend.register(CommandSpec.command("weaverlive")
                .executor(context -> 1)
                .build());
        EventBus.GAME.post(new CommandRegisterEvent(new FakeCommands(dispatcher)));
        assertEquals(1, execute("weaverlive", "source"));

        first.unregister();
        assertNull(rootChild("weaverlive"));

        CommandRegistration second = backend.register(CommandSpec.command("weaverlive")
                .executor(context -> 2)
                .build());
        EventBus.GAME.post(new ServerTickEvent.Pre(1L));

        assertNotNull(rootChild("weaverlive"));
        assertEquals(2, execute("weaverlive", "source"));
        assertEquals(List.of("weaverlive"), backend.commandNames());
        assertTrue(second.isActive());
    }

    private Object rootChild(String name) throws Exception
    {
        Object root = dispatcherType().getMethod("getRoot").invoke(dispatcher);
        return root.getClass().getMethod("getChild", String.class).invoke(root, name);
    }

    private Object newDispatcher() throws Exception
    {
        return dispatcherType().getDeclaredConstructor().newInstance();
    }

    private Class<?> dispatcherType() throws ClassNotFoundException
    {
        return Class.forName("com.mojang.brigadier.CommandDispatcher", true, loader);
    }

    private int execute(String input, Object source) throws Exception
    {
        Method execute = dispatcherType().getMethod("execute", String.class, Object.class);
        return (Integer) execute.invoke(dispatcher, input, source);
    }

    private static Path findBrigadierJar()
    {
        Path gameDir = resolveGameDir();
        if (gameDir == null)
        {
            return null;
        }
        Path root = gameDir.resolve("libraries").resolve("com").resolve("mojang")
                .resolve("brigadier");
        if (!Files.isDirectory(root))
        {
            return null;
        }
        try (Stream<Path> paths = Files.walk(root))
        {
            return paths.filter(Files::isRegularFile)
                    .filter(path ->
                    {
                        String name = path.getFileName().toString();
                        return name.startsWith("brigadier-") && name.endsWith(".jar");
                    })
                    .sorted()
                    .findFirst()
                    .orElse(null);
        }
        catch (IOException failure)
        {
            return null;
        }
    }

    private static Path resolveGameDir()
    {
        String configured = System.getenv("WEAVER_GAME_DIR");
        if (configured != null && !configured.isBlank())
        {
            Path path = Path.of(configured).toAbsolutePath();
            Path nested = path.resolve("work").resolve("game-26.3");
            if (Files.isDirectory(nested))
            {
                return nested;
            }
            if (Files.isDirectory(path))
            {
                return path;
            }
        }
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null)
        {
            Path candidate = current.resolve("work").resolve("game-26.3");
            if (Files.isDirectory(candidate))
            {
                return candidate;
            }
            Path direct = current.resolve("game-26.3");
            if (Files.isDirectory(direct.resolve("libraries")))
            {
                return direct;
            }
            current = current.getParent();
        }
        return null;
    }
}
