package net.weaver.patcher.test;

import net.weaver.patcher.JarPatcher;
import net.weaver.patcher.PatchException;
import net.weaver.patcher.PatchSet;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Self-contained fixture test: build a tiny jar, patch it through the same
 * JarPatcher, load the result with URLClassLoader, and exercise both the new
 * members and the original bytecode.
 */
public final class PatcherSelfTest {
    private PatcherSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        Path injectDirectory = Path.of(args[0]).toAbsolutePath().normalize();
        Path workDirectory = Path.of(args[1]).toAbsolutePath().normalize();
        deleteRecursively(workDirectory);
        Files.createDirectories(workDirectory);

        Path fixtureJar = workDirectory.resolve("fixture.jar");
        writeFixtureJar(fixtureJar, "fixture/Dummy.class");

        Path patchedJar = workDirectory.resolve("fixture-patched.jar");
        Path report = workDirectory.resolve("fixture-report.json");
        JarPatcher.Result result = JarPatcher.patch(fixtureJar, patchedJar, injectDirectory, report,
                PatchSet.PATCH_LEVEL, PatchSet.ID,
                List.of(new PatchSet.Target("fixture/Dummy", true)));
        assertTrue(Files.isRegularFile(patchedJar), "patched fixture jar was not created");
        assertTrue(Files.isRegularFile(report), "report was not created");
        assertTrue(result.operationCount() == 12,
                "expected 12 operations, got " + result.operationCount());

        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{patchedJar.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            assertAddedMembers(loader);
            assertInvocation(loader);
        }

        Path patchedAgain = workDirectory.resolve("fixture-patched-again.jar");
        JarPatcher.patch(fixtureJar, patchedAgain, injectDirectory,
                workDirectory.resolve("fixture-report-again.json"), PatchSet.PATCH_LEVEL, PatchSet.ID,
                List.of(new PatchSet.Target("fixture/Dummy", true)));
        assertTrue(Arrays.equals(sha256(patchedJar), sha256(patchedAgain)),
                "two patch runs produced different fixture jars");

        assertOriginalEntriesPreserved(fixtureJar, patchedJar);

        Path conflictJar = workDirectory.resolve("conflict.jar");
        writeFixtureJar(conflictJar, "fixture/Conflict.class");
        Path conflictOutput = workDirectory.resolve("conflict-patched.jar");
        Files.writeString(conflictOutput, "stale artifact");
        boolean failed = false;
        try {
            JarPatcher.patch(conflictJar, conflictOutput, injectDirectory,
                    workDirectory.resolve("conflict-report.json"), PatchSet.PATCH_LEVEL, PatchSet.ID,
                    List.of(new PatchSet.Target("fixture/Conflict", true)));
        } catch (PatchException expected) {
            failed = true;
        }
        assertTrue(failed, "conflicting member did not fail closed");
        assertTrue(!Files.exists(conflictOutput), "failed patch left an output file");

        Path missingJar = workDirectory.resolve("missing.jar");
        writeFixtureJar(missingJar, "fixture/Dummy.class");
        Path missingOutput = workDirectory.resolve("missing-patched.jar");
        failed = false;
        try {
            JarPatcher.patch(missingJar, missingOutput, injectDirectory,
                    workDirectory.resolve("missing-report.json"), PatchSet.PATCH_LEVEL, PatchSet.ID,
                    List.of(new PatchSet.Target("fixture/Missing", true)));
        } catch (PatchException expected) {
            failed = true;
        }
        assertTrue(failed, "missing target did not fail closed");
        assertTrue(!Files.exists(missingOutput), "missing target left an output file");

