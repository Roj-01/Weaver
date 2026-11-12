package net.weaver.api.game.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.SharedConstants;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.LivingEntity;
import net.weaver.api.client.ClientRegistration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityLayerRegistryTest
{
    private EntityRendererProvider.Context context;
    private FakeLivingRenderer renderer;

    @BeforeAll
    static void bootstrapGameClasses()
    {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void setUp()
    {
        EntityLayerRegistry.reset();
        context = new EntityRendererProvider.Context(null, null, null, null, null, null, null,
                null, null, null, null);
        renderer = new FakeLivingRenderer(context);
    }

    @AfterEach
    void tearDown()
    {
        EntityLayerRegistry.reset();
    }

    @Test
    void matchingRendererTypeAddsLayer()
    {
        List<RenderLayer<?, ?>> added = new ArrayList<>();
        EntityLayerRegistry.register(FakeLivingRenderer.class,
                (target, providerContext) -> fakeLayer(target));

        EntityLayerRegistry.attach(renderer, context, layer ->
        {
            added.add(layer);
            return true;
        });

        assertEquals(1, added.size());
        assertTrue(added.get(0) instanceof FakeLayer);
    }

    @Test
    void nonMatchingRendererTypeIsIgnored()
    {
        List<RenderLayer<?, ?>> added = new ArrayList<>();
        EntityLayerRegistry.register(OtherLivingRenderer.class,
                (target, providerContext) -> fakeLayer(target));

        EntityLayerRegistry.attach(renderer, context, layer ->
        {
            added.add(layer);
            return true;
        });

        assertTrue(added.isEmpty());
    }

    @Test
    void unregisterStopsFutureAttachments()
    {
        ClientRegistration registration = EntityLayerRegistry.register(FakeLivingRenderer.class,
                (target, providerContext) -> fakeLayer(target));

        registration.unregister();

        List<RenderLayer<?, ?>> added = new ArrayList<>();
        EntityLayerRegistry.attach(renderer, context, layer ->
        {
            added.add(layer);
            return true;
        });

        assertFalse(registration.isActive());
        assertTrue(added.isEmpty());
        assertEquals(0, EntityLayerRegistry.registrationCount());
    }

    @Test
    void oneFailingFactoryDoesNotStopLaterFactories()
    {
        EntityLayerRegistry.register(FakeLivingRenderer.class, (target, providerContext) ->
        {
            throw new IllegalStateException("boom");
        });
        EntityLayerRegistry.register(FakeLivingRenderer.class,
                (target, providerContext) -> fakeLayer(target));

        List<RenderLayer<?, ?>> added = new ArrayList<>();
        assertDoesNotThrow(() -> EntityLayerRegistry.attach(renderer, context, layer ->
        {
            added.add(layer);
            return true;
        }));

        assertEquals(1, added.size());
    }

    @Test
    void nullLayerIsSkipped()
    {
        EntityLayerRegistry.register(FakeLivingRenderer.class,
                (target, providerContext) -> null);

        List<RenderLayer<?, ?>> added = new ArrayList<>();
        EntityLayerRegistry.attach(renderer, context, layer ->
        {
            added.add(layer);
            return true;
        });

        assertTrue(added.isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static RenderLayer<?, ?> fakeLayer(LivingEntityRenderer<?, ?, ?> renderer)
    {
        RenderLayerParent<LivingEntityRenderState,
                EntityModel<LivingEntityRenderState>> parent =
                (RenderLayerParent<LivingEntityRenderState,
                        EntityModel<LivingEntityRenderState>>) renderer;
        return new FakeLayer(parent);
    }

    private static class FakeLivingRenderer
            extends LivingEntityRenderer<LivingEntity, LivingEntityRenderState,
            EntityModel<LivingEntityRenderState>>
    {
        private FakeLivingRenderer(EntityRendererProvider.Context context)
        {
            super(context, null, 0.0F);
        }

        @Override
        public LivingEntityRenderState createRenderState()
        {
            return new LivingEntityRenderState();
        }

        @Override
        public Identifier getTextureLocation(LivingEntityRenderState state)
        {
            return null;
        }
    }

    private static final class OtherLivingRenderer extends FakeLivingRenderer
    {
        private OtherLivingRenderer(EntityRendererProvider.Context context)
        {
            super(context);
        }
    }

    private static final class FakeLayer
            extends RenderLayer<LivingEntityRenderState, EntityModel<LivingEntityRenderState>>
    {
        private FakeLayer(RenderLayerParent<LivingEntityRenderState,
                EntityModel<LivingEntityRenderState>> parent)
        {
            super(parent);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light,
                           LivingEntityRenderState state, float partialTick, float ageInTicks)
        {
        }
    }
}
