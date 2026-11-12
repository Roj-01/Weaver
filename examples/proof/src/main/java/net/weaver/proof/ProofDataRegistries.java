package net.weaver.proof;

import java.util.Map;
import net.weaver.api.registry.DataRegistry;
import net.weaver.api.registry.DataRegistryManager;
import net.weaver.api.registry.RegistryKey;
import net.weaver.api.registry.TagKey;
import net.weaver.api.registry.TagView;

/**
 * A data registry filled from the proof's data pack ({@code examples/proof/datapack},
 * copied into the server world by {@code run-server.ps1}). The mod only
 * creates the registry; the values come from
 * {@code data/weaverproof/weaver/registry/weaverproof/gems/*.json} through the
 * server's real data pack load.
 */
final class ProofDataRegistries {
    record Gem(String color, int hardness) {
    }

    static volatile DataRegistry<Gem> gems;

    private ProofDataRegistries() {
    }

    static void register() {
        if (gems == null) {
            gems = DataRegistryManager.create("weaverproof:gems", Gem.class, ProofDataRegistries::decode);
        }
    }

    private static Gem decode(String id, Map<String, Object> data) {
        return new Gem((String) data.get("color"), ((Number) data.get("hardness")).intValue());
    }

    /** Checks the pack-loaded values; logs one check line per assertion. */
    static void check(GameEventsProof proof) {
        DataRegistry<Gem> registry = gems;
        if (registry == null) {
            proof.check(false, "data registry not created");
            return;
        }
        Gem ruby = registry.get("weaverproof:ruby");
        Gem sapphire = registry.get("weaverproof:sapphire");
        proof.check(ruby != null && "red".equals(ruby.color()) && ruby.hardness() == 7,
                "data registry pack entry ruby=" + ruby);
        proof.check(sapphire != null && "blue".equals(sapphire.color()), "data registry pack entry sapphire=" + sapphire);
        TagView<Gem> shiny = registry.tag(TagKey.of(RegistryKey.parse("weaverproof:gems"), "weaverproof:shiny"));
        proof.check(shiny.size() == 2 && shiny.contains(ruby) && shiny.contains(sapphire),
                "data registry pack tag shiny=" + shiny.values());
    }

    /**
     * Checks the proof mod jar as a built-in data pack: its own data registry
     * entry and vanilla recipe load, it sits between vanilla and the world
     * pack, and the world pack's ruby (red) overrides the jar's (pink).
     */
    static void checkModPack(GameEventsProof proof, net.minecraft.server.MinecraftServer server) {
        DataRegistry<Gem> registry = gems;
        Gem emerald = registry == null ? null : registry.get("weaverproof:emerald");
        proof.check(emerald != null && "green".equals(emerald.color()), "mod pack data registry entry emerald=" + emerald);
        Gem ruby = registry == null ? null : registry.get("weaverproof:ruby");
        proof.check(ruby != null && "red".equals(ruby.color()), "world pack overrides mod pack ruby=" + ruby);
        var recipe = server.getRecipeManager().byKey(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.RECIPE,
                net.minecraft.resources.Identifier.parse("weaverproof:proof_gem")));
        proof.check(recipe.isPresent(), "mod pack vanilla recipe present=" + recipe.isPresent());
        // Load conditions: Weaver, NeoForge, and Fabric forms; a failing file is not loaded at all.
        java.util.function.Predicate<String> hasRecipe = path -> server.getRecipeManager().byKey(
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE,
                        net.minecraft.resources.Identifier.fromNamespaceAndPath("weaverproof", path))).isPresent();
        boolean present = hasRecipe.test("conditional_present");
        boolean itemExists = hasRecipe.test("conditional_item");
        boolean absent = !hasRecipe.test("conditional_absent");
        boolean fabric = !hasRecipe.test("conditional_fabric");
        boolean tagOff = server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ITEM)
                .get(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                        net.minecraft.resources.Identifier.fromNamespaceAndPath("weaverproof", "conditional_off")))
                .map(set -> set.size() == 0).orElse(true);
        proof.check(present && itemExists && absent && fabric && tagOff, "load conditions present=" + present
                + " itemExists=" + itemExists + " absent=" + absent + " fabric=" + fabric + " tagOff=" + tagOff);
        // A BlockItem registered directly (not through Items) is linked to its block.
        net.minecraft.world.level.block.Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("weaverproof", "proof_block"));
        net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("weaverproof", "proof_block"));
        boolean linked = block.asItem() == item && item != net.minecraft.world.item.Items.AIR
                && net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.getId(block.defaultBlockState()) >= 0;
        proof.check(linked, "mod block item linked=" + linked + " asItem=" + block.asItem()
                + " stateId=" + net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.getId(block.defaultBlockState()));
        java.util.List<String> selected = java.util.List.copyOf(server.getPackRepository().getSelectedIds());
        int vanilla = selected.indexOf("vanilla");
        int mod = selected.indexOf("mod/weaverproof");
        int world = selected.indexOf("file/weaverproof_data");
        proof.check(vanilla >= 0 && mod > vanilla && world > mod, "mod pack order selected=" + selected);
        // Mod packs are known packs: their world data (this mod has worldgen) is stable, so the world
        // does not open behind the experimental settings warning.
        var modPack = server.getPackRepository().getPack("mod/weaverproof");
        boolean known = modPack != null && modPack.location().knownPackInfo().isPresent();
        boolean stable = server.getWorldData().worldGenSettingsLifecycle() == com.mojang.serialization.Lifecycle.stable();
        proof.check(known && stable, "mod packs known=" + known + " worldStable=" + stable
                + " lifecycle=" + server.getWorldData().worldGenSettingsLifecycle());
    }
}
