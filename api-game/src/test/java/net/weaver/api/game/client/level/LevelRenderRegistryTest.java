package net.weaver.api.game.client.level;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
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

class LevelRenderRegistryTest
{
    private static final LevelRenderStage[] STAGES =
            {
                    LevelRenderStage.START,
                    LevelRenderStage.SKY,
                    LevelRenderStage.ENTITIES,
                    LevelRenderStage.BLOCK_ENTITIES,
                    LevelRenderStage.END
            };

    private final List<String> calls = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        LevelRenderRegistry.reset();
        calls.clear();
    }

    @AfterEach
    void tearDown()
    {
        LevelRenderRegistry.reset();
    }

    @Test
    void everyStageIsDeliveredInOrder()
    {
        LevelRenderRegistry.register(listener("twenty", 20));
        LevelRenderRegistry.register(listener("ten", 10));

        for (LevelRenderStage stage : STAGES)
        {
            LevelRenderRegistry.dispatch(context(stage));
        }

        assertEquals(List.of(
                "ten:START", "twenty:START",
                "ten:SKY", "twenty:SKY",
                "ten:ENTITIES", "twenty:ENTITIES",
                "ten:BLOCK_ENTITIES", "twenty:BLOCK_ENTITIES",
                "ten:END", "twenty:END"), calls);
    }

    @Test
    void unregisterRemovesOnlyThatListener()
    {
        ClientRegistration first = LevelRenderRegistry.register(listener("first", 0));
        LevelRenderRegistry.register(listener("second", 0));

        first.unregister();

        assertFalse(first.isActive());
        LevelRenderRegistry.dispatch(context(LevelRenderStage.ENTITIES));
        assertEquals(List.of("second:ENTITIES"), calls);
        assertEquals(1, LevelRenderRegistry.listenerCount());
    }

    @Test
    void duplicateIdsAreRejected()
    {
        LevelRenderRegistry.register(listener("same", 0));

        assertThrows(ClientException.class, () -> LevelRenderRegistry.register(
                listener("same", 0)));
    }

    @Test
    void oneFailingListenerDoesNotStopLaterListeners()
    {
        LevelRenderRegistry.register(new LevelRenderListener()
        {
            @Override
            public String id()
            {
                return "bad";
            }

            @Override
            public void onRender(LevelRenderContext context)
            {
                throw new IllegalStateException("boom");
            }
        });
        LevelRenderRegistry.register(listener("good", 0));

        assertDoesNotThrow(() -> LevelRenderRegistry.dispatch(
                context(LevelRenderStage.START)));

        assertEquals(List.of("good:START"), calls);
    }

    @Test
    void entityStagesExposePoseStackAndCollector()
    {
        LevelRenderContext entity = context(LevelRenderStage.ENTITIES);
        LevelRenderContext start = context(LevelRenderStage.START);

        assertTrue(entity.hasEntityPass());
        assertFalse(start.hasEntityPass());
        assertTrue(entity.poseStack() != null);
        assertTrue(start.poseStack() == null);
    }

    private LevelRenderContext context(LevelRenderStage stage)
    {
        boolean entity = stage == LevelRenderStage.ENTITIES
                || stage == LevelRenderStage.BLOCK_ENTITIES;
        return new LevelRenderContext(null, new CameraRenderState(), new LevelRenderState(),
                entity ? new PoseStack() : null, null, 0.5F, stage, null);
    }

    private LevelRenderListener listener(String id, int order)
    {
        return new LevelRenderListener()
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
            public void onRender(LevelRenderContext context)
            {
                calls.add(id + ":" + context.stage());
            }
        };
    }
}
