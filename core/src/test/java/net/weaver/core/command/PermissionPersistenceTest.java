package net.weaver.core.command;

import net.weaver.api.command.PermissionNode;
import net.weaver.api.command.PermissionResolver;
import net.weaver.api.command.PermissionService;
import net.weaver.api.command.PermissionStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionPersistenceTest
{
    private static final PermissionNode NODE = PermissionNode.parse("demo:command/gate");

    @TempDir
    Path temporaryDirectory;

    @BeforeEach
    void setUp()
    {
        PermissionService.reset();
        PermissionStoreSupport.reset();
    }

    @AfterEach
    void tearDown()
    {
        PermissionService.reset();
        PermissionStoreSupport.reset();
    }

    @Test
    void decisionsSurviveAStoreRestartWithStableUuidKeys()
    {
        Path file = temporaryDirectory.resolve("permissions.json");
        UUID playerId = UUID.randomUUID();
        Subject player = new Subject(playerId);
        PermissionService.install(new SubjectResolver(),
                new JsonPermissionStore(file, message ->
                {
                }));

        PermissionService.allow(player, NODE);

        assertEquals(Map.of("player:" + playerId, Map.of(NODE.toString(), true)),
                PermissionService.snapshot());

        PermissionService.reset();
        PermissionService.install(new SubjectResolver(),
                new JsonPermissionStore(file, message ->
                {
                }));

        assertTrue(PermissionService.isAllowed(new Subject(playerId), NODE));
        assertTrue(PermissionService.hasExplicitDecision(new Subject(playerId), NODE));
    }

    @Test
    void clearPersistsTheRemovedDecision()
    {
        Path file = temporaryDirectory.resolve("permissions.json");
        UUID playerId = UUID.randomUUID();
        Subject player = new Subject(playerId);
        PermissionService.install(new SubjectResolver(),
                new JsonPermissionStore(file, message ->
                {
                }));
        PermissionService.allow(player, NODE);

        PermissionService.clear(player, NODE);

        assertTrue(PermissionService.snapshot().isEmpty());

        PermissionService.reset();
        PermissionService.install(new SubjectResolver(),
                new JsonPermissionStore(file, message ->
                {
                }));

        assertFalse(PermissionService.isAllowed(new Subject(playerId), NODE));
    }

    @Test
    void clearAllRemovesEveryNodeForTheSubject()
    {
        Path file = temporaryDirectory.resolve("permissions.json");
        UUID playerId = UUID.randomUUID();
        Subject player = new Subject(playerId);
        PermissionNode other = PermissionNode.parse("demo:command/other");
        PermissionService.install(new SubjectResolver(),
                new JsonPermissionStore(file, message ->
                {
                }));
        PermissionService.allow(player, NODE);
        PermissionService.deny(player, other);

        PermissionService.clearAll(player);

        assertTrue(PermissionService.snapshot().isEmpty());
    }

    @Test
    void resetClearsThePersistedMapAndStoreReference()
    {
        Path file = temporaryDirectory.resolve("permissions.json");
        Subject player = new Subject(UUID.randomUUID());
        PermissionService.install(new SubjectResolver(),
                new JsonPermissionStore(file, message ->
                {
                }));
        PermissionService.allow(player, NODE);
        assertTrue(PermissionService.hasStore());

        PermissionService.reset();

        assertTrue(PermissionService.snapshot().isEmpty());
        assertFalse(PermissionService.hasStore());
    }

    @Test
    void storeFailuresAreIsolated()
    {
        PermissionStore failing = new PermissionStore()
        {
            @Override
            public Map<String, Map<String, Boolean>> load()
            {
                throw new IllegalStateException("load failed");
            }

            @Override
            public void save(Map<String, Map<String, Boolean>> decisions)
            {
                throw new IllegalStateException("save failed");
            }
        };
        Subject player = new Subject(UUID.randomUUID());

        PermissionService.install(new SubjectResolver(), failing);
        PermissionService.allow(player, NODE);

        assertEquals(Map.of("player:" + player.id(), Map.of(NODE.toString(), true)),
                PermissionService.snapshot());
    }

    @Test
    void permissionStoreSupportInstallsThePairIdempotently()
    {
        Path file = temporaryDirectory.resolve("loader-permissions.json");
        List<String> messages = new ArrayList<>();

        PermissionStoreSupport.install(file, messages::add);
        PermissionResolver first = PermissionService.resolver();
        PermissionStoreSupport.install(file, messages::add);

        assertTrue(PermissionService.hasStore());
        assertTrue(first instanceof MinecraftPermissionResolver);
        assertEquals(first, PermissionService.resolver());
        assertEquals(2, messages.size(), messages.toString());
        assertTrue(messages.stream().anyMatch(message ->
                message.contains("[weaver] PERMISSION STORE ready")), messages.toString());
    }

    @Test
    void permissionStoreSupportIsFailureIsolated()
    {
        List<String> messages = new ArrayList<>();

        PermissionStoreSupport.install(null, messages::add);

        assertFalse(PermissionService.hasStore());
        assertEquals(1, messages.size(), messages.toString());
        assertTrue(messages.get(0).contains("[weaver] PERMISSION STORE INSTALL FAILED"));
    }

    private record Subject(UUID id)
    {
    }

    private static final class SubjectResolver implements PermissionResolver
    {
        @Override
        public Object subject(Object source)
        {
            return source;
        }

        @Override
        public int opLevel(Object source)
        {
            return 0;
        }

        @Override
        public String subjectKey(Object subject)
        {
            if (subject instanceof Subject player)
            {
                return "player:" + player.id();
            }
            return PermissionResolver.super.subjectKey(subject);
        }
    }
}
