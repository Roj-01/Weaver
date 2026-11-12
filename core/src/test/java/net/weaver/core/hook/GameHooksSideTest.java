package net.weaver.core.hook;

import net.weaver.api.ModContext;
import net.weaver.api.event.LogicalSide;
import net.weaver.core.LifecycleHooks;
import net.weaver.core.ModJar;
import net.weaver.core.event.LogicalSideFixtureMod;
import net.weaver.core.event.TestJars;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameHooksSideTest {

    @TempDir
    Path tempDir;

    private ModJar mod;

    @BeforeEach
    void setUp() {
        LifecycleHooks.reset();
        LogicalSideFixtureMod.reset();
        GameEventFactoryHolder.resetForTests();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mod != null) {
            mod.close();
            mod = null;
        }
        LifecycleHooks.reset();
        LogicalSideFixtureMod.reset();
        GameEventFactoryHolder.resetForTests();
    }

    @Test
    void sideCallbacksReceiveMatchingLogicalContext() throws Exception {
        List<String> log = new ArrayList<>();
        mod = ModJar.read(TestJars.create("logical-side-fixture",
                LogicalSideFixtureMod.class.getName(), LogicalSideFixtureMod.class));
        LifecycleHooks.install(List.of(mod), getClass().getClassLoader(), "client", true,
                tempDir.resolve("config"), tempDir.resolve("data"), log::add);
        LifecycleHooks.onInitialize();

        assertEquals(1, LifecycleHooks.modCount(), () -> "log: " + log);

        GameHooks.clientTickEnter();
        GameHooks.serverTickEnter();
        GameHooks.playerJoin(new Object(), new Object());

        assertEquals(1, LogicalSideFixtureMod.CLIENT_TICKS.get());
        assertEquals(1, LogicalSideFixtureMod.SERVER_TICKS.get());
        assertEquals(1, LogicalSideFixtureMod.JOINS.get());

        ModContext client = LogicalSideFixtureMod.clientContext;
        assertNotNull(client);
        assertTrue(client.isPhysicalClient());
        assertFalse(client.isPhysicalServer());
        assertTrue(client.isClient());
        assertFalse(client.isServer());
        assertEquals(LogicalSide.CLIENT, client.logicalSide());
        assertTrue(client.isLogicalClient());
        assertFalse(client.isLogicalServer());

        ModContext server = LogicalSideFixtureMod.serverContext;
        assertNotNull(server);
        assertTrue(server.isPhysicalClient());
        assertFalse(server.isPhysicalServer());
        assertTrue(server.isClient());
        assertFalse(server.isServer());
        assertEquals(LogicalSide.SERVER, server.logicalSide());
        assertTrue(server.isLogicalServer());
        assertFalse(server.isLogicalClient());

        ModContext join = LogicalSideFixtureMod.joinContext;
        assertNotNull(join);
        assertEquals(LogicalSide.SERVER, join.logicalSide());
        assertTrue(join.isLogicalServer());
    }
}
