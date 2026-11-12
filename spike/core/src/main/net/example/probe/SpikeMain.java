package net.example.probe;

import net.example.game.GameTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;

/** Drives the fake game through every lifecycle phase. */
public final class SpikeMain {

    private SpikeMain() {
    }

    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "client" : args[0];
        Class<?> hooks = Class.forName("net.example.mod.ProofHooks", true,
                ClassLoader.getSystemClassLoader());
        Method hasPhase = hooks.getMethod("hasPhase", String.class);
        Method phases = hooks.getMethod("phases");
        Method hits = hooks.getMethod("hits");

        BuiltInRegistries.bootStrap();
        if ("client".equals(mode)) {
            new Minecraft().tick();
        }
        MinecraftServer server = new MinecraftServer();
        server.runServer();
        server.tickServer(() -> true);
        new GameTarget().tick();

        boolean initialize = (boolean) hasPhase.invoke(null, "initialize");
        boolean register = (boolean) hasPhase.invoke(null, "register");
        boolean common = (boolean) hasPhase.invoke(null, "common");
        boolean client = (boolean) hasPhase.invoke(null, "client");
        boolean serverPhase = (boolean) hasPhase.invoke(null, "server");
        boolean complete = (boolean) hasPhase.invoke(null, "complete");
        int hitCount = (int) hits.invoke(null);
        String phaseList = String.valueOf(phases.invoke(null));

        check(initialize, "initialize phase");
        check(register, "register phase");
        check(common, "common phase");
        check(serverPhase, "server phase");
        check(complete, "complete phase");
        check("client".equals(mode) == client, "client phase presence");
        check(hitCount == 1, "mixin hit count=" + hitCount);
        System.out.println("SPIKE RESULT ok=true mode=" + mode + " hits=" + hitCount
                + " phases=" + phaseList);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("check failed: " + message);
        }
        System.out.println("SPIKE CHECK ok=true " + message);
    }
}
