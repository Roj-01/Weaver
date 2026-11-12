package net.weaver.api.game.client.screen;

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

class ScreenRenderRegistryTest
{
    private final List<String> calls = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        ScreenRenderRegistry.reset();
        calls.clear();
    }

    @AfterEach
    void tearDown()
    {
        ScreenRenderRegistry.reset();
    }

    @Test
    void preAndPostRunInOrderAndIgnoreCancellation()
    {
        ScreenRenderRegistry.register(listener("twenty", 20, ScreenRenderResult.CANCEL));
        ScreenRenderRegistry.register(listener("ten", 10, ScreenRenderResult.CONTINUE));

        assertFalse(ScreenRenderRegistry.dispatch(context(ScreenRenderPhase.PRE)));
        assertFalse(ScreenRenderRegistry.dispatch(context(ScreenRenderPhase.POST)));

        assertEquals(List.of(
                "ten:PRE", "twenty:PRE",
                "ten:POST", "twenty:POST"), calls);
    }

    @Test
    void backgroundStopsAtFirstCancellingListener()
    {
        ScreenRenderRegistry.register(listener("first", 0, ScreenRenderResult.CONTINUE));
        ScreenRenderRegistry.register(listener("cancel", 1, ScreenRenderResult.CANCEL));
        ScreenRenderRegistry.register(listener("never", 2, ScreenRenderResult.CONTINUE));

        boolean cancelled = ScreenRenderRegistry.dispatch(context(ScreenRenderPhase.BACKGROUND));

        assertTrue(cancelled);
        assertEquals(List.of("first:BACKGROUND", "cancel:BACKGROUND"), calls);
    }

    @Test
    void unregisterRemovesOnlyThatListener()
    {
        ClientRegistration first = ScreenRenderRegistry.register(
                listener("first", 0, ScreenRenderResult.CONTINUE));
        ScreenRenderRegistry.register(listener("second", 0, ScreenRenderResult.CONTINUE));

        first.unregister();

        assertFalse(first.isActive());
        ScreenRenderRegistry.dispatch(context(ScreenRenderPhase.PRE));
        assertEquals(List.of("second:PRE"), calls);
        assertEquals(1, ScreenRenderRegistry.listenerCount());
    }

    @Test
    void duplicateIdsAreRejected()
    {
        ScreenRenderRegistry.register(listener("same", 0, ScreenRenderResult.CONTINUE));

        assertThrows(ClientException.class, () -> ScreenRenderRegistry.register(
                listener("same", 0, ScreenRenderResult.CONTINUE)));
    }

    @Test
    void oneFailingListenerDoesNotStopLaterListeners()
    {
        ScreenRenderRegistry.register(new ScreenRenderListener()
        {
            @Override
            public String id()
            {
                return "bad";
            }

            @Override
            public ScreenRenderResult onScreenRender(ScreenRenderContext context)
            {
                throw new IllegalStateException("boom");
            }
        });
        ScreenRenderRegistry.register(listener("good", 0, ScreenRenderResult.CONTINUE));

        assertDoesNotThrow(() -> ScreenRenderRegistry.dispatch(
                context(ScreenRenderPhase.BACKGROUND)));

        assertEquals(List.of("good:BACKGROUND"), calls);
    }

    private ScreenRenderContext context(ScreenRenderPhase phase)
    {
        return new ScreenRenderContext(null, null, 32, 48, 0.5F, phase, null);
    }

    private ScreenRenderListener listener(String id, int order, ScreenRenderResult result)
    {
        return new ScreenRenderListener()
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
            public ScreenRenderResult onScreenRender(ScreenRenderContext context)
            {
                calls.add(id + ":" + context.phase());
                return result;
            }
        };
    }
}
