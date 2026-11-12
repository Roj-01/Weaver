package net.weaver.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Hands the installed mods to {@code net.weaver.api.game.data.ModPacks} in
 * {@code api-game}, which offers each mod jar with a {@code data/} or
 * {@code assets/} folder to the game as a built-in pack. Core does not depend
 * on {@code api-game}, so the call is reflective and failure-isolated: a
 * missing artifact logs one line and the game runs without mod packs.
 */
final class ModPacksInstall {
    private static final String MOD_PACKS_CLASS = "net.weaver.api.game.data.ModPacks";

    private ModPacksInstall() {
    }

    static void install(ClassLoader gameLoader, List<ModJar> mods, Consumer<String> log) {
        if (gameLoader == null) {
            return;
        }
        List<List<String>> entries = new ArrayList<>();
        for (ModJar mod : mods) {
            ModMetadata metadata = mod.metadata();
            String name = metadata.name() == null || metadata.name().isBlank() ? metadata.id() : metadata.name();
            entries.add(List.of(metadata.id(), name, mod.path().toAbsolutePath().toString()));
        }
        try {
            Class<?> modPacks = Class.forName(MOD_PACKS_CLASS, true, gameLoader);
            modPacks.getMethod("setMods", List.class).invoke(null, entries);
            Object packs = modPacks.getMethod("mods").invoke(null);
            log.accept("[weaver] MOD PACKS mods=" + mods.size() + " packs=" + ((List<?>) packs).size());
        } catch (ClassNotFoundException | LinkageError missing) {
            log.accept("[weaver] MOD PACKS unavailable reason=" + missing);
        } catch (Throwable failure) {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            log.accept("[weaver] MOD PACKS FAILED reason=" + cause);
        }
    }
}
