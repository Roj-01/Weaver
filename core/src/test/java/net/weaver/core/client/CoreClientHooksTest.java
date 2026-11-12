package net.weaver.core.client;

import net.weaver.api.client.ClientTooltipHook;
import net.weaver.api.client.HudLayer;
import net.weaver.api.client.HudRenderContext;
import net.weaver.api.client.TooltipContext;
import net.weaver.api.client.TooltipProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreClientHooksTest
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
    void hooksForwardGraphicsAndAppendLiteralComponents()
    {
        FakeClientGameAdapter adapter = new FakeClientGameAdapter();
        adapter.guiWidth = 411;
        adapter.guiHeight = 222;
        adapter.currentScreen = new Object();
        backend = new CoreClientBackend(adapter, true);
        backend.install();
        AtomicReference<HudRenderContext> seenHud = new AtomicReference<>();
        AtomicReference<TooltipContext> seenTooltip = new AtomicReference<>();
        backend.registerHudLayer(new HudLayer()
        {
            @Override
            public String id()
            {
                return "demo:hud";
            }

            @Override
            public int order()
            {
                return 0;
            }

            @Override
            public void render(HudRenderContext context)
            {
                seenHud.set(context);
            }
        });
        backend.registerTooltipProvider(new TooltipProvider()
        {
            @Override
            public String id()
            {
                return "demo:tooltip";
            }

            @Override
            public void append(TooltipContext context, List<String> lines)
            {
                seenTooltip.set(context);
                lines.add("tip-a");
            }
        });
        Object graphics = new Object();

        CoreClientHooks.renderHud(graphics);

        assertNotNull(seenHud.get());
        assertSame(graphics, seenHud.get().graphics());
        assertSame(adapter.minecraft, seenHud.get().minecraft());
        assertEquals(411, seenHud.get().width());
        assertEquals(222, seenHud.get().height());
        assertEquals(0.0F, seenHud.get().partialTick());

        Object minecraft = new Object();
        Object itemStack = new Object();
        List<Object> lines = new ArrayList<>();
        lines.add("existing");

        CoreClientHooks.appendTooltip(minecraft, itemStack, lines);

        assertEquals(2, lines.size());
        assertSame("existing", lines.get(0));
        assertTrue(lines.get(1) instanceof FakeClientGameAdapter.FakeComponent);
        assertEquals("tip-a",
                ((FakeClientGameAdapter.FakeComponent) lines.get(1)).text());
        assertEquals(1, adapter.literalCalls);
        assertNotNull(seenTooltip.get());
        assertSame(minecraft, seenTooltip.get().minecraft());
        assertSame(itemStack, seenTooltip.get().itemStack());
        assertSame(adapter.currentScreen, seenTooltip.get().screen());
        assertEquals(List.of("existing"), seenTooltip.get().existingLines());
    }

    @Test
    void hooksAreNoOpsWithoutAnInstalledBackend()
    {
        CoreClientBackend.resetInstalledForTests();
        List<Object> lines = new ArrayList<>();

        CoreClientHooks.renderHud(new Object());
        CoreClientHooks.appendTooltip(new Object(), new Object(), lines);

        assertTrue(lines.isEmpty());
    }

    @Test
    void tooltipBridgeIsInstalledAndImmutableListsAreTolerated()
    {
        FakeClientGameAdapter adapter = new FakeClientGameAdapter();
        backend = new CoreClientBackend(adapter, true);
        backend.install();
        backend.registerTooltipProvider(new TooltipProvider()
        {
            @Override
            public String id()
            {
                return "demo:immutable";
            }

            @Override
            public void append(TooltipContext context, List<String> lines)
            {
                lines.add("extra");
            }
        });

        assertTrue(ClientTooltipHook.isInstalled());
        List<Object> immutable = List.of("existing");
        assertDoesNotThrow(() -> ClientTooltipHook.appendTooltip(new Object(), new Object(),
                immutable));
        assertEquals(1, immutable.size());
    }
}
