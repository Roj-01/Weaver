package net.example.mod;

/** Mod-owned code called from a mixin that is copied into the game class. */
public final class ModHooks {

    private static volatile int hits;

    private ModHooks() {
    }

    public static void hit() {
        hits++;
        System.out.println("MOD hook hit=" + hits);
    }

    public static int hits() {
        return hits;
    }

    public static void reset() {
        hits = 0;
    }
}
