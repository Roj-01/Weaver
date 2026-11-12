package net.weaver.hotswapproof;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.weaver.api.ModContext;
import net.weaver.api.WeaverMod;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.events.HotSwapEvent;
import net.weaver.api.game.event.ServerStartingEvent;

/**
 * Developer hot reload on a running server, across a restart.
 *
 * <p>Phase 1 (first boot): the server runs the first version of the classes and
 * watches a folder holding them, plus a resources folder. The mod copies the
 * second version in, as a recompile would, and checks that a loaded class runs
 * the new code, that a class first used afterwards loads it, and that a class
 * that did not exist (and is not in the jar) works. It then adds a function to the
 * resources folder and checks the server reloaded its data. Last, it changes a
 * class's shape, which the JVM cannot swap: the game restarts by itself.</p>
 *
 * <p>Phase 2 (after the restart, the jar never rebuilt): the newest compiled code
 * runs, the reshaped class and the new class included.</p>
 */
public final class HotSwapProofMod implements WeaverMod {
    private final List<HotSwapEvent> events = new ArrayList<>();
    private volatile MinecraftServer server;
    private Path phaseFile;

    @Override
    public void onInitialize(ModContext context) {
        phaseFile = context.dataDir().resolve("hotswap-proof-phase1.txt");
        EventBus.GAME.addListener(HotSwapEvent.class, event -> {
            synchronized (events) {
                events.add(event);
                events.notifyAll();
            }
        });
        EventBus.GAME.addListener(ServerStartingEvent.class, event -> server = event.server());
    }

    @Override
    public void onServerStarted(ModContext context) {
        Thread proof = new Thread(Files.exists(phaseFile) ? this::phase2 : this::phase1, "hotswap-proof");
        proof.setDaemon(true);
        proof.start();
    }

    private void phase1() {
        List<String> failures = new ArrayList<>();
        try {
            String before = Greeter.greet();
            check(failures, "v1".equals(before), "greeter before=" + before);
            // loaded now, so its later shape change cannot be swapped live
            check(failures, Shape.value() == 1, "shape before=" + Shape.value());
            check(failures, Settings.limit() == 1, "static initializer value before=" + Settings.limit());
            Path watch = Path.of(System.getProperty("weaver.dev.hotswap.dirs", "").split(java.io.File.pathSeparator)[0]);
            Path resources = Path.of(System.getProperty("weaver.dev.hotswap.resources", "").split(java.io.File.pathSeparator)[0]);
            Path v2 = watch.resolveSibling("v2");

            // 1. method bodies, a class not loaded yet, and a brand-new class: all live
            copy(v2, watch, "Extra", "Greeter", "Late");
            HotSwapEvent event = awaitEvent(0, 20_000L);
            check(failures, event != null && event.complete(), "a complete hot swap within 20 s skipped="
                    + (event == null ? "-" : event.skipped()));
            String after = Greeter.greet();
            check(failures, "v2+extra".equals(after), "loaded class runs new code that uses a new class: " + after);
            String late = Late.name();
            check(failures, "late-v2".equals(late), "class first used after the change late=" + late);

            // 2. data: a new function in the resources folder, reloaded live
            Identifier id = Identifier.fromNamespaceAndPath("hotswapproof", "hello");
            check(failures, server != null && server.getFunctions().get(id).isEmpty(), "no function before");
            Path function = resources.resolve("data/hotswapproof/function/hello.mcfunction");
            Files.createDirectories(function.getParent());
            Files.writeString(function, "say hello from hot reload\n");
            boolean reloaded = false;
            for (int i = 0; i < 80 && !reloaded; i++) {
                Thread.sleep(250);
                reloaded = server.getFunctions().get(id).isPresent();
            }
            check(failures, reloaded, "data reloaded live: function " + id + " present");

            // assets: a language file (the client reloads its resources; dedicated servers have none)
            Path lang = resources.resolve("assets/hotswapproof/lang/en_us.json");
            Files.createDirectories(lang.getParent());
            Files.writeString(lang, "{\"hotswapproof.hello\": \"Hello from hot reload\"}\n");
            Thread.sleep(3_000);

            Files.writeString(phaseFile, failures.isEmpty() ? "ok" : String.join("|", failures));
            System.out.println("HOTSWAP PROOF PHASE1 ok=" + failures.isEmpty() + " (changing a class shape next: restart)");
            // 3. a shape change: not swappable, so the game restarts by itself
            copy(v2, watch, "Shape", "Settings");
        } catch (Throwable failure) {
            System.out.println("HOTSWAP PROOF RESULT ok=false failed=phase1 unexpected " + failure);
        }
    }

    private void phase2() {
        List<String> failures = new ArrayList<>();
        try {
            String phase1 = Files.readString(phaseFile).trim();
            check(failures, "ok".equals(phase1), "phase 1 before the restart: " + phase1);
            check(failures, Shape.value() == 2, "after the restart the reshaped class runs: value=" + Shape.value());
            check(failures, "v2+extra".equals(Greeter.greet()), "the newest code runs without a rebuilt jar: " + Greeter.greet());
            check(failures, Settings.limit() == 2, "a changed static initializer ran after the restart: " + Settings.limit());
            check(failures, server != null && server.getFunctions()
                    .get(Identifier.fromNamespaceAndPath("hotswapproof", "hello")).isPresent(), "data from the resources folder after the restart");
        } catch (Throwable failure) {
            failures.add("unexpected " + failure);
        }
        System.out.println("HOTSWAP PROOF RESULT ok=" + failures.isEmpty()
                + (failures.isEmpty() ? "" : " failed=" + String.join("|", failures)));
    }

    private static void copy(Path from, Path to, String... names) throws Exception {
        for (String name : names) {
            Path relative = Path.of("net", "weaver", "hotswapproof", name + ".class");
            Path target = to.resolve(relative);
            Files.createDirectories(target.getParent());
            Files.copy(from.resolve(relative), target, StandardCopyOption.REPLACE_EXISTING);
            Files.setLastModifiedTime(target, FileTime.fromMillis(System.currentTimeMillis()));
        }
    }

    private HotSwapEvent awaitEvent(int index, long timeoutMillis) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMillis;
        synchronized (events) {
            while (events.size() <= index && System.currentTimeMillis() < end) {
                events.wait(Math.max(1L, end - System.currentTimeMillis()));
            }
            return events.size() > index ? events.get(index) : null;
        }
    }

    private static void check(List<String> failures, boolean ok, String name) {
        System.out.println("HOTSWAP PROOF CHECK ok=" + ok + " " + name);
        if (!ok) {
            failures.add(name);
        }
    }
}
