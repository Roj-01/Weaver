package net.weaver.api.game;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;
import net.weaver.api.game.registry.GameRegistries;
import net.weaver.api.registry.RegistryKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GameRegistriesTest {

    @Test
    void typedConstantsUseVerifiedMinecraftPaths() {
        assertKey(GameRegistries.ITEM, "item");
        assertKey(GameRegistries.BLOCK, "block");
        assertKey(GameRegistries.ENTITY_TYPE, "entity_type");
        assertKey(GameRegistries.FLUID, "fluid");
        assertKey(GameRegistries.MOB_EFFECT, "mob_effect");
        assertKey(GameRegistries.ENCHANTMENT, "enchantment");
        assertKey(GameRegistries.BLOCK_ENTITY_TYPE, "block_entity_type");
        assertKey(GameRegistries.MENU, "menu");
        assertKey(GameRegistries.RECIPE_TYPE, "recipe_type");
        assertKey(GameRegistries.ATTRIBUTE, "attribute");
        assertKey(GameRegistries.PARTICLE_TYPE, "particle_type");
        assertKey(GameRegistries.SOUND_EVENT, "sound_event");
        assertKey(GameRegistries.POTION, "potion");
    }

    @Test
    void constantsKeepTheirElementType() {
        RegistryKey<Item> item = GameRegistries.ITEM;
        RegistryKey<Block> block = GameRegistries.BLOCK;
        RegistryKey<EntityType<?>> entityType = GameRegistries.ENTITY_TYPE;
        RegistryKey<Fluid> fluid = GameRegistries.FLUID;
        RegistryKey<MobEffect> mobEffect = GameRegistries.MOB_EFFECT;
        RegistryKey<Enchantment> enchantment = GameRegistries.ENCHANTMENT;
        RegistryKey<BlockEntityType<?>> blockEntityType = GameRegistries.BLOCK_ENTITY_TYPE;
        RegistryKey<MenuType<?>> menu = GameRegistries.MENU;
        RegistryKey<RecipeType<?>> recipeType = GameRegistries.RECIPE_TYPE;
        RegistryKey<Attribute> attribute = GameRegistries.ATTRIBUTE;
        RegistryKey<ParticleType<?>> particleType = GameRegistries.PARTICLE_TYPE;
        RegistryKey<SoundEvent> soundEvent = GameRegistries.SOUND_EVENT;
        RegistryKey<Potion> potion = GameRegistries.POTION;

        assertNotNull(item);
        assertNotNull(block);
        assertNotNull(entityType);
        assertNotNull(fluid);
        assertNotNull(mobEffect);
        assertNotNull(enchantment);
        assertNotNull(blockEntityType);
        assertNotNull(menu);
        assertNotNull(recipeType);
        assertNotNull(attribute);
        assertNotNull(particleType);
        assertNotNull(soundEvent);
        assertNotNull(potion);
    }

    private static void assertKey(RegistryKey<?> key, String path) {
        assertNotNull(key);
        assertEquals("minecraft", key.namespace());
        assertEquals(path, key.path());
        assertEquals("minecraft:" + path, key.id());
    }
}
