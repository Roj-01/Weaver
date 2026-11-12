package net.weaver.api.game;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.dedicated.DedicatedPlayerList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.weaver.api.event.EventBus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sun.misc.Unsafe;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves that a consumer compiled against only the base {@code api} classes -
 * that is, a pre-2.1/base-API mod that has never seen {@code net.weaver.api.game}
 * or {@code net.minecraft} - still links against a typed event at runtime.
 *
 * <p>The fixture is compiled inside this test with {@link JavaCompiler} and a
 * classpath that contains only the located {@code api} main classes or jar. It
 * therefore cannot reference a game or typed class. The compiled fixture is
 * loaded with a {@link URLClassLoader} whose parent is the test classloader, so
 * the frozen base descriptors resolve from {@code api}, exactly as they would
 * for a mod built before the typed artifact existed.
 *
 * <p>The payload instances are created with {@link Unsafe#allocateInstance}
 * solely to provide object identity for the identity assertions. No game state
 * is read or written; the game bootstrap is only needed because allocating an
 * instance initializes the {@code ServerPlayer} class hierarchy.
 */
class LegacyFixtureLinkageTest {

    private static final String FIXTURE_SOURCE = """
            public final class LegacyBaseApiConsumer {

                private net.weaver.api.event.events.PlayerJoinEvent receivedEvent;
                private Object receivedPlayerList;
                private Object receivedPlayer;

                public void register(net.weaver.api.event.EventBus bus) {
                    bus.addListener(net.weaver.api.event.events.PlayerJoinEvent.class, event -> {
                        receivedEvent = event;
                        receivedPlayerList = event.playerList();
                        receivedPlayer = event.player();
                    });
                }

                public Object receivedEvent() {
                    return receivedEvent;
                }

                public Object receivedPlayerList() {
                    return receivedPlayerList;
                }

                public Object receivedPlayer() {
                    return receivedPlayer;
                }
            }
            """;

    @BeforeAll
    static void bootstrapGameClasses() {
        // allocateInstance initializes the ServerPlayer hierarchy, which expects
        // the game bootstrap to have run. This is test-only setup and does not
        // touch world or player state.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void baseOnlyFixtureLinksAgainstTypedEvent(@TempDir Path tempDir) throws Exception {
        Path classesRoot = compileFixture(tempDir);

        ServerPlayer fakePlayer = allocate(ServerPlayer.class);
        // PlayerList is abstract in 26.3; DedicatedPlayerList is its concrete
        // server implementation and still satisfies the PlayerList accessor.
        PlayerList fakePlayerList = allocate(DedicatedPlayerList.class);

        EventBus bus = new EventBus("legacy-fixture");
        AtomicReference<Throwable> listenerFailure = new AtomicReference<>();
        bus.setExceptionHandler((event, listener, failure) -> listenerFailure.set(failure));

        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{classesRoot.toUri().toURL()},
                LegacyFixtureLinkageTest.class.getClassLoader())) {
            Class<?> fixtureType = Class.forName("LegacyBaseApiConsumer", true, loader);
            assertSame(loader, fixtureType.getClassLoader(),
                    "fixture must come from the compiled temp directory, not the test classpath");
            Object fixture = fixtureType.getConstructor().newInstance();
            fixtureType.getMethod("register", EventBus.class).invoke(fixture, bus);

            net.weaver.api.game.event.PlayerJoinEvent typedEvent =
                    (net.weaver.api.game.event.PlayerJoinEvent)
                            new TypedGameEventFactory().playerJoin(fakePlayerList, fakePlayer);

            assertTrue(bus.post(typedEvent), "typed event must not be canceled");
            assertNull(listenerFailure.get(),
                    () -> "base-only fixture listener failed with " + listenerFailure.get());

            Object receivedEvent = fixtureType.getMethod("receivedEvent").invoke(fixture);
            assertInstanceOf(net.weaver.api.game.event.PlayerJoinEvent.class, receivedEvent);
            assertSame(typedEvent, receivedEvent);
            assertSame(fakePlayerList,
                    fixtureType.getMethod("receivedPlayerList").invoke(fixture));
            assertSame(fakePlayer,
                    fixtureType.getMethod("receivedPlayer").invoke(fixture));

            net.weaver.api.event.events.PlayerJoinEvent baseView = typedEvent;
            assertSame(fakePlayerList, baseView.playerList());
            assertSame(fakePlayer, baseView.player());

            assertInstanceOf(ServerPlayer.class, typedEvent.player());
            assertInstanceOf(PlayerList.class, typedEvent.playerList());
            assertSame(fakePlayer, typedEvent.player());
            assertSame(fakePlayerList, typedEvent.playerList());
        }
    }

    private static Path compileFixture(Path tempDir) throws Exception {
        Path sourceRoot = Files.createDirectories(tempDir.resolve("src"));
        Path classesRoot = Files.createDirectories(tempDir.resolve("classes"));
        Path source = sourceRoot.resolve("LegacyBaseApiConsumer.java");
        Files.writeString(source, FIXTURE_SOURCE, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "A JDK compiler is required to compile the legacy fixture");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Path apiClasses = apiClassesLocation();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnostics, null, StandardCharsets.UTF_8)) {
            Boolean compiled = compiler.getTask(
                            null,
                            fileManager,
                            diagnostics,
                            List.of(
                                    "--release", Integer.toString(Runtime.version().feature()),
                                    "-proc:none",
                                    "-classpath", apiClasses.toString(),
                                    "-d", classesRoot.toString()),
                            null,
                            fileManager.getJavaFileObjects(source.toFile()))
                    .call();
            assertTrue(Boolean.TRUE.equals(compiled),
                    () -> "fixture compilation against api-only failed:\n"
                            + diagnosticsText(diagnostics));
        }
        return classesRoot;
    }

    private static Path apiClassesLocation() throws Exception {
        URL location = net.weaver.api.event.Event.class.getProtectionDomain()
                .getCodeSource().getLocation();
        assertNotNull(location, "api classes location");
        return Path.of(location.toURI());
    }

    private static String diagnosticsText(DiagnosticCollector<JavaFileObject> diagnostics) {
        return diagnostics.getDiagnostics().stream()
                .map(diagnostic -> diagnostic.getKind() + " at "
                        + diagnostic.getSource() + ":" + diagnostic.getLineNumber()
                        + " - " + diagnostic.getMessage(null))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    @SuppressWarnings("removal")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Unsafe unsafe = (Unsafe) field.get(null);
        return type.cast(unsafe.allocateInstance(type));
    }
}
