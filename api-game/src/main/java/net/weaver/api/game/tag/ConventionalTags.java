package net.weaver.api.game.tag;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

/**
 * The shared {@code c:} tags: the names mods use to recognise each other's
 * materials. A mod that adds tin ingots puts them in {@code c:ingots/tin} (and
 * so in {@code c:ingots}); a machine that accepts "any ingot" checks
 * {@link #INGOTS}. Weaver ships these tags with the vanilla entries in its
 * built-in data pack {@code mod/weaver}; add your own entries from your mod's
 * {@code data/c/tags/...} files (tags merge, nothing is replaced).
 *
 * <p>Sub-tags follow the pattern {@code c:<group>/<material>}, for example
 * {@code c:ores/iron}, {@code c:storage_blocks/copper}, {@code c:dyes/red}:
 * build one with {@link #item(String)}, {@link #block(String)}, or {@link #fluid(String)}.
 */
public final class ConventionalTags {
    public static final TagKey<Item> INGOTS = item("ingots");
    public static final TagKey<Item> NUGGETS = item("nuggets");
    public static final TagKey<Item> RAW_MATERIALS = item("raw_materials");
    public static final TagKey<Item> GEMS = item("gems");
    public static final TagKey<Item> DUSTS = item("dusts");
    public static final TagKey<Item> RODS = item("rods");
    public static final TagKey<Item> DYES = item("dyes");
    public static final TagKey<Item> SEEDS = item("seeds");
    public static final TagKey<Item> CROPS = item("crops");
    public static final TagKey<Item> TOOLS = item("tools");
    public static final TagKey<Item> BUCKETS = item("buckets");
    public static final TagKey<Item> STRINGS = item("strings");
    public static final TagKey<Item> LEATHERS = item("leathers");
    public static final TagKey<Item> SLIME_BALLS = item("slime_balls");
    public static final TagKey<Item> ORE_ITEMS = item("ores");
    public static final TagKey<Item> STORAGE_BLOCK_ITEMS = item("storage_blocks");
    public static final TagKey<Item> CHEST_ITEMS = item("chests");
    public static final TagKey<Item> GLASS_BLOCK_ITEMS = item("glass_blocks");

    public static final TagKey<Block> ORES = block("ores");
    public static final TagKey<Block> STORAGE_BLOCKS = block("storage_blocks");
    public static final TagKey<Block> CHESTS = block("chests");
    public static final TagKey<Block> BARRELS = block("barrels");
    public static final TagKey<Block> GLASS_BLOCKS = block("glass_blocks");
    public static final TagKey<Block> GLASS_PANES = block("glass_panes");
    public static final TagKey<Block> STONES = block("stones");
    public static final TagKey<Block> COBBLESTONES = block("cobblestones");
    public static final TagKey<Block> SANDS = block("sands");
    public static final TagKey<Block> OBSIDIANS = block("obsidians");

    public static final TagKey<Fluid> WATER = fluid("water");
    public static final TagKey<Fluid> LAVA = fluid("lava");

    private ConventionalTags() {
    }

    /** {@code c:<path>} for items, for example {@code item("ingots/tin")}. */
    public static TagKey<Item> item(String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", path));
    }

    public static TagKey<Block> block(String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", path));
    }

    public static TagKey<Fluid> fluid(String path) {
        return TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath("c", path));
    }
}
