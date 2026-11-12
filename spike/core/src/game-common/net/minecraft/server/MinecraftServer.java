package net.minecraft.server;

/** Fake dedicated server. */
public final class MinecraftServer {

    public void runServer() {
        System.out.println("GAME runServer");
    }

    public void tickServer(java.util.function.BooleanSupplier keepRunning) {
        System.out.println("GAME server tick");
    }
}
