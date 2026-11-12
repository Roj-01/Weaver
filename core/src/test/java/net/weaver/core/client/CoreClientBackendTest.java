package net.weaver.core.client;

import net.weaver.api.client.ClientException;
import net.weaver.api.client.ClientRegistration;
import net.weaver.api.client.ClientThreadException;
import net.weaver.api.client.ClientTickContext;
import net.weaver.api.client.HudLayer;
import net.weaver.api.client.HudRenderContext;
import net.weaver.api.client.KeyMappingSpec;
import net.weaver.api.client.ResourceReloadContext;
import net.weaver.api.client.ScreenFactory;
import net.weaver.api.client.TooltipContext;
import net.weaver.api.client.TooltipProvider;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.Side;
import net.weaver.api.event.events.ClientTickEvent;
import net.weaver.api.event.events.ResourceReloadEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreClientBackendTest
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
    void keyMappingRunsActionOnPreTickAndStopsAfterUnregister()
    {
        FakeClientGameAdapter adapter = new FakeClientGameAdapter();
        CoreClientBackend backend = start(adapter);
        AtomicInteger runs = new AtomicInteger();
        KeyMappingSpec spec = KeyMappingSpec.key("demo", "open", 71);
        ClientRegistration registration = backend.registerKeyMapping(spec,
                runs::incrementAndGet);

        assertEquals("open:demo", registration.id());
        assertEquals(1, adapter.createdKeyMappings.size());
        assertTrue(registration.isActive());

        adapter.consumeClickResult = true;
        EventBus.GAME.post(new ClientTickEvent.Pre(1L));
        assertEquals(1, runs.get());

        adapter.consumeClickResult = false;
        EventBus.GAME.post(new ClientTickEvent.Pre(2L));
        assertEquals(1, runs.get());

        registration.unregister();
        assertFalse(registration.isActive());
        assertEquals(1, adapter.removedKeyMappings.size());

        adapter.consumeClickResult = true;
        EventBus.GAME.post(new ClientTickEvent.Pre(3L));
        assertEquals(1, runs.get());

        ClientRegistration replacement = backend.registerKeyMapping(spec,
                runs::incrementAndGet);
        assertTrue(replacement.isActive());
        assertThrows(ClientException.class, () -> backend.registerKeyMapping(spec,
                runs::incrementAndGet));
    }

    @Test
    void keyMappingActionFailureDoesNotStopLaterMappings()
    {
        FakeClientGameAdapter adapter = new FakeClientGameAdapter();
        CoreClientBackend backend = start(adapter);
        AtomicInteger later = new AtomicInteger();
        backend.registerKeyMapping(KeyMappingSpec.key("demo", "bad", 1), () ->
        {
            throw new IllegalStateException("boom");
        });
        backend.registerKeyMapping(KeyMappingSpec.key("demo", "good", 2),
                later::incrementAndGet);
        adapter.consumeClickResult = true;

        assertDoesNotThrow(() -> EventBus.GAME.post(new ClientTickEvent.Pre(1L)));

        assertEquals(1, later.get());
    }

    @Test
    void screenFactoryOpensFakeScreenAndUnknownIdIsNoOp()
    {
        FakeClientGameAdapter adapter = new FakeClientGameAdapter();
        adapter.currentScreen = new Object();
        CoreClientBackend backend = start(adapter);
        Object screen = new Object();
        AtomicReference<Object> seenMinecraft = new AtomicReference<>();
        AtomicReference<Object> seenParent = new AtomicReference<>();
        ScreenFactory factory = new ScreenFactory()
        {
            @Override
            public String id()
            {
                return "demo:menu";
            }

            @Override
            public Object createScreen(Object minecraft, Object parentScreen)
            {
                seenMinecraft.set(minecraft);
                seenParent.set(parentScreen);
                return screen;
            }
        };

        ClientRegistration registration = backend.registerScreenFactory(factory);
        assertEquals("demo:menu", registration.id());

        backend.openScreen("demo:menu");

        assertEquals(List.of(screen), adapter.openedScreens);
        assertSame(adapter.minecraft, seenMinecraft.get());
        assertSame(adapter.currentScreen, seenParent.get());

        backend.openScreen("demo:missing");

        assertEquals(1, adapter.openedScreens.size());
    }

    @Test
    void screenFactoryReturningNullCancelsOpen()
    {
        FakeClientGameAdapter adapter = new FakeClientGameAdapter();
        CoreClientBackend backend = start(adapter);
        ScreenFactory factory = new ScreenFactory()
        {
            @Override
            public String id()
            {
                return "demo:cancel";
            }

            @Override
            public Object createScreen(Object minecraft, Object parentScreen)
            {
                return null;
            }
        };
        backend.registerScreenFactory(factory);

        backend.openScreen("demo:cancel");

        assertTrue(adapter.openedScreens.isEmpty());
    }

    @Test
    void duplicateScreenFactoryIdIsRejected()
    {
        FakeClientGameAdapter adapter = new FakeClientGameAdapter();
        CoreClientBackend backend = start(adapter);
        ScreenFactory first = screenFactory("demo:menu");
        ScreenFactory second = screenFactory("demo:menu");

        backend.registerScreenFactory(first);

        assertThrows(ClientException.class, () -> backend.registerScreenFactory(second));
    }

    @Test
    void hudLayersRenderInOrderWithRegistrationTieBreak()
    {
        FakeClientGameAdapter adapter = new FakeClientGameAdapter();
        CoreClientBackend backend = start(adapter);
        List<String> calls = new ArrayList<>();
        backend.registerHudLayer(hudLayer("layer20", 20, calls));
        backend.registerHudLayer(hudLayer("layer10-first", 10, calls));
        backend.registerHudLayer(hudLayer("layer10-second", 10, calls));

        backend.renderHud(new HudRenderContext(new Object(), adapter.minecraft, 320, 240, 0.5F));

        assertEquals(List.of("layer10-first", "layer10-second", "layer20"), calls);
    }

    @Test
    void hudLayerFailureDoesNotStopLaterLayers()
    {
        FakeClientGameAdapter adapter = new FakeClientGameAdapter();
        CoreClientBackend backend = start(adapter);
        List<String> calls = new ArrayList<>();
        HudLayer failing = new HudLayer()
        {
            @Override
            public String id()
            {
                return "demo:failing";
            }

            @Override
            public int order()
            {
                return 0;
            }

            @Override
            public void render(HudRenderContext context)
            {
                throw new IllegalStateException("boom");
            }
        };
        backend.registerHudLayer(failing);
        backend.registerHudLayer(hudLayer("demo:good", 10, calls));

        assertDoesNotThrow(() -> backend.renderHud(
                new HudRenderContext(new Object(), adapter.minecraft, 320, 240, 0.0F)));

        assertEquals(List.of("demo:good"), calls);
    }

    @Test
    void duplicateHudLayerIdIsRejected()
    {
        FakeClientGameAdapter adapter = new FakeClientGameAdapter();
        CoreClientBackend backend = start(adapter);
        List<String> calls = new ArrayList<>();
        backend.registerHudLayer(hudLayer("demo:hud", 0, calls));

        assertThrows(ClientException.class,
                () -> backend.registerHudLayer(hudLayer("demo:hud", 10, calls)));
    }

    @Test
    void tooltipProvidersCollectNonNullLinesAndIsolateFailures()
    {
        FakeClientGameAdapter adapter = new FakeClientGameAdapter();
        CoreClientBackend backend = start(adapter);
        backend.registerTooltipProvider(new TooltipProvider()
        {
            @Override
            public String id()
            {
                return "demo:first";
            }

            @Override
            public void append(TooltipContext context, List<String> lines)
            {
                lines.add("first");
                lines.add(null);
            }
        });
        backend.registerTooltipProvider(new TooltipProvider()
        {
            @Override
            public String id()
            {
                return "demo:failing";
            }

            @Override
            public void append(TooltipContext context, List<String> lines)
            {
                throw new IllegalStateException("boom");
            }
        });
        backend.registerTooltipProvider(new TooltipProvider()
        {
            @Override
            public String id()
            {
                return "demo:last";
            }

            @Override
            public void append(TooltipContext context, List<String> lines)
            {
                lines.add("last");
            }
        });

        List<String> lines = backend.tooltipLines(
                new TooltipContext(adapter.minecraft, null, null, List.of()));

        assertNotNull(lines);
        assertEquals(List.of("first", "last"), lines);
        assertThrows(UnsupportedOperationException.class, () -> lines.add("extra"));
    }

    @Test
    void duplicateTooltipProviderIdIsRejected()
    {
        FakeClientGameAdapter adapter = new FakeClientGameAdapter();
        CoreClientBackend backend = start(adapter);
        backend.registerTooltipProvider(tooltipProvider("demo:tooltip", lines -> lines.add("one")));

        assertThrows(ClientException.class, () -> backend.registerTooltipProvider(
                tooltipProvider("demo:tooltip", lines -> lines.add("two"))));
    }

    @Test
    void tickAndReloadListenersReceiveContextsAndFailuresAreIsolated()
    {
        FakeClientGameAdapter adapter = new FakeClientGameAdapter();
        CoreClientBackend backend = start(adapter);
        List<ClientTickContext> pre = new ArrayList<>();
        List<ClientTickContext> post = new ArrayList<>();
        List<ResourceReloadContext> reloads = new ArrayList<>();
        backend.registerTickListener(context ->
        {
            throw new IllegalStateException("pre boom");
        }, true);
        backend.registerTickListener(pre::add, true);
        backend.registerTickListener(context ->
        {
            throw new IllegalStateException("post boom");
        }, false);
        backend.registerTickListener(post::add, false);
        backend.registerResourceReloadListener(context ->
        {
            throw new IllegalStateException("reload boom");
        });
        backend.registerResourceReloadListener(reloads::add);

        EventBus.GAME.post(new ClientTickEvent.Pre(7L));
        EventBus.GAME.post(new ClientTickEvent.Post(7L));
        Object source = new Object();
        EventBus.GAME.post(new ResourceReloadEvent(source, Side.CLIENT,
                ResourceReloadEvent.Phase.START));
        EventBus.GAME.post(new ResourceReloadEvent(source, Side.CLIENT,
                ResourceReloadEvent.Phase.FINISHED));
        EventBus.GAME.post(new ResourceReloadEvent(source, Side.CLIENT,
                ResourceReloadEvent.Phase.FAILED));
        EventBus.GAME.post(new ResourceReloadEvent(source, Side.SERVER,
                ResourceReloadEvent.Phase.START));

        assertEquals(1, pre.size());
        assertEquals(7L, pre.get(0).tick());
        assertTrue(pre.get(0).pre());
        assertSame(adapter.minecraft, pre.get(0).minecraft());

        assertEquals(1, post.size());
        assertEquals(7L, post.get(0).tick());
        assertFalse(post.get(0).pre());
        assertSame(adapter.minecraft, post.get(0).minecraft());

        assertEquals(3, reloads.size());
        assertEquals(ResourceReloadContext.Phase.START, reloads.get(0).phase());
        assertEquals(ResourceReloadContext.Phase.FINISHED, reloads.get(1).phase());
        assertEquals(ResourceReloadContext.Phase.FAILED, reloads.get(2).phase());
        assertSame(source, reloads.get(0).source());
        assertTrue(reloads.get(0).client());
    }

    @Test
    void wrongThreadIsRejectedByEveryClientOperation()
    {
        FakeClientGameAdapter adapter = new FakeClientGameAdapter();
        adapter.clientThread = false;
        CoreClientBackend backend = start(adapter);
        assertTrue(backend.isAvailable());
        ScreenFactory factory = screenFactory("demo:menu");
        List<String> calls = new ArrayList<>();
        HudLayer layer = hudLayer("demo:hud", 0, calls);
        TooltipProvider provider = tooltipProvider("demo:tooltip", lines -> lines.add("line"));

        assertThrows(ClientThreadException.class, () -> backend.registerKeyMapping(
                KeyMappingSpec.key("demo", "key", 1), () -> { }));
        assertThrows(ClientThreadException.class,
                () -> backend.registerScreenFactory(factory));
        assertThrows(ClientThreadException.class, () -> backend.registerHudLayer(layer));
        assertThrows(ClientThreadException.class,
                () -> backend.registerTooltipProvider(provider));
        assertThrows(ClientThreadException.class,
                () -> backend.registerTickListener(context -> { }, true));
        assertThrows(ClientThreadException.class,
                () -> backend.registerResourceReloadListener(context -> { }));
        assertThrows(ClientThreadException.class, () -> backend.openScreen("demo:menu"));
        assertThrows(ClientThreadException.class, () -> backend.renderHud(
                new HudRenderContext(new Object(), adapter.minecraft, 320, 240, 0.0F)));
        assertThrows(ClientThreadException.class, () -> backend.tooltipLines(
                new TooltipContext(adapter.minecraft, null, null, List.of())));
    }

    @Test
    void dedicatedServerHasNoClientAndRejectsRegistration()
    {
        FakeClientGameAdapter adapter = new FakeClientGameAdapter();
        adapter.available = false;
        CoreClientBackend backend = start(adapter);

        assertFalse(backend.isAvailable());

        ClientThreadException failure = assertThrows(ClientThreadException.class,
                () -> backend.registerKeyMapping(KeyMappingSpec.key("demo", "key", 1),
                        () -> { }));
        assertTrue(failure.getMessage().contains("registerKeyMapping"),
                failure.getMessage());
    }

    @Test
    void hooksAreNoOpsWhenNoBackendIsInstalled()
    {
        CoreClientBackend.resetInstalledForTests();
        List<Object> lines = new ArrayList<>();

        assertDoesNotThrow(() -> CoreClientHooks.renderHud(new Object()));
        assertDoesNotThrow(() -> CoreClientHooks.appendTooltip(new Object(), new Object(),
                lines));

        assertTrue(lines.isEmpty());
    }

    @Test
    void sharedInstanceIsCachedAndInstalled()
    {
        CoreClientBackend shared = CoreClientBackend.instance();

        assertSame(shared, CoreClientBackend.instance());
        assertSame(shared, CoreClientBackend.installed());
        assertDoesNotThrow(() -> EventBus.GAME.post(new ClientTickEvent.Pre(1L)));
    }

    @Test
    void uninstallRemovesEventListenersAndDeactivatesHandles()
    {
        FakeClientGameAdapter adapter = new FakeClientGameAdapter();
        CoreClientBackend backend = start(adapter);
        AtomicInteger ticks = new AtomicInteger();
        ClientRegistration registration = backend.registerTickListener(
                context -> ticks.incrementAndGet(), true);

        EventBus.GAME.post(new ClientTickEvent.Pre(1L));
        assertEquals(1, ticks.get());

        backend.install();
        EventBus.GAME.post(new ClientTickEvent.Pre(2L));
        assertEquals(2, ticks.get());

        backend.uninstall();

        assertFalse(registration.isActive());
        EventBus.GAME.post(new ClientTickEvent.Pre(3L));
        assertEquals(2, ticks.get());
        assertDoesNotThrow(backend::uninstall);
    }

    private CoreClientBackend start(FakeClientGameAdapter adapter)
    {
        backend = new CoreClientBackend(adapter, true);
        backend.install();
        return backend;
    }

    private static ScreenFactory screenFactory(String id)
    {
        return new ScreenFactory()
        {
            @Override
            public String id()
            {
                return id;
            }

            @Override
            public Object createScreen(Object minecraft, Object parentScreen)
            {
                return new Object();
            }
        };
    }

    private static HudLayer hudLayer(String id, int order, List<String> calls)
    {
        return new HudLayer()
        {
            @Override
            public String id()
            {
                return id;
            }

            @Override
            public int order()
            {
                return order;
            }

            @Override
            public void render(HudRenderContext context)
            {
                calls.add(id);
            }
        };
    }

    private static TooltipProvider tooltipProvider(String id,
            java.util.function.Consumer<List<String>> appender)
    {
        return new TooltipProvider()
        {
            @Override
            public String id()
            {
                return id;
            }

            @Override
            public void append(TooltipContext context, List<String> lines)
            {
                appender.accept(lines);
            }
        };
    }
}
