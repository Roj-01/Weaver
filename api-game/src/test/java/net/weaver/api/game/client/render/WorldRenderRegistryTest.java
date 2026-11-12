package net.weaver.api.game.client.render;

import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.SimpleGizmoCollector;
import net.minecraft.world.phys.Vec3;
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

class WorldRenderRegistryTest
{
    private final List<String> calls = new ArrayList<>();
    private WorldRenderContext context;

    @BeforeEach
    void setUp()
    {
        WorldRenderRegistry.reset();
        calls.clear();
        context = new WorldRenderContext(null, null, 0.0F, Vec3.ZERO,
                new RecordingSink(calls), position -> position, 1920, 1080);
    }

    @AfterEach
    void tearDown()
    {
        WorldRenderRegistry.reset();
    }

    @Test
    void listenersRunInOrderWithRegistrationTieBreak()
    {
        WorldRenderRegistry.register(listener("twenty", 20));
        WorldRenderRegistry.register(listener("ten-first", 10));
        WorldRenderRegistry.register(listener("ten-second", 10));

        WorldRenderRegistry.render(context);

        assertEquals(List.of("ten-first", "ten-second", "twenty"), calls);
        assertEquals(3, WorldRenderRegistry.listenerCount());
    }

    @Test
    void duplicateIdsAreRejected()
    {
        WorldRenderRegistry.register(listener("same", 0));

        assertThrows(ClientException.class, () -> WorldRenderRegistry.register(
                listener("same", 0)));
        assertThrows(ClientException.class, () -> WorldRenderRegistry.register(
                listener(" ", 0)));
    }

    @Test
    void unregisterRemovesOnlyThatListener()
    {
        ClientRegistration first = WorldRenderRegistry.register(listener("first", 0));
        WorldRenderRegistry.register(listener("second", 0));

        first.unregister();
        assertFalse(first.isActive());
        WorldRenderRegistry.render(context);

        assertEquals(List.of("second"), calls);
        assertEquals(1, WorldRenderRegistry.listenerCount());
    }

    @Test
    void oneFailingListenerDoesNotStopLaterListeners()
    {
        WorldRenderRegistry.register(new WorldRenderListener()
        {
            @Override
            public String id()
            {
                return "bad";
            }

            @Override
            public void render(WorldRenderContext ignored)
            {
                throw new IllegalStateException("boom");
            }
        });
        WorldRenderRegistry.register(listener("good", 0));

        assertDoesNotThrow(() -> WorldRenderRegistry.render(context));

        assertEquals(List.of("good"), calls);
    }

    @Test
    void sinkCanDrawLinesPointsAndText()
    {
        WorldRenderRegistry.register(new WorldRenderListener()
        {
            @Override
            public String id()
            {
                return "draw";
            }

            @Override
            public void render(WorldRenderContext renderContext)
            {
                WorldRenderSink sink = renderContext.sink();
                sink.line(Vec3.ZERO, new Vec3(1.0, 2.0, 3.0), 0xFFFFFFFF);
                sink.point(new Vec3(4.0, 5.0, 6.0), 0xFF00FF00, 2.0F);
                sink.text("hello", new Vec3(7.0, 8.0, 9.0), 0xFF0000FF, 1.5F);
            }
        });

        WorldRenderRegistry.render(context);

        assertEquals(3, calls.size());
        assertTrue(calls.get(0).startsWith("line:"));
        assertTrue(calls.get(1).startsWith("point:"));
        assertTrue(calls.get(2).startsWith("text:hello:"));
    }

    @Test
    void gizmoSinkEmitsLinesPointsAndText()
    {
        SimpleGizmoCollector collector = new SimpleGizmoCollector();
        try (Gizmos.TemporaryCollection ignored = Gizmos.withCollector(collector))
        {
            GizmoWorldRenderSink sink = new GizmoWorldRenderSink();
            sink.line(Vec3.ZERO, new Vec3(1.0, 2.0, 3.0), 0xFFFFFFFF, 2.0F);
            sink.point(new Vec3(4.0, 5.0, 6.0), 0xFF00FF00, 1.0F);
            sink.text("hello", new Vec3(7.0, 8.0, 9.0), 0xFF0000FF, 1.0F);
        }

        assertEquals(3, collector.drainGizmos().size());
    }

    @Test
    void projectToScreenMapsClipSpaceToPixels()
    {
        WorldRenderContext center = new WorldRenderContext(null, null, 0.0F, Vec3.ZERO,
                new RecordingSink(calls), position -> new Vec3(0.0, 0.0, 0.5), 1920, 1080);

        WorldRenderContext.ScreenPoint point = center.projectToScreen(Vec3.ZERO);

        assertEquals(960.0F, point.x(), 0.001F);
        assertEquals(540.0F, point.y(), 0.001F);
        assertTrue(point.visible());
    }

    private WorldRenderListener listener(String id, int order)
    {
        return new WorldRenderListener()
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
            public void render(WorldRenderContext ignored)
            {
                calls.add(id);
            }
        };
    }

    private static final class RecordingSink implements WorldRenderSink
    {
        private final List<String> calls;

        private RecordingSink(List<String> calls)
        {
            this.calls = calls;
        }

        @Override
        public void line(Vec3 from, Vec3 to, int argb, float width)
        {
            calls.add("line:" + from + ":" + to + ":" + argb + ":" + width);
        }

        @Override
        public void point(Vec3 position, int argb, float size)
        {
            calls.add("point:" + position + ":" + argb + ":" + size);
        }

        @Override
        public void text(String text, Vec3 position, int argb, float scale)
        {
            calls.add("text:" + text + ":" + position + ":" + argb + ":" + scale);
        }
    }
}
