package net.weaver.api.game.client.fog;

import net.minecraft.client.renderer.fog.FogData;
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

class FogRenderRegistryTest
{
    private final List<String> calls = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        FogRenderRegistry.reset();
        calls.clear();
    }

    @AfterEach
    void tearDown()
    {
        FogRenderRegistry.reset();
    }

    @Test
    void listenersRunInOrderAndCanMutateFogData()
    {
        FogRenderRegistry.register(new FogRenderListener()
        {
            @Override
            public String id()
            {
                return "twenty";
            }

            @Override
            public int order()
            {
                return 20;
            }

            @Override
            public void onSetupFog(FogRenderContext context)
            {
                calls.add("twenty");
                context.data().environmentalEnd = 99.0F;
            }
        });
        FogRenderRegistry.register(new FogRenderListener()
        {
            @Override
            public String id()
            {
                return "ten";
            }

            @Override
            public int order()
            {
                return 10;
            }

            @Override
            public void onSetupFog(FogRenderContext context)
            {
                calls.add("ten");
                context.data().environmentalStart = 7.0F;
            }
        });

        FogData data = new FogData();
        FogRenderRegistry.dispatch(new FogRenderContext(null, data, null, 12, 0.5F, null));

        assertEquals(List.of("ten", "twenty"), calls);
        assertEquals(7.0F, data.environmentalStart, 0.0001F);
        assertEquals(99.0F, data.environmentalEnd, 0.0001F);
    }

    @Test
    void unregisterRemovesOnlyThatListener()
    {
        ClientRegistration first = FogRenderRegistry.register(listener("first"));
        FogRenderRegistry.register(listener("second"));

        first.unregister();

        assertFalse(first.isActive());
        FogRenderRegistry.dispatch(new FogRenderContext(null, new FogData(), null, 12, 0.0F,
                null));
        assertEquals(List.of("second"), calls);
        assertEquals(1, FogRenderRegistry.listenerCount());
    }

    @Test
    void duplicateIdsAreRejected()
    {
        FogRenderRegistry.register(listener("same"));

        assertThrows(ClientException.class, () -> FogRenderRegistry.register(listener("same")));
    }

    @Test
    void oneFailingListenerDoesNotStopLaterListeners()
    {
        FogRenderRegistry.register(new FogRenderListener()
        {
            @Override
            public String id()
            {
                return "bad";
            }

            @Override
            public void onSetupFog(FogRenderContext context)
            {
                throw new IllegalStateException("boom");
            }
        });
        FogRenderRegistry.register(listener("good"));

        assertDoesNotThrow(() -> FogRenderRegistry.dispatch(new FogRenderContext(null,
                new FogData(), null, 12, 0.0F, null)));

        assertEquals(List.of("good"), calls);
    }

    private FogRenderListener listener(String id)
    {
        return new FogRenderListener()
        {
            @Override
            public String id()
            {
                return id;
            }

            @Override
            public void onSetupFog(FogRenderContext context)
            {
                calls.add(id);
            }
        };
    }
}
