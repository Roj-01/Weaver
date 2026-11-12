package net.weaver.api.game.client.camera;

import net.minecraft.client.renderer.state.level.CameraRenderState;
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

class CameraRenderRegistryTest
{
    private final List<String> calls = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        CameraRenderRegistry.reset();
        calls.clear();
    }

    @AfterEach
    void tearDown()
    {
        CameraRenderRegistry.reset();
    }

    @Test
    void listenersRunInOrderAndCanMutateCameraState()
    {
        CameraRenderRegistry.register(new CameraRenderListener()
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
            public void onExtractCamera(CameraRenderContext context)
            {
                calls.add("twenty");
                context.state().xRot = 45.0F;
            }
        });
        CameraRenderRegistry.register(new CameraRenderListener()
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
            public void onExtractCamera(CameraRenderContext context)
            {
                calls.add("ten");
                context.state().yRot = 90.0F;
            }
        });

        CameraRenderState state = new CameraRenderState();
        CameraRenderRegistry.dispatch(new CameraRenderContext(null, state, null, 0.5F, null));

        assertEquals(List.of("ten", "twenty"), calls);
        assertEquals(90.0F, state.yRot, 0.0001F);
        assertEquals(45.0F, state.xRot, 0.0001F);
    }

    @Test
    void unregisterRemovesOnlyThatListener()
    {
        ClientRegistration first = CameraRenderRegistry.register(listener("first"));
        CameraRenderRegistry.register(listener("second"));

        first.unregister();

        assertFalse(first.isActive());
        CameraRenderRegistry.dispatch(new CameraRenderContext(null, new CameraRenderState(),
                null, 0.0F, null));
        assertEquals(List.of("second"), calls);
        assertEquals(1, CameraRenderRegistry.listenerCount());
    }

    @Test
    void duplicateIdsAreRejected()
    {
        CameraRenderRegistry.register(listener("same"));

        assertThrows(ClientException.class, () -> CameraRenderRegistry.register(
                listener("same")));
    }

    @Test
    void oneFailingListenerDoesNotStopLaterListeners()
    {
        CameraRenderRegistry.register(new CameraRenderListener()
        {
            @Override
            public String id()
            {
                return "bad";
            }

            @Override
            public void onExtractCamera(CameraRenderContext context)
            {
                throw new IllegalStateException("boom");
            }
        });
        CameraRenderRegistry.register(listener("good"));

        assertDoesNotThrow(() -> CameraRenderRegistry.dispatch(new CameraRenderContext(null,
                new CameraRenderState(), null, 0.0F, null)));

        assertEquals(List.of("good"), calls);
    }

    private CameraRenderListener listener(String id)
    {
        return new CameraRenderListener()
        {
            @Override
            public String id()
            {
                return id;
            }

            @Override
            public void onExtractCamera(CameraRenderContext context)
            {
                calls.add(id);
            }
        };
    }
}
