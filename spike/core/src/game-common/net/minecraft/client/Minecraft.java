package net.minecraft.client;

/** Fake client entry point. */
public final class Minecraft {

    public Minecraft() {
        Options options = new Options();
        System.out.println("GAME Minecraft ctor options=" + options.getClass().getSimpleName());
    }

    public void tick() {
        System.out.println("GAME client tick");
    }
}
