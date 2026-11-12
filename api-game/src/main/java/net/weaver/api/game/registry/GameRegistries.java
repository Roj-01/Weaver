package net.weaver.api.game.registry;

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
import net.weaver.api.registry.RegistryKey;

/**
 * Typed registry keys for the built-in Minecraft registries.
 *
 * <p>Every key uses the {@code minecraft} namespace and the exact resource path
 * the game uses for that registry. The element type is the registry's value
 * type, so a key can be passed directly to the typed
 * {@code RegisterEvent.registry(RegistryKey)} helper without an unchecked cast.
 */
public final class GameRegistries {

    /** The item registry, {@code minecraft:item}. */
    public static final RegistryKey<Item> ITEM = key("item");

    /** The block registry, {@code minecraft:block}. */
    public static final RegistryKey<Block> BLOCK = key("block");

    /** The entity-type registry, {@code minecraft:entity_type}. */
    public static final RegistryKey<EntityType<?>> ENTITY_TYPE = key("entity_type");

    /** The fluid registry, {@code minecraft:fluid}. */
    public static final RegistryKey<Fluid> FLUID = key("fluid");

    /** The mob-effect registry, {@code minecraft:mob_effect}. */
    public static final RegistryKey<MobEffect> MOB_EFFECT = key("mob_effect");

    /** The enchantment registry, {@code minecraft:enchantment}. */
    public static final RegistryKey<Enchantment> ENCHANTMENT = key("enchantment");

    /** The block-entity-type registry, {@code minecraft:block_entity_type}. */
    public static final RegistryKey<BlockEntityType<?>> BLOCK_ENTITY_TYPE =
            key("block_entity_type");

    /** The menu-type registry, {@code minecraft:menu}. */
    public static final RegistryKey<MenuType<?>> MENU = key("menu");

    /** The recipe-type registry, {@code minecraft:recipe_type}. */
    public static final RegistryKey<RecipeType<?>> RECIPE_TYPE = key("recipe_type");

    /** The attribute registry, {@code minecraft:attribute}. */
    public static final RegistryKey<Attribute> ATTRIBUTE = key("attribute");

    /** The particle-type registry, {@code minecraft:particle_type}. */
    public static final RegistryKey<ParticleType<?>> PARTICLE_TYPE = key("particle_type");

    /** The sound-event registry, {@code minecraft:sound_event}. */
    public static final RegistryKey<SoundEvent> SOUND_EVENT = key("sound_event");

    /** The potion registry, {@code minecraft:potion}. */
    public static final RegistryKey<Potion> POTION = key("potion");

    private GameRegistries() {
    }

    private static <T> RegistryKey<T> key(String path) {
        return RegistryKey.of("minecraft", path);
    }
}
