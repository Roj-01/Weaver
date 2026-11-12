package net.weaver.proof;

import net.minecraft.ChatFormatting;

/** Arguments of the constants the proof adds to vanilla enums (META-INF/enumextensions.json). */
final class ProofEnums {
    private ProofEnums() {
    }

    /** Rarity(int id, String name, ChatFormatting color); ids follow on, so the ordinal is the id. */
    static Object[] mythic(int ordinal) {
        return new Object[] {ordinal, "weaverproof_mythic", ChatFormatting.LIGHT_PURPLE};
    }
}
