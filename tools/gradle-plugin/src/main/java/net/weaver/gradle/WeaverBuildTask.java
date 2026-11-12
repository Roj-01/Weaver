package net.weaver.gradle;

import net.weaver.gradle.jij.JijJar;
import net.weaver.gradle.jij.JijLayout;
import net.weaver.gradle.jij.JijManifest;
import org.gradle.api.GradleException;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.bundling.Jar;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Builds and verifies the mod jar.
 *
 * <p>Verification covers the entrypoint, the descriptor, API leakage, and the
 * jar-in-jar layout: every entry listed in {@code META-INF/weaver/jij.json}
 * must exist at its declared path. Nested mod classes are never inspected or
 * rewritten here; they are loaded in the shared class space by the loader.
 */
public abstract class WeaverBuildTask extends Jar {

    @Input
    @Optional
    public abstract Property<String> getModuleClass();

    @Input
    public abstract Property<String> getModId();

    @Input
    public abstract Property<String> getModVersion();

    public WeaverBuildTask() {
        getManifest().attributes(Map.of(
                "Weaver-Module-Class", getModuleClass().map(value -> value == null ? "" : value.trim()),
                "Weaver-Api-Version", "V2"
        ));
        doLast("weaver-verify", task -> verify());
    }

    private void verify() {
        File jar = getArchiveFile().get().getAsFile();
        if (!jar.isFile()) {
            throw new GradleException("weaverBuild: missing output " + jar);
        }
        String main = getModuleClass().getOrElse("").trim();
        int nested = 0;
        try (JarFile file = new JarFile(jar)) {
            var manifest = file.getManifest();
            if (manifest == null) {
                throw new GradleException("weaverBuild: jar has no manifest");
            }
            String entry = manifest.getMainAttributes().getValue("Weaver-Module-Class");
            if (entry == null || !entry.equals(main)) {
                throw new GradleException("weaverBuild: Weaver-Module-Class is wrong: " + entry);
            }
            if (file.getEntry(main.replace('.', '/') + ".class") == null) {
                throw new GradleException("weaverBuild: main class is not in the jar: " + main);
            }
            if (file.getEntry("weaver.mod.json") == null) {
                throw new GradleException("weaverBuild: weaver.mod.json is not in the jar");
            }
            for (var entries = file.entries(); entries.hasMoreElements(); ) {
                JarEntry jarEntry = entries.nextElement();
                if (jarEntry.getName().startsWith("net/weaver/api/")) {
                    throw new GradleException("weaverBuild: the API must stay compile-only");
                }
            }
            JarEntry jij = file.getJarEntry(JijLayout.MANIFEST);
            if (jij != null) {
                List<JijJar> jars = JijManifest.parse(read(file, jij));
                for (JijJar nestedJar : jars) {
                    if (file.getEntry(nestedJar.entryName()) == null) {
                        throw new GradleException("weaverBuild: jar-in-jar entry is missing: "
                                + nestedJar.entryName());
                    }
                }
                nested = jars.size();
            }
        } catch (GradleException e) {
            throw e;
        } catch (Exception e) {
            throw new GradleException("weaverBuild: cannot read " + jar, e);
        }
        System.out.println("WEAVERBUILD ok=true jar=" + jar.getAbsolutePath()
                + " id=" + getModId().get() + " version=" + getModVersion().get()
                + " entrypoint=" + main + " nested=" + nested);
    }

    private static byte[] read(JarFile file, JarEntry entry) throws Exception {
        try (InputStream in = file.getInputStream(entry)) {
            return in.readAllBytes();
        }
    }
}
