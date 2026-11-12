package net.weaver.api.game.client.entity;

import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.pig.Pig;
import net.weaver.api.client.ClientException;
import net.weaver.api.client.ClientRegistration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityRendererRegistryTest
{
    private EntityRendererProvider.Context context;

    @BeforeAll
    static void bootstrapGameClasses()
    {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void setUp()
    {
        EntityRendererRegistry.reset();
        context = new EntityRendererProvider.Context(null, null, null, null, null, null, null,
                null, null, null, null);
    }

    @AfterEach
    void tearDown()
    {
        EntityRendererRegistry.reset();
    }

    @Test
    void mergeCopiesTheVanillaMapAndAddsRegisteredRenderers()
    {
        EntityRenderer<?, ?> vanillaRenderer = new FakeRenderer<Entity>(context);
        Map<EntityType<?>, EntityRenderer<?, ?>> vanilla = new LinkedHashMap<>();
        vanilla.put(EntityTypes.COW, vanillaRenderer);

        EntityRenderer<Pig, EntityRenderState> custom = new FakeRenderer<Pig>(context);
        EntityRendererRegistry.register(EntityTypes.PIG,
                providerContext -> custom);

        Map<EntityType<?>, EntityRenderer<?, ?>> merged =
                EntityRendererRegistry.merge(vanilla, context);

        assertEquals(1, vanilla.size());
        assertFalse(vanilla.containsKey(EntityTypes.PIG));
        assertEquals(2, merged.size());
        assertSame(vanillaRenderer, merged.get(EntityTypes.COW));
        assertSame(custom, merged.get(EntityTypes.PIG));
    }

    @Test
    void unregisterRemovesOnlyThatProvider()
    {
        ClientRegistration registration = EntityRendererRegistry.register(EntityTypes.PIG,
                providerContext -> new FakeRenderer<Pig>(context));
        EntityRendererRegistry.register(EntityTypes.COW,
                providerContext -> new FakeRenderer<Cow>(context));

        registration.unregister();

        assertFalse(registration.isActive());
        Map<EntityType<?>, EntityRenderer<?, ?>> vanilla = new LinkedHashMap<>();
        Map<EntityType<?>, EntityRenderer<?, ?>> merged =
                EntityRendererRegistry.merge(vanilla, context);
        assertEquals(1, EntityRendererRegistry.registrationCount());
        assertTrue(merged.containsKey(EntityTypes.COW));
        assertFalse(merged.containsKey(EntityTypes.PIG));
    }

    @Test
    void failingProviderKeepsTheVanillaEntry()
    {
        EntityRenderer<?, ?> vanillaRenderer = new FakeRenderer<Entity>(context);
        Map<EntityType<?>, EntityRenderer<?, ?>> vanilla = new LinkedHashMap<>();
        vanilla.put(EntityTypes.PIG, vanillaRenderer);
        EntityRendererRegistry.register(EntityTypes.PIG, providerContext ->
        {
            throw new IllegalStateException("boom");
        });

        Map<EntityType<?>, EntityRenderer<?, ?>> merged =
                assertDoesNotThrow(() -> EntityRendererRegistry.merge(vanilla, context));

        assertSame(vanillaRenderer, merged.get(EntityTypes.PIG));
    }

    @Test
    void nullRendererKeepsTheVanillaEntry()
    {
        EntityRenderer<?, ?> vanillaRenderer = new FakeRenderer<Entity>(context);
        Map<EntityType<?>, EntityRenderer<?, ?>> vanilla = new LinkedHashMap<>();
        vanilla.put(EntityTypes.PIG, vanillaRenderer);
        EntityRendererRegistry.register(EntityTypes.PIG, providerContext -> null);

        Map<EntityType<?>, EntityRenderer<?, ?>> merged =
                EntityRendererRegistry.merge(vanilla, context);

        assertSame(vanillaRenderer, merged.get(EntityTypes.PIG));
    }

    @Test
    void duplicateRegistrationsAreRejected()
    {
        EntityRendererRegistry.register(EntityTypes.PIG,
                providerContext -> new FakeRenderer<Pig>(context));

        assertThrows(ClientException.class, () -> EntityRendererRegistry.register(
                EntityTypes.PIG, providerContext -> new FakeRenderer<Pig>(context)));
    }

    private static final class FakeRenderer<T extends Entity>
            extends EntityRenderer<T, EntityRenderState>
    {
        private FakeRenderer(EntityRendererProvider.Context context)
        {
            super(context);
        }

        @Override
        public EntityRenderState createRenderState()
        {
            return new EntityRenderState();
        }
    }
}
