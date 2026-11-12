package net.weaver.core.command;

import net.weaver.api.command.CommandArgumentType;
import net.weaver.api.command.CommandContext;
import net.weaver.api.command.CommandNodeSpec;
import net.weaver.api.command.CommandRegistrationException;
import net.weaver.api.command.CommandRegistry;
import net.weaver.api.command.CommandSpec;
import net.weaver.api.command.PermissionNode;
import net.weaver.api.command.PermissionPolicy;
import net.weaver.api.command.PermissionService;
import net.weaver.api.event.EventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrigadierRegistrarTest
{
    private URLClassLoader loader;
    private Object dispatcher;
    private BrigadierRegistrar registrar;

    @BeforeEach
    void setUp() throws Exception
    {
        Path jar = findBrigadierJar();
        Assumptions.assumeTrue(jar != null, "Brigadier jar is not available");
        loader = new URLClassLoader(new URL[]{jar.toUri().toURL()},
                ClassLoader.getPlatformClassLoader());
        dispatcher = newDispatcher();
        registrar = new BrigadierRegistrar(loader);
        PermissionService.reset();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PermissionService.reset();
        CommandRegistry.reset();
        CommandRegistry.resetTreeSnapshot();
        EventBus.GAME.unregister(this);
        if (loader != null)
        {
            loader.close();
            loader = null;
        }
    }

    @Test
    void executesRootLiteralChildAndIntegerArgument() throws Exception
    {
        AtomicInteger rootRuns = new AtomicInteger();
        AtomicInteger statusRuns = new AtomicInteger();
        AtomicReference<CommandContext> seen = new AtomicReference<>();
        CommandSpec spec = CommandSpec.command("proof")
                .executor(context ->
                {
                    rootRuns.incrementAndGet();
                    return 1;
                })
                .child(CommandNodeSpec.literal("status")
                        .executor(context ->
                        {
                            statusRuns.incrementAndGet();
                            return 1;
                        })
                        .child(CommandNodeSpec.argument("value", CommandArgumentType.INTEGER)
                                .executor(context ->
                                {
                                    seen.set(context);
                                    return 7;
                                })
                                .build())
                        .build())
                .build();

        Object root = registrar.register(dispatcher, spec);
        Object source = "source";
        int result = execute("proof status 7", source);

        assertNotNull(root);
        assertEquals(7, result);
        assertEquals(0, rootRuns.get());
        assertEquals(0, statusRuns.get());
        assertNotNull(seen.get());
        assertSame(source, seen.get().source().raw());
        assertEquals("String", seen.get().source().name());
        assertEquals(7, seen.get().intArgument("value"));
        assertEquals("proof status 7", seen.get().input());
        assertEquals(List.of("proof"), registrar.commandNames(dispatcher));
    }

    @Test
    void aliasExecutesTheSameRootExecutor() throws Exception
    {
        AtomicInteger runs = new AtomicInteger();
        CommandSpec spec = CommandSpec.command("proof")
                .alias("p")
                .executor(context ->
                {
                    runs.incrementAndGet();
                    return 1;
                })
                .build();
        registrar.register(dispatcher, spec);

        assertEquals(1, execute("proof", "source"));
        assertEquals(1, execute("p", "source"));
        assertEquals(2, runs.get());
    }

    @Test
    void siblingRedirectExecutesTheTargetExecutor() throws Exception
    {
        AtomicInteger statusRuns = new AtomicInteger();
        CommandSpec spec = CommandSpec.command("proof")
                .child(CommandNodeSpec.literal("status")
                        .executor(context ->
                        {
                            statusRuns.incrementAndGet();
                            return 1;
                        })
                        .build())
                .child(CommandNodeSpec.literal("alias").redirect("status").build())
                .build();
        registrar.register(dispatcher, spec);

        assertEquals(1, execute("proof alias", "source"));
        assertEquals(1, statusRuns.get());
    }

    @Test
    void permissionedRedirectToUnpermissionedSiblingKeepsItsOwnPermission() throws Exception
    {
        PermissionNode admin = PermissionNode.parse("proof:command/forcekill");
        PermissionService.register(admin, PermissionPolicy.DENY, 0);
        AtomicInteger killRuns = new AtomicInteger();
        CommandSpec spec = CommandSpec.command("proof")
                .child(CommandNodeSpec.literal("kill")
                        .executor(context ->
                        {
                            killRuns.incrementAndGet();
                            return 1;
                        })
                        .build())
                .child(CommandNodeSpec.literal("forcekill")
                        .permission(admin)
                        .redirect("kill")
                        .build())
                .build();
        registrar.register(dispatcher, spec);
        Object anyone = new Object();
        Object adminSource = new Object();
        PermissionService.allow(adminSource, admin);

        assertEquals(1, execute("proof kill", anyone));
        assertThrows(Throwable.class, () -> execute("proof forcekill", anyone));
        assertEquals(1, killRuns.get());

        assertEquals(1, execute("proof forcekill", adminSource));
        assertEquals(2, killRuns.get());
    }

    @Test
    void permissionedRedirectAlsoRequiresTheTargetPermission() throws Exception
    {
        PermissionNode own = PermissionNode.parse("proof:command/short");
        PermissionNode target = PermissionNode.parse("proof:command/long");
        PermissionService.register(own, PermissionPolicy.DENY, 0);
        PermissionService.register(target, PermissionPolicy.DENY, 0);
        CommandSpec spec = CommandSpec.command("proof")
                .child(CommandNodeSpec.literal("long")
                        .permission(target)
                        .executor(context -> 1)
                        .build())
                .child(CommandNodeSpec.literal("short")
                        .permission(own)
                        .redirect("long")
                        .build())
                .build();
        registrar.register(dispatcher, spec);
        Object onlyOwn = new Object();
        PermissionService.allow(onlyOwn, own);
        Object both = new Object();
        PermissionService.allow(both, own);
        PermissionService.allow(both, target);

        assertThrows(Throwable.class, () -> execute("proof short", onlyOwn));
        assertEquals(1, execute("proof short", both));
    }

    @Test
    void argumentSuggestionsReachTheDispatcher() throws Exception
    {
        CommandSpec spec = CommandSpec.command("proof")
                .child(CommandNodeSpec.literal("pick")
                        .child(CommandNodeSpec.argument("name", CommandArgumentType.STRING)
                                .suggests(context -> List.of("alpha", "beta"))
                                .build())
                        .build())
                .build();
        registrar.register(dispatcher, spec);

        List<String> suggestions = suggestions(parse("proof pick ", "source"));

        assertTrue(suggestions.containsAll(List.of("alpha", "beta")), suggestions.toString());
    }

    @Test
    void explicitAllowEnablesAndExplicitDenyBlocksExecution() throws Exception
    {
        PermissionNode node = PermissionNode.parse("proof:command/proof");
        PermissionService.register(node, PermissionPolicy.DENY, 0);
        AtomicInteger runs = new AtomicInteger();
        CommandSpec spec = CommandSpec.command("proof")
                .permission(node)
                .executor(context ->
                {
                    runs.incrementAndGet();
                    return 1;
                })
                .build();
        registrar.register(dispatcher, spec);
        Object source = new Object();

        PermissionService.allow(source, node);
        assertEquals(1, execute("proof", source));
        assertEquals(1, runs.get());

        PermissionService.deny(source, node);
        Throwable failure = assertThrows(Throwable.class, () -> execute("proof", source));
        assertEquals(1, runs.get());
        assertTrue(failure instanceof InvocationTargetException
                || failure.getClass().getName().contains("CommandSyntaxException"));
    }

    @Test
    void opLevelFallbackDeniesAndAllowsBasedOnTheResolver() throws Exception
    {
        FakePermissionResolver resolver = new FakePermissionResolver();
        resolver.setOpLevel(0);
        PermissionService.install(resolver);
        PermissionNode node = PermissionNode.parse("proof:command/proof");
        PermissionService.register(node, PermissionPolicy.DENY, 2);
        AtomicInteger runs = new AtomicInteger();
        CommandSpec spec = CommandSpec.command("proof")
                .permission(node)
                .executor(context ->
                {
                    runs.incrementAndGet();
                    return 1;
                })
                .build();
        registrar.register(dispatcher, spec);
        Object source = new Object();

        assertThrows(Throwable.class, () -> execute("proof", source));
        assertEquals(0, runs.get());

        resolver.setOpLevel(2);
        assertEquals(1, execute("proof", source));
        assertEquals(1, runs.get());
    }

    @Test
    void invalidCustomTypeAndMissingRedirectAreRejected()
    {
        CommandSpec invalidType = CommandSpec.command("bad")
                .child(CommandNodeSpec.argument("value",
                        CommandArgumentType.custom("not-an-argument-type")).build())
                .build();
        CommandSpec missingRedirect = CommandSpec.command("badredirect")
                .child(CommandNodeSpec.literal("alias").redirect("missing").build())
                .build();

        assertThrows(CommandRegistrationException.class,
                () -> registrar.register(dispatcher, invalidType));
        assertThrows(CommandRegistrationException.class,
                () -> registrar.register(dispatcher, missingRedirect));
    }

    @Test
    void existingRootIsSkippedWithoutThrowing() throws Exception
    {
        CommandSpec first = CommandSpec.command("proof").executor(context -> 1).build();
        CommandSpec second = CommandSpec.command("proof").executor(context -> 2).build();
        registrar.register(dispatcher, first);

        Object skipped = registrar.register(dispatcher, second);

        assertNull(skipped);
        assertEquals(1, execute("proof", "source"));
    }

    private Object newDispatcher() throws Exception
    {
        Class<?> type = dispatcherType();
        return type.getDeclaredConstructor().newInstance();
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

    private Object parse(String input, Object source) throws Exception
    {
        Method parse = dispatcherType().getMethod("parse", String.class, Object.class);
        return parse.invoke(dispatcher, input, source);
    }

    private List<String> suggestions(Object parsed) throws Exception
    {
        Class<?> parseResults = Class.forName("com.mojang.brigadier.ParseResults", true, loader);
        Method completion = dispatcherType().getMethod("getCompletionSuggestions", parseResults);
        CompletableFuture<?> future = (CompletableFuture<?>) completion.invoke(dispatcher, parsed);
        Object result = future.get(5L, TimeUnit.SECONDS);
        Class<?> suggestionsType = Class.forName(
                "com.mojang.brigadier.suggestion.Suggestions", true, loader);
        Class<?> suggestionType = Class.forName(
                "com.mojang.brigadier.suggestion.Suggestion", true, loader);
        List<?> items = (List<?>) suggestionsType.getMethod("getList").invoke(result);
        Method text = suggestionType.getMethod("getText");
        List<String> suggestions = new ArrayList<>(items.size());
        for (Object item : items)
        {
            suggestions.add((String) text.invoke(item));
        }
        return suggestions;
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
