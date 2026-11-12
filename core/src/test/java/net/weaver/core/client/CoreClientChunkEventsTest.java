package net.weaver.core.client;

import net.weaver.api.client.ClientBlockContext;
import net.weaver.api.client.ClientChunkContext;
import net.weaver.api.client.ClientChunkListener;
import net.weaver.api.client.ClientException;
import net.weaver.api.client.ClientRegistration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreClientChunkEventsTest
{
    private CoreClientBackend backend;

    @BeforeEach
    void resetGlobals()
    {
        CoreClientBackend.resetInstalledForTests();
    }

    @AfterEach
    void cleanup()
    {
        if (backend != null)
        {
            backend.uninstall();
            backend = null;
        }
        CoreClientBackend.resetInstalledForTests();
    }

    @Test
    void chunkEventsReachEveryListenerInRegistrationOrder()
    {
        FakeClientGameAdapter adapter = new FakeClientGameAdapter();
        backend = new CoreClientBackend(adapter, true);
        backend.install();
        List<String> calls = new ArrayList<>();
        ClientChunkListener first = listener("first", calls);
        ClientChunkListener second = listener("second", calls);
        backend.registerChunkListener(first);
        backend.registerChunkListener(second);

        backend.chunkLoaded(new ClientChunkContext("minecraft:overworld", 3, -7));
        backend.chunkUnloaded(new ClientChunkContext("minecraft:overworld", 4, -8));
        backend.blockUpdated(new ClientBlockContext("minecraft:overworld", 1, 64, -2));

        assertEquals(List.of(
                "first:load:minecraft:overworld:3:-7",
                "second:load:minecraft:overworld:3:-7",
                "first:unload:minecraft:overworld:4:-8",
                "second:unload:minecraft:overworld:4:-8",
                "first:block:minecraft:overworld:1:64:-2",
                "second:block:minecraft:overworld:1:64:-2"), calls);
    }

    @Test
    void duplicateListenerInstanceIsRejectedButEqualClassesAreAllowed()
    {
        FakeClientGameAdapter adapter = new FakeClientGameAdapter();
        backend = new CoreClientBackend(adapter, true);
        backend.install();
        List<String> calls = new ArrayList<>();
        ClientChunkListener listener = listener("one", calls);
        backend.registerChunkListener(listener);

        assertThrows(ClientException.class, () -> backend.registerChunkListener(listener));
        assertDoesNotThrow(() -> backend.registerChunkListener(listener("two", calls)));
    }

    @Test
    void unregisterRemovesOnlyThatListener()
    {
        FakeClientGameAdapter adapter = new FakeClientGameAdapter();
        backend = new CoreClientBackend(adapter, true);
        backend.install();
        List<String> calls = new ArrayList<>();
        ClientRegistration first = backend.registerChunkListener(listener("first", calls));
        backend.registerChunkListener(listener("second", calls));

        first.unregister();
        assertFalse(first.isActive());
        backend.chunkLoaded(new ClientChunkContext("minecraft:overworld", 0, 0));

        assertEquals(List.of("second:load:minecraft:overworld:0:0"), calls);
    }

    @Test
    void oneFailingListenerDoesNotStopLaterListeners()
    {
        FakeClientGameAdapter adapter = new FakeClientGameAdapter();
        backend = new CoreClientBackend(adapter, true);
        backend.install();
        List<String> calls = new ArrayList<>();
        backend.registerChunkListener(new ClientChunkListener()
        {
            @Override
            public void onChunkLoad(ClientChunkContext context)
            {
                throw new IllegalStateException("boom");
            }

            @Override
            public void onChunkUnload(ClientChunkContext context)
            {
                throw new IllegalStateException("boom");
            }

            @Override
            public void onBlockUpdate(ClientBlockContext context)
            {
                throw new IllegalStateException("boom");
            }
        });
        backend.registerChunkListener(listener("good", calls));

        assertDoesNotThrow(() ->
                backend.chunkLoaded(new ClientChunkContext("minecraft:overworld", 1, 1)));
        assertDoesNotThrow(() ->
                backend.chunkUnloaded(new ClientChunkContext("minecraft:overworld", 1, 1)));
        assertDoesNotThrow(() ->
                backend.blockUpdated(new ClientBlockContext("minecraft:overworld", 1, 2, 3)));

        assertEquals(List.of("good:load:minecraft:overworld:1:1",
                "good:unload:minecraft:overworld:1:1",
                "good:block:minecraft:overworld:1:2:3"), calls);
    }

    @Test
    void contextsRejectNullAndBlankDimensions()
    {
        assertThrows(IllegalArgumentException.class,
                () -> new ClientChunkContext(null, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ClientChunkContext(" ", 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ClientBlockContext(null, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ClientBlockContext("", 0, 0, 0));
    }

    @Test
    void registrationRequiresTheClientThread()
    {
        FakeClientGameAdapter adapter = new FakeClientGameAdapter();
        adapter.clientThread = false;
        backend = new CoreClientBackend(adapter, true);
        backend.install();

        assertThrows(net.weaver.api.client.ClientThreadException.class,
                () -> backend.registerChunkListener(listener("thread", new ArrayList<>())));
    }

    @Test
    void uninstallClearsListeners()
    {
        FakeClientGameAdapter adapter = new FakeClientGameAdapter();
        backend = new CoreClientBackend(adapter, true);
        backend.install();
        List<String> calls = new ArrayList<>();
        backend.registerChunkListener(listener("one", calls));
        CoreClientBackend uninstalled = backend;

        backend.uninstall();
        backend = null;
        uninstalled.chunkLoaded(new ClientChunkContext("minecraft:overworld", 0, 0));

        assertTrue(calls.isEmpty());
    }

    private static ClientChunkListener listener(String name, List<String> calls)
    {
        return new ClientChunkListener()
        {
            @Override
            public void onChunkLoad(ClientChunkContext context)
            {
                calls.add(name + ":load:" + context.dimension() + ":" + context.chunkX()
                        + ":" + context.chunkZ());
            }

            @Override
            public void onChunkUnload(ClientChunkContext context)
            {
                calls.add(name + ":unload:" + context.dimension() + ":" + context.chunkX()
                        + ":" + context.chunkZ());
            }

            @Override
            public void onBlockUpdate(ClientBlockContext context)
            {
                calls.add(name + ":block:" + context.dimension() + ":" + context.x()
                        + ":" + context.y() + ":" + context.z());
            }
        };
    }
}