        System.out.println("PATCHER TEST ok=true");
    }

    private static void assertAddedMembers(ClassLoader loader) throws Exception {
        Class<?> dummy = Class.forName("fixture.Dummy", false, loader);
        Set<String> interfaces = new TreeSet<>();
        for (Class<?> type : dummy.getInterfaces()) {
            interfaces.add(type.getName());
        }
        assertTrue(interfaces.contains(PatchSet.HOLDER_INTERFACE.replace('/', '.')),
                "Dummy is missing the holder interface: " + interfaces);
        assertTrue(interfaces.contains(PatchSet.PATCHED_GAME_INTERFACE.replace('/', '.')),
                "Dummy is missing the patched-game interface: " + interfaces);

        Method get = requiredMethod(dummy, PatchSet.GET, Object.class, String.class);
        requiredMethod(dummy, PatchSet.SET, Object.class, String.class, Object.class);
        requiredMethod(dummy, PatchSet.REMOVE, Object.class, String.class);
        requiredMethod(dummy, PatchSet.HAS, boolean.class, String.class);
        Method patchLevel = requiredMethod(dummy, PatchSet.PATCH_LEVEL_METHOD, int.class);
        assertTrue(Modifier.isPublic(get.getModifiers()), "added get method is not public");
        assertTrue(Modifier.isPublic(patchLevel.getModifiers()), "added patch-level method is not public");
    }

    private static void assertInvocation(ClassLoader loader) throws Exception {
        Class<?> dummyClass = Class.forName("fixture.Dummy", true, loader);
        Object dummy = dummyClass.getConstructor().newInstance();

        assertTrue("dummy".equals(dummyClass.getMethod("hello").invoke(dummy)),
                "existing hello() changed");
        assertTrue(Integer.valueOf(42).equals(dummyClass.getMethod("twice", int.class).invoke(dummy, 21)),
                "existing twice() changed");

        Method get = requiredMethod(dummyClass, PatchSet.GET, Object.class, String.class);
        Method set = requiredMethod(dummyClass, PatchSet.SET, Object.class, String.class, Object.class);
        Method remove = requiredMethod(dummyClass, PatchSet.REMOVE, Object.class, String.class);
        Method has = requiredMethod(dummyClass, PatchSet.HAS, boolean.class, String.class);
        Method patchLevel = requiredMethod(dummyClass, PatchSet.PATCH_LEVEL_METHOD, int.class);

        assertTrue(get.invoke(dummy, "missing") == null, "get on empty holder was not null");
        assertTrue(set.invoke(dummy, "alpha", "one") == null, "first set did not return null");
        assertTrue("one".equals(get.invoke(dummy, "alpha")), "get returned the wrong value");
        assertTrue(Boolean.TRUE.equals(has.invoke(dummy, "alpha")), "has did not see the attachment");
        assertTrue("one".equals(set.invoke(dummy, "alpha", "two")), "set did not return the previous value");
        assertTrue("two".equals(get.invoke(dummy, "alpha")), "set did not replace the value");
        assertTrue("two".equals(remove.invoke(dummy, "alpha")), "remove did not return the previous value");
        assertTrue(Boolean.FALSE.equals(has.invoke(dummy, "alpha")), "remove left the attachment");
        assertTrue(remove.invoke(dummy, "alpha") == null, "second remove did not return null");
        assertTrue(Integer.valueOf(1).equals(patchLevel.invoke(dummy)), "patch level was not 1");

        Object other = dummyClass.getConstructor().newInstance();
        set.invoke(other, "alpha", "other");
        assertTrue(get.invoke(dummy, "alpha") == null, "distinct holders shared an identity key");
        assertTrue("other".equals(get.invoke(other, "alpha")), "second holder lost its own attachment");
        remove.invoke(other, "alpha");

        set.invoke(dummy, "gamma", "kept");
        System.gc();
        Thread.sleep(25);
        assertTrue("kept".equals(get.invoke(dummy, "gamma")),
                "attachment was lost after GC while the holder was still live");

        Class<?> store = Class.forName(PatchSet.STORE_CLASS.replace('/', '.'), true, loader);
        Method size = store.getMethod("size");
        Method clear = store.getMethod("clear", Object.class);
        set.invoke(dummy, "beta", "value");
        assertTrue(((Integer) size.invoke(null)) >= 1, "store did not track the holder");
        clear.invoke(null, dummy);
        assertTrue(((Integer) size.invoke(null)) == 0, "clear did not remove the holder");
    }

    private static void assertOriginalEntriesPreserved(Path original, Path patched) throws IOException {
        try (ZipFile before = new ZipFile(original.toFile());
             ZipFile after = new ZipFile(patched.toFile())) {
            Set<String> patchedNames = new TreeSet<>();
            Enumeration<? extends ZipEntry> entries = after.entries();
            while (entries.hasMoreElements()) {
                patchedNames.add(entries.nextElement().getName());
            }
            entries = before.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                assertTrue(patchedNames.contains(name), "patched jar lost entry " + name);
            }
            assertTrue(Arrays.equals(entryBytes(before, "META-INF/MANIFEST.MF"),
                    entryBytes(after, "META-INF/MANIFEST.MF")), "manifest was modified");
            assertTrue(Arrays.equals(entryBytes(before, "fixture/data.txt"),
                    entryBytes(after, "fixture/data.txt")), "resource entry was modified");
            assertTrue(patchedNames.contains("META-INF/weaver/patch.json"), "metadata entry missing");
        }
    }

    private static byte[] entryBytes(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        assertTrue(entry != null, "missing entry " + name);
        try (InputStream in = zip.getInputStream(entry)) {
            return in.readAllBytes();
        }
    }

    private static void writeFixtureJar(Path path, String... classEntries) throws IOException {
        List<String> names = new ArrayList<>();
        names.add("META-INF/MANIFEST.MF");
        names.add("fixture/data.txt");
        names.addAll(List.of(classEntries));
        List<String> sorted = new ArrayList<>(new TreeSet<>(names));
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            for (String name : sorted) {
                ZipEntry entry = new ZipEntry(name);
                entry.setTime(0L);
                output.putNextEntry(entry);
                byte[] bytes;
                if (name.equals("META-INF/MANIFEST.MF")) {
                    bytes = "Manifest-Version: 1.0\r\n\r\n".getBytes(StandardCharsets.UTF_8);
                } else if (name.equals("fixture/data.txt")) {
                    bytes = "fixture-resource\n".getBytes(StandardCharsets.UTF_8);
                } else {
                    try (InputStream in = PatcherSelfTest.class.getResourceAsStream("/" + name)) {
                        assertTrue(in != null, "fixture class resource not found: " + name);
                        bytes = in.readAllBytes();
                    }
                }
                output.write(bytes);
                output.closeEntry();
            }
        }
    }

    private static Method requiredMethod(Class<?> owner, String name, Class<?> returnType,
                                         Class<?>... parameters) throws NoSuchMethodException {
        Method method = owner.getMethod(name, parameters);
        assertTrue(method.getReturnType().equals(returnType),
                "method " + name + " has return type " + method.getReturnType());
        return method;
    }

    private static byte[] sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return digest.digest();
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            List<Path> paths = stream.sorted(java.util.Comparator.reverseOrder()).toList();
            for (Path item : paths) {
                Files.deleteIfExists(item);
            }
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
