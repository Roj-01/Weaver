package net.weaver.api.game.client.sky;

import net.minecraft.client.renderer.state.level.SkyRenderState;
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

class SkyRenderRegistryTest
{
    private final List<String> calls = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        SkyRenderRegistry.reset();
        calls.clear();
    }

    @AfterEach
    void tearDown()
    {
        SkyRenderRegistry.reset();
    }

    @Test
    void listenersRunInOrderAndCanMutateSkyState()
    {
        SkyRenderRegistry.register(new SkyRenderListener()
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
            public void onExtractSky(SkyRenderContext context)
            {
                calls.add("twenty");
                context.state().starBrightness = 0.8F;
            }
        });
        SkyRenderRegistry.register(new SkyRenderListener()
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
            public void onExtractSky(SkyRenderContext context)
            {
                calls.add("ten");
                context.state().rainBrightness = 0.25F;
            }
        });

        SkyRenderState state = new SkyRenderState();
        SkyRenderRegistry.dispatch(new SkyRenderContext(null, 0.5F, null, state, null));

        assertEquals(List.of("ten", "twenty"), calls);
        assertEquals(0.25F, state.rainBrightness, 0.0001F);
        assertEquals(0.8F, state.starBrightness, 0.0001F);
    }

    @Test
    void unregisterRemovesOnlyThatListener()
    {
        ClientRegistration first = SkyRenderRegistry.register(listener("first"));
        SkyRenderRegistry.register(listener("second"));

        first.unregister();

        assertFalse(first.isActive());
        SkyRenderRegistry.dispatch(new SkyRenderContext(null, 0.0F, null,
                new SkyRenderState(), null));
        assertEquals(List.of("second"), calls);
        assertEquals(1, SkyRenderRegistry.listenerCount());
    }

    @Test
    void duplicateIdsAreRejected()
    {
        SkyRenderRegistry.register(listener("same"));

        assertThrows(ClientException.class, () -> SkyRenderRegistry.register(listener("same")));
    }

    @Test
    void oneFailingListenerDoesNotStopLaterListeners()
    {
        SkyRenderRegistry.register(new SkyRenderListener()
        {
            @Override
            public String id()
            {
                return "bad";
            }

            @Override
            public void onExtractSky(SkyRenderContext context)
            {
                throw new IllegalStateException("boom");
            }
        });
        SkyRenderRegistry.register(listener("good"));

        assertDoesNotThrow(() -> SkyRenderRegistry.dispatch(new SkyRenderContext(null, 0.0F,
                null, new SkyRenderState(), null)));

        assertEquals(List.of("good"), calls);
    }

    private SkyRenderListener listener(String id)
    {
        return new SkyRenderListener()
        {
            @Override
            public String id()
            {
                return id;
            }

            @Override
            public void onExtractSky(SkyRenderContext context)
            {
                calls.add(id);
            }
        };
    }
}
