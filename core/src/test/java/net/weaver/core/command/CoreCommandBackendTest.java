package net.weaver.core.command;

import net.weaver.api.command.CommandNodeSpec;
import net.weaver.api.command.CommandRegistration;
import net.weaver.api.command.CommandRegistrationException;
import net.weaver.api.command.CommandRegistry;
import net.weaver.api.command.CommandSpec;
import net.weaver.api.command.CommandTreeSnapshot;
import net.weaver.api.command.PermissionNode;
import net.weaver.api.command.PermissionPolicy;
import net.weaver.api.command.PermissionResolver;
import net.weaver.api.command.PermissionService;
import net.weaver.api.command.PermissionStore;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.events.CommandRegisterEvent;
import net.weaver.api.event.events.ServerStoppedEvent;
import net.weaver.api.event.events.ServerTickEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreCommandBackendTest
{
    private CoreCommandBackend backend;

    @BeforeEach
    void setUp()
    {
        CommandRegistry.reset();
        CommandRegistry.resetTreeSnapshot();
        PermissionService.reset();
    }

    @AfterEach
    void tearDown()
    {
        if (backend != null)
        {
            backend.uninstall();
            backend = null;
        }
        EventBus.GAME.unregister(this);
        CommandRegistry.reset();
        CommandRegistry.resetTreeSnapshot();
        PermissionService.reset();
    }

    @Test
    void registerBeforeDispatcherIsAppliedOnRegisterEvent()
    {
        RecordingCommandRegistrar registrar = new RecordingCommandRegistrar();
        backend = new CoreCommandBackend(registrar);
        backend.install();
        CommandSpec spec = CommandSpec.command("proof").alias("p").executor(context -> 1).build();

        CommandRegistration registration = backend.register(spec);

        assertEquals(0, registrar.registerCalls());
        Object dispatcher = new Object();
        EventBus.GAME.post(new CommandRegisterEvent(new FakeCommands(dispatcher)));

        assertEquals(1, registrar.registerCalls());
        assertSame(spec, registrar.specs().get(0));
        assertSame(dispatcher, registrar.dispatchers().get(0));
        assertSame(dispatcher, backend.dispatcher());
        assertTrue(registration.isActive());
        assertEquals(List.of("proof", "p"), backend.commandNames());
        assertThrows(UnsupportedOperationException.class,
                () -> backend.commandNames().add("other"));

        CommandTreeSnapshot snapshot = CommandRegistry.treeSnapshot();
        assertNotNull(snapshot);
        assertEquals(List.of("proof", "p"), snapshot.commandNames());
        assertEquals("server", snapshot.source());
        assertTrue(snapshot.sequence() > 0L);
    }

    @Test
    void duplicateRootNamesAndAliasesAreRejected()
    {
        backend = new CoreCommandBackend(new RecordingCommandRegistrar());
        backend.install();
        backend.register(CommandSpec.command("dup").alias("d").build());

        assertThrows(CommandRegistrationException.class,
                () -> backend.register(CommandSpec.command("dup").build()));
        assertThrows(CommandRegistrationException.class,
                () -> backend.register(CommandSpec.command("other").alias("dup").build()));
        assertThrows(CommandRegistrationException.class,
                () -> backend.register(CommandSpec.command("d").build()));
        assertThrows(CommandRegistrationException.class,
                () -> backend.register(CommandSpec.command("another").alias("d").build()));
    }

    @Test
    void lateRegistrationWaitsForTheServerTick()
    {
        RecordingCommandRegistrar registrar = new RecordingCommandRegistrar();
        backend = new CoreCommandBackend(registrar);
        backend.install();
        backend.register(CommandSpec.command("proof").executor(context -> 1).build());
        EventBus.GAME.post(new CommandRegisterEvent(new FakeCommands(new Object())));
        assertEquals(1, registrar.registerCalls());

        backend.register(CommandSpec.command("late").executor(context -> 1).build());

        assertEquals(1, registrar.registerCalls());
        EventBus.GAME.post(new ServerTickEvent.Pre(1L));

        assertEquals(2, registrar.registerCalls());
        CommandTreeSnapshot snapshot = CommandRegistry.treeSnapshot();
        assertNotNull(snapshot);
        assertEquals("server-tick", snapshot.source());
        assertEquals(List.of("proof", "late"), snapshot.commandNames());

        EventBus.GAME.post(new ServerTickEvent.Pre(2L));

        assertEquals(2, registrar.registerCalls());
    }

    @Test
    void unregisterBeforeBuildPreventsRegistration()
    {
        RecordingCommandRegistrar registrar = new RecordingCommandRegistrar();
        backend = new CoreCommandBackend(registrar);
        backend.install();
        CommandRegistration registration = backend.register(
                CommandSpec.command("proof").executor(context -> 1).build());

        registration.unregister();
        EventBus.GAME.post(new CommandRegisterEvent(new FakeCommands(new Object())));

        assertEquals(0, registrar.registerCalls());
        assertFalse(registration.isActive());
        assertTrue(backend.commandNames().isEmpty());
    }

    @Test
    void unregisterAfterBuildMarksInactiveAndStopsFutureBuilds()
    {
        RecordingCommandRegistrar registrar = new RecordingCommandRegistrar();
        backend = new CoreCommandBackend(registrar);
        backend.install();
        CommandRegistration registration = backend.register(
                CommandSpec.command("proof").executor(context -> 1).build());
        EventBus.GAME.post(new CommandRegisterEvent(new FakeCommands(new Object())));
        assertEquals(1, registrar.registerCalls());

        registration.unregister();

        assertFalse(registration.isActive());
        assertTrue(backend.commandNames().isEmpty());
        assertEquals(1, registrar.removedNodes().size());
        EventBus.GAME.post(new CommandRegisterEvent(new FakeCommands(new Object())));
        EventBus.GAME.post(new ServerTickEvent.Pre(1L));
        assertEquals(1, registrar.registerCalls());
    }

    @Test
    void unregisterRebuildsRemainingActiveCommands()
    {
        RecordingCommandRegistrar registrar = new RecordingCommandRegistrar();
        backend = new CoreCommandBackend(registrar);
        backend.install();
        CommandRegistration removed = backend.register(
                CommandSpec.command("removed").executor(context -> 1).build());
        backend.register(CommandSpec.command("kept").executor(context -> 1).build());
        EventBus.GAME.post(new CommandRegisterEvent(new FakeCommands(new Object())));
        assertEquals(2, registrar.registerCalls());

        removed.unregister();

        assertEquals(2, registrar.removedNodes().size());
        assertEquals(3, registrar.registerCalls());
        assertEquals(List.of("kept"), backend.commandNames());
        CommandTreeSnapshot snapshot = CommandRegistry.treeSnapshot();
        assertNotNull(snapshot);
        assertEquals("rebuild", snapshot.source());
        assertEquals(List.of("kept"), snapshot.commandNames());
    }

    @Test
    void clientTreeSyncSourceIsRecordedSeparately()
    {
        Object dispatcher = new Object();
        CommandRegistry.recordTreeSync(dispatcher, List.of("clientonly"), "client");

        CommandTreeSnapshot snapshot = CommandRegistry.treeSnapshot();
        assertNotNull(snapshot);
        assertEquals("client", snapshot.source());
        assertEquals(List.of("clientonly"), snapshot.commandNames());

        backend = new CoreCommandBackend(new RecordingCommandRegistrar());
        backend.install();
        backend.register(CommandSpec.command("proof").executor(context -> 1).build());
        EventBus.GAME.post(new CommandRegisterEvent(new FakeCommands(new Object())));
        backend.register(CommandSpec.command("late").executor(context -> 1).build());
        EventBus.GAME.post(new ServerTickEvent.Pre(1L));

        snapshot = CommandRegistry.treeSnapshot();
        assertNotNull(snapshot);
        assertEquals("server-tick", snapshot.source());
        assertEquals(List.of("proof", "late"), snapshot.commandNames());
    }

    @Test
    void installKeepsAResolverThatWasInstalledWithAStore()
    {
        PermissionResolver configured = new FakePermissionResolver();
        PermissionStore store = new PermissionStore()
        {
            @Override
            public Map<String, Map<String, Boolean>> load()
            {
                return Map.of();
            }

            @Override
            public void save(Map<String, Map<String, Boolean>> decisions)
            {
            }
        };
        PermissionService.install(configured, store);
        backend = new CoreCommandBackend(new RecordingCommandRegistrar());

        backend.install();

        assertSame(configured, PermissionService.resolver());
        assertTrue(PermissionService.hasStore());
    }

    @Test
    void uninstallRemovesListenersAndMarksHandlesInactive()
    {
        RecordingCommandRegistrar registrar = new RecordingCommandRegistrar();
        backend = new CoreCommandBackend(registrar);
        backend.install();
        CommandRegistration registration = backend.register(
                CommandSpec.command("proof").executor(context -> 1).build());

        backend.uninstall();

        assertNull(backend.dispatcher());
        assertFalse(registration.isActive());
        assertTrue(backend.commandNames().isEmpty());
        EventBus.GAME.post(new CommandRegisterEvent(new FakeCommands(new Object())));
        assertEquals(0, registrar.registerCalls());
        backend.uninstall();
    }

    @Test
    void permissionsAreRegisteredForRootAndChildrenAndReleasedOnUninstall()
    {
        PermissionNode rootNode = PermissionNode.parse("proof:command/proof");
        PermissionNode childNode = PermissionNode.parse("proof:command/proof/status");
        CommandSpec spec = CommandSpec.command("proof")
                .permission(rootNode, PermissionPolicy.ALLOW, 0)
                .child(CommandNodeSpec.literal("status")
                        .permission(childNode, PermissionPolicy.ALLOW, 0)
                        .build())
                .build();
        backend = new CoreCommandBackend(new RecordingCommandRegistrar());
        backend.install();
        Object source = new Object();

        backend.register(spec);

        assertTrue(PermissionService.isAllowed(source, rootNode));
        assertTrue(PermissionService.isAllowed(source, childNode));
        backend.uninstall();
        assertFalse(PermissionService.isAllowed(source, rootNode));
        assertFalse(PermissionService.isAllowed(source, childNode));
    }

    @Test
    void permissionOpLevelFallbackUsesTheInstalledResolver()
    {
        FakePermissionResolver resolver = new FakePermissionResolver();
        resolver.setOpLevel(2);
        PermissionNode node = PermissionNode.parse("proof:command/op");
        CommandSpec spec = CommandSpec.command("proof")
                .permission(node, PermissionPolicy.DENY, 2)
                .build();
        backend = new CoreCommandBackend(new RecordingCommandRegistrar());
        backend.install();
        PermissionService.install(resolver);
        Object source = new Object();

        backend.register(spec);

        assertTrue(PermissionService.isAllowed(source, node));
        resolver.setOpLevel(1);
        assertFalse(PermissionService.isAllowed(source, node));
    }

    @Test
    void registrarFailureIsLoggedAndDoesNotBreakOtherCommands()
    {
        RecordingCommandRegistrar registrar = new RecordingCommandRegistrar();
        registrar.setFailingName("bad");
        backend = new CoreCommandBackend(registrar);
        backend.install();
        backend.register(CommandSpec.command("bad").executor(context -> 1).build());
        backend.register(CommandSpec.command("good").executor(context -> 1).build());

        EventBus.GAME.post(new CommandRegisterEvent(new FakeCommands(new Object())));

        assertEquals(1, registrar.registerCalls());
        assertEquals(List.of("good"), registrar.specs().stream().map(CommandSpec::name).toList());
        assertEquals(List.of("bad", "good"), backend.commandNames());
    }

    @Test
    void isAvailableDelegatesAndNeverThrows()
    {
        RecordingCommandRegistrar registrar = new RecordingCommandRegistrar();
        backend = new CoreCommandBackend(registrar);

        assertTrue(backend.isAvailable());
        registrar.setAvailable(false);
        assertFalse(backend.isAvailable());
        registrar.setAvailabilityThrows(true);
        assertFalse(backend.isAvailable());
    }

    @Test
    void serverStoppedClearsTheDispatcherAndAppliedTracking()
    {
        RecordingCommandRegistrar registrar = new RecordingCommandRegistrar();
        backend = new CoreCommandBackend(registrar);
        backend.install();
        backend.register(CommandSpec.command("proof").executor(context -> 1).build());
        Object first = new Object();
        EventBus.GAME.post(new CommandRegisterEvent(new FakeCommands(first)));
        assertEquals(1, registrar.registerCalls());

        EventBus.GAME.post(new ServerStoppedEvent(new Object()));

        assertNull(backend.dispatcher());
        Object second = new Object();
        EventBus.GAME.post(new CommandRegisterEvent(new FakeCommands(second)));
        assertEquals(2, registrar.registerCalls());
        assertSame(second, backend.dispatcher());
    }

    @Test
    void installIsIdempotent()
    {
        RecordingCommandRegistrar registrar = new RecordingCommandRegistrar();
        backend = new CoreCommandBackend(registrar);
        backend.install();
        backend.install();
        backend.register(CommandSpec.command("proof").executor(context -> 1).build());

        EventBus.GAME.post(new CommandRegisterEvent(new FakeCommands(new Object())));

        assertEquals(1, registrar.registerCalls());
    }

    @Test
    void instanceIsASingleton()
    {
        CoreCommandBackend first = CoreCommandBackend.instance();
        CoreCommandBackend second = CoreCommandBackend.instance();
        backend = first;

        assertSame(first, second);
    }
}
