package net.weaver.clientproof;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.weaver.api.game.creative.CreativeTabItems;
import net.weaver.api.game.creative.ModCreativeTabs;

/** A mod creative tab, registered on both sides; {@link ClientCreativeTabProof} pages to it. */
final class ProofCreativeTab {
    private static CreativeModeTab tab;

    private ProofCreativeTab() {
    }

    static void register() {
        tab = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
                Identifier.fromNamespaceAndPath("weaverclientproof", "proof_tab"),
                ModCreativeTabs.builder()
                        .title(Component.translatable("itemGroup.weaverclientproof"))
                        .icon(ProofCreativeTab::icon)
                        .displayItems(ModCreativeTabs.items(ProofCreativeTab::items))
                        .build());
    }

    static CreativeModeTab tab() {
        return tab;
    }

    private static ItemStack icon() {
        return new ItemStack(Items.AMETHYST_SHARD);
    }

    private static void items(CreativeTabItems items) {
        items.accept(Items.AMETHYST_SHARD);
    }
}
