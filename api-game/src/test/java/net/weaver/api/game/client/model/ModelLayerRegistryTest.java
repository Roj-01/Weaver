package net.weaver.api.game.client.model;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.resources.Identifier;
import net.weaver.api.client.ClientException;
import net.weaver.api.client.ClientRegistration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelLayerRegistryTest
{
    private ModelLayerLocation location;
    private LayerDefinition definition;

    @BeforeEach
    void setUp()
    {
        ModelLayerRegistry.reset();
        location = new ModelLayerLocation(
                Identifier.fromNamespaceAndPath("weaver", "model_layer_test"), "main");
        definition = LayerDefinition.create(new MeshDefinition(), 64, 64);
    }

    @AfterEach
    void tearDown()
    {
        ModelLayerRegistry.reset();
    }

    @Test
    void mergeCopiesTheVanillaMapAndAddsRegisteredDefinitions()
    {
        ModelLayerLocation vanillaLocation = new ModelLayerLocation(
                Identifier.fromNamespaceAndPath("minecraft", "player"), "main");
        LayerDefinition vanillaDefinition = LayerDefinition.create(new MeshDefinition(), 64, 64);
        Map<ModelLayerLocation, LayerDefinition> vanilla = new LinkedHashMap<>();
        vanilla.put(vanillaLocation, vanillaDefinition);

        ModelLayerRegistry.register(location, definition);
        Map<ModelLayerLocation, LayerDefinition> merged = ModelLayerRegistry.merge(vanilla);

        assertEquals(1, vanilla.size());
        assertFalse(vanilla.containsKey(location));
        assertEquals(2, merged.size());
        assertSame(vanillaDefinition, merged.get(vanillaLocation));
        assertSame(definition, merged.get(location));
    }

    @Test
    void unregisterRemovesOnlyThatDefinition()
    {
        ClientRegistration registration = ModelLayerRegistry.register(location, definition);

        registration.unregister();

        assertFalse(registration.isActive());
        assertEquals(0, ModelLayerRegistry.registrationCount());
        assertTrue(ModelLayerRegistry.merge(emptyRoots()).isEmpty());
    }

    @Test
    void duplicateLocationsAreRejected()
    {
        ModelLayerRegistry.register(location, definition);

        assertThrows(ClientException.class, () -> ModelLayerRegistry.register(location,
                LayerDefinition.create(new MeshDefinition(), 64, 64)));
    }

    @Test
    void resetClearsQueuedDefinitions()
    {
        ModelLayerRegistry.register(location, definition);

        ModelLayerRegistry.reset();

        assertEquals(0, ModelLayerRegistry.registrationCount());
        assertTrue(ModelLayerRegistry.merge(emptyRoots()).isEmpty());
    }

    private static Map<ModelLayerLocation, LayerDefinition> emptyRoots()
    {
        return new LinkedHashMap<>();
    }
}
