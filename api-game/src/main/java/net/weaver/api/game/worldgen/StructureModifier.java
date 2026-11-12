package net.weaver.api.game.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * One structure change, read from a data pack file under
 * {@code data/<namespace>/weaver/structure_modifiers/}. Mods add kinds with
 * {@link StructureModifiers#registerType}.
 */
public interface StructureModifier {
    /**
     * Changes the settings of {@code structure}: where it may generate, the mob
     * spawns inside it, its generation step, and how terrain fits around it. Called
     * on every data pack load, each time starting from the structure's own
     * settings, so files that were removed stop applying.
     */
    void modify(Holder<Structure> structure, StructureSettingsEditor settings);
}
