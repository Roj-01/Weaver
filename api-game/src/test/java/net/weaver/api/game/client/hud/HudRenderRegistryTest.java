package net.weaver.api.game.client.hud;

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

class HudRenderRegistryTest
{
    private final List<String> calls = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        HudRenderRegistry.reset();
        calls.clear();
    }

    @AfterEach
    void tearDown()
    {
        HudRenderRegistry.reset();
    }

    @Test
    void everyStageIsDeliveredInOrder()
    {
        HudRenderRegistry.register(listener("twenty", 20));
        HudRenderRegistry.register(listener("ten", 10));

        for (HudRenderStage stage : HudRenderStage.values())
        {
            HudRenderRegistry.dispatch(context(stage));
        }

        assertEquals(List.of(
                "ten:PRE_HUD", "twenty:PRE_HUD",
                "ten:POST_HUD", "twenty:POST_HUD",
                "ten:CROSSHAIR", "twenty:CROSSHAIR",
                "ten:HOTBAR", "twenty:HOTBAR",
                "ten:POST_GUI", "twenty:POST_GUI"), calls);
    }

    @Test
    void unregisterRemovesOnlyThatListener()
    {
        ClientRegistration first = HudRenderRegistry.register(listener("first", 0));
        HudRenderRegistry.register(listener("second", 0));

        first.unregister();

        assertFalse(first.isActive());
        HudRenderRegistry.dispatch(context(HudRenderStage.HOTBAR));
        assertEquals(List.of("second:HOTBAR"), calls);
        assertEquals(1, HudRenderRegistry.listenerCount());
    }

    @Test
    void duplicateIdsAreRejected()
    {
        HudRenderRegistry.register(listener("same", 0));

        assertThrows(ClientException.class, () -> HudRenderRegistry.register(
                listener("same", 0)));
    }

    @Test
    void oneFailingListenerDoesNotStopLaterListeners()
    {
        HudRenderRegistry.register(new HudRenderListener()
        {
            @Override
            public String id()
            {
                return "bad";
            }

            @Override
            public void onRender(HudRenderContext context)
            {
                throw new IllegalStateException("boom");
            }
        });
        HudRenderRegistry.register(listener("good", 0));

        assertDoesNotThrow(() -> HudRenderRegistry.dispatch(context(HudRenderStage.CROSSHAIR)));

        assertEquals(List.of("good:CROSSHAIR"), calls);
    }

    @Test
    void postGuiReportsScaledSizeWithoutGraphics()
    {
        HudRenderContext context = new HudRenderContext(null, null, null, 320, 240, 0.5F,
                HudRenderStage.POST_GUI);

        assertFalse(context.canDraw());
        assertEquals(320, context.width());
        assertEquals(240, context.height());
        assertTrue(context.width() > 0);
    }

    private HudRenderContext context(HudRenderStage stage)
    {
        return new HudRenderContext(null, null, null, 320, 240, 0.5F, stage);
    }

    private HudRenderListener listener(String id, int order)
    {
        return new HudRenderListener()
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
            public void onRender(HudRenderContext context)
            {
                calls.add(id + ":" + context.stage());
            }
        };
    }
}
