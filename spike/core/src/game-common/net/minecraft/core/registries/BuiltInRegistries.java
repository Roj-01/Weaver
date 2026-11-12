package net.minecraft.core.registries;

/** Fake registry bootstrap for the core spike. */
public final class BuiltInRegistries {

    private BuiltInRegistries() {
    }

    public static void bootStrap() {
        createContents();
        freeze();
    }

    private static void createContents() {
        System.out.println("GAME createContents");
    }

    private static void freeze() {
        System.out.println("GAME freeze");
    }
}
