package net.weaver.core.dev;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.instrument.ClassDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import net.weaver.api.event.events.HotSwapEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What the developer hot reload swaps live, adds, serves at boot, reloads, or restarts for. */
class HotSwapServiceTest {
    @TempDir
    Path root;

    @TempDir
    Path resources;

    @TempDir
    Path work;

    /** Stands in for the JVM and the game: two loaded classes; one rejects redefinition as a shape change. */
    private final class FakeRuntime implements HotSwapService.Runtime {
        final List<String> redefined = new ArrayList<>();
        final Map<String, Class<?>> loaded = Map.of("demo.Loaded", String.class, "demo.Reshaped", Integer.class);
        final Map<String, byte[]> jars = new HashMap<>();
        final List<Path> appended = new ArrayList<>();
        final List<String> reloads = new ArrayList<>();
        final List<String> restarts = new ArrayList<>();

        @Override
        public Class<?> loaded(String binaryName) {
            return loaded.get(binaryName);
        }

        @Override
        public void redefine(List<ClassDefinition> definitions) {
            for (ClassDefinition definition : definitions) {
                if (definition.getDefinitionClass() == Integer.class) {
                    throw new UnsupportedOperationException("class redefinition failed: attempted to add a method");
                }
            }
            definitions.forEach(definition -> redefined.add(definition.getDefinitionClass().getName()));
        }

        @Override
        public byte[] jarBytes(String binaryName) {
            return jars.get(binaryName);
        }

        @Override
        public void appendJar(Path jar) {
            appended.add(jar);
        }

        @Override
        public void reloadResources(boolean assets, boolean data) {
            reloads.add("assets=" + assets + " data=" + data);
        }

        @Override
        public void restart(String why) {
            restarts.add(why);
        }
    }

    private Path write(Path base, String relative, String content, long time) throws Exception {
        Path file = base.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        Files.setLastModifiedTime(file, FileTime.fromMillis(time));
        return file;
    }

    private Path writeClass(String binaryName, String content, long time) throws Exception {
        return write(root, binaryName.replace('.', '/') + ".class", content, time);
    }

    private HotSwapService service(FakeRuntime runtime, boolean autoRestart, List<String> log) {
        for (String name : List.of("demo.Loaded", "demo.Reshaped", "demo.NotYet", "demo.Mixin", "demo.Same")) {
            runtime.jars.put(name, "v1".getBytes());
        }
        return new HotSwapService(List.of(root), List.of(resources), runtime, Set.of("demo.Mixin")::contains, log::add,
                work, autoRestart);
    }

    @Test
    void swapsLiveAddsNewClassesAndRestartsForTheRest() throws Exception {
        for (String name : List.of("demo.Loaded", "demo.Reshaped", "demo.NotYet", "demo.Mixin", "demo.Same")) {
            writeClass(name, "v1", 1_000);
        }
        FakeRuntime runtime = new FakeRuntime();
        List<String> log = new ArrayList<>();
        HotSwapService service = service(runtime, true, log);
        service.prepareBoot();
        assertTrue(runtime.appended.isEmpty() && service.pending().isEmpty(), "the jars already hold this code");
        assertNull(service.poll(), "nothing changed yet");

        writeClass("demo.Loaded", "v2", 2_000);
        writeClass("demo.Reshaped", "v2", 2_000);
        writeClass("demo.NotYet", "v2", 2_000);
        writeClass("demo.Mixin", "v2", 2_000);
        writeClass("demo.Same", "v1", 2_000); // touched, same bytes
        writeClass("demo.Brand", "new", 2_000); // did not exist at boot, nor in any jar

        HotSwapEvent event = service.poll();
        assertEquals(List.of("java.lang.String"), runtime.redefined, "the loaded class is redefined");
        assertTrue(event.swapped().containsAll(List.of("demo.NotYet", "java.lang.String", "demo.Brand")), event.swapped().toString());
        assertArrayEquals("v2".getBytes(), service.pending().get("demo/NotYet"), "an unloaded class loads the new bytes");
        assertEquals(1, runtime.appended.size(), "the new class goes into an overlay jar");
        try (JarFile jar = new JarFile(runtime.appended.get(0).toFile())) {
            assertTrue(jar.getEntry("demo/Brand.class") != null);
        }
        List<String> skipped = event.skipped().stream().map(HotSwapEvent.Skipped::className).sorted().toList();
        assertEquals(List.of("demo.Mixin", "java.lang.Integer"), skipped);
        assertEquals(1, runtime.restarts.size(), "what cannot be swapped restarts the game once");
        assertNull(service.poll(), "a second poll with no change does nothing");
    }

    @Test
    void restartCanBeTurnedOff() throws Exception {
        writeClass("demo.Reshaped", "v1", 1_000);
        FakeRuntime runtime = new FakeRuntime();
        List<String> log = new ArrayList<>();
        HotSwapService service = service(runtime, false, log);
        service.prepareBoot();
        writeClass("demo.Reshaped", "v2", 2_000);
        service.poll();
        assertTrue(runtime.restarts.isEmpty());
        assertTrue(log.stream().anyMatch(line -> line.contains("RESTART NEEDED")), log.toString());
    }

    @Test
    void bootRunsTheNewestCompiledCodeEvenWithAStaleJar() throws Exception {
        writeClass("demo.Loaded", "v2", 1_000); // the jar still has v1
        writeClass("demo.OnlyHere", "x", 1_000); // the jar does not have it at all
        FakeRuntime runtime = new FakeRuntime();
        HotSwapService service = service(runtime, true, new ArrayList<>());
        service.prepareBoot();
        assertArrayEquals("v2".getBytes(), service.pending().get("demo/Loaded"));
        assertEquals(1, runtime.appended.size());
        assertNull(service.poll(), "the boot state is the baseline");
    }

    @Test
    void resourceChangesReloadAssetsOrData() throws Exception {
        write(resources, "assets/demo/lang/en_us.json", "{}", 1_000);
        write(resources, "data/demo/recipe/a.json", "{}", 1_000);
        FakeRuntime runtime = new FakeRuntime();
        HotSwapService service = service(runtime, true, new ArrayList<>());
        service.prepareBoot();
        service.poll();
        assertTrue(runtime.reloads.isEmpty(), "nothing changed");
        write(resources, "data/demo/recipe/b.json", "{}", 2_000);
        service.poll();
        assertEquals(List.of("assets=false data=true"), runtime.reloads);
        write(resources, "assets/demo/lang/en_us.json", "{\"a\":1}", 3_000);
        service.poll();
        assertEquals("assets=true data=false", runtime.reloads.get(1));
        assertFalse(runtime.reloads.size() > 2);
    }

    @Test
    void transformerServesPendingBytesOnceToTheModLoaderOnly() throws Exception {
        writeClass("demo.NotYet", "v1", 1_000);
        FakeRuntime runtime = new FakeRuntime();
        HotSwapService service = service(runtime, true, new ArrayList<>());
        service.prepareBoot();
        writeClass("demo.NotYet", "v2", 2_000);
        service.poll();
        ClassLoader mods = new ClassLoader() { };
        var transformer = service.transformer(mods);
        assertNull(transformer.transform(new ClassLoader() { }, "demo/NotYet", null, null, new byte[0]), "another loader");
        assertArrayEquals("v2".getBytes(), transformer.transform(mods, "demo/NotYet", null, null, new byte[0]));
        assertNull(transformer.transform(mods, "demo/NotYet", null, null, new byte[0]), "served once");
    }
}
