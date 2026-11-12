package net.weaver.api.game.client.hand;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
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

class HandRenderRegistryTest
{
    private final List<String> calls = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        HandRenderRegistry.reset();
        calls.clear();
    }

    @AfterEach
    void tearDown()
    {
        HandRenderRegistry.reset();
    }

    @Test
    void listenersRunInOrderWithRegistrationTieBreak()
    {
        HandRenderRegistry.register(listener("twenty", 20, HandRenderResult.CONTINUE));
        HandRenderRegistry.register(listener("ten-first", 10, HandRenderResult.CONTINUE));
        HandRenderRegistry.register(listener("ten-second", 10, HandRenderResult.CONTINUE));

        HandRenderRegistry.dispatch(context(null, HandRenderPhase.PRE));

        assertEquals(List.of("ten-first:PRE", "ten-second:PRE", "twenty:PRE"), calls);
    }

    @Test
    void cancelStopsAtFirstCancellingListener()
    {
        HandRenderRegistry.register(listener("first", 0, HandRenderResult.CONTINUE));
        HandRenderRegistry.register(listener("cancel", 1, HandRenderResult.CANCEL));
        HandRenderRegistry.register(listener("never", 2, HandRenderResult.CONTINUE));

        boolean cancelled = HandRenderRegistry.dispatch(
                context(InteractionHand.MAIN_HAND, HandRenderPhase.PRE));

        assertTrue(cancelled);
        assertEquals(List.of("first:PRE", "cancel:PRE"), calls);
    }

    @Test
    void postPhaseAlwaysContinues()
    {
        HandRenderRegistry.register(listener("a", 0, HandRenderResult.CANCEL));
        HandRenderRegistry.register(listener("b", 1, HandRenderResult.CONTINUE));

        boolean cancelled = HandRenderRegistry.dispatch(context(null, HandRenderPhase.POST));

        assertFalse(cancelled);
        assertEquals(List.of("a:POST", "b:POST"), calls);
    }

    @Test
    void unregisterRemovesOnlyThatListener()
    {
        ClientRegistration first = HandRenderRegistry.register(
                listener("first", 0, HandRenderResult.CONTINUE));
        HandRenderRegistry.register(listener("second", 0, HandRenderResult.CONTINUE));

        first.unregister();

        assertFalse(first.isActive());
        HandRenderRegistry.dispatch(context(null, HandRenderPhase.PRE));
        assertEquals(List.of("second:PRE"), calls);
        assertEquals(1, HandRenderRegistry.listenerCount());
    }

    @Test
    void duplicateIdsAreRejected()
    {
        HandRenderRegistry.register(listener("same", 0, HandRenderResult.CONTINUE));

        assertThrows(ClientException.class, () -> HandRenderRegistry.register(
                listener("same", 0, HandRenderResult.CONTINUE)));
        assertThrows(ClientException.class, () -> HandRenderRegistry.register(
                listener(" ", 0, HandRenderResult.CONTINUE)));
    }

    @Test
    void oneFailingListenerDoesNotStopLaterListeners()
    {
        HandRenderRegistry.register(new HandRenderListener()
        {
            @Override
            public String id()
            {
                return "bad";
            }

            @Override
            public HandRenderResult onRender(HandRenderContext context)
            {
                throw new IllegalStateException("boom");
            }
        });
        HandRenderRegistry.register(listener("good", 0, HandRenderResult.CONTINUE));

        assertDoesNotThrow(() -> HandRenderRegistry.dispatch(
                context(null, HandRenderPhase.PRE)));

        assertEquals(List.of("good:PRE"), calls);
    }

    @Test
    void stateListenersCanMutateTheRenderState()
    {
        FirstPersonHandsAndItemsRenderState state = new FirstPersonHandsAndItemsRenderState();
        HandRenderRegistry.register(new HandRenderListener()
        {
            @Override
            public String id()
            {
                return "state";
            }

            @Override
            public void onExtractState(HandRenderStateContext context)
            {
                context.state().mainHandHeight = 0.75F;
                calls.add("state");
            }
        });

        HandRenderRegistry.dispatchState(new HandRenderStateContext(null, state, 0.5F, null));

        assertEquals(0.75F, state.mainHandHeight, 0.0001F);
        assertEquals(List.of("state"), calls);
    }

    private HandRenderContext context(InteractionHand hand, HandRenderPhase phase)
    {
        return new HandRenderContext(hand, null, new PoseStack(), null,
                new PlayerRenderState(), new FirstPersonHandsAndItemsRenderState(), 0.5F, null,
                phase);
    }

    private HandRenderListener listener(String id, int order, HandRenderResult result)
    {
        return new HandRenderListener()
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
            public HandRenderResult onRender(HandRenderContext context)
            {
                calls.add(id + ":" + context.phase());
                return result;
            }
        };
    }
}
