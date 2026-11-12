package net.weaver.gradle.jij;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Extracts embedded nested mod jars into the run mods directory.
 *
 * <p>Only files named {@code jij-*.jar} are removed before extraction; other
 * files already present in the output directory, such as the parent mod jar
 * staged by another task, are left untouched. When {@link #getExportEnabled()}
 * is {@code false} the task is a true no-op and prints nothing.
 *
 * <p>The switch is named {@code exportEnabled} rather than {@code enabled}
 * because {@link org.gradle.api.Task#getEnabled()} already returns a primitive
 * {@code boolean} and cannot be redeclared as a {@code Property<Boolean>}.
 */
public abstract class WeaverExportJijTask extends DefaultTask {

    /** The built mod jar that may contain nested jars. */
    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract RegularFileProperty getJarFile();

    /** The run mods directory. */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /** Whether extraction is enabled. */
    @Input
    public abstract Property<Boolean> getExportEnabled();

    /**
     * Extracts nested jars when enabled.
     *
     * @throws IOException if the jar cannot be read or an entry cannot be
     *         written
     */
    @TaskAction
    public void extract() throws IOException {
        if (!getExportEnabled().getOrElse(true)) {
            return;
        }
        if (!getJarFile().isPresent()) {
            throw new GradleException("weaverExportJij: jarFile is not configured");
        }
        File jar = getJarFile().get().getAsFile();
        Path output = getOutputDirectory().get().getAsFile().toPath();
        Files.createDirectories(output);
        removeStaleJijFiles(output);
        JijExtractor.extract(jar, output, line -> getLogger().lifecycle(line));
    }

    private static void removeStaleJijFiles(Path output) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(output, "jij-*.jar")) {
            for (Path path : stream) {
                Files.deleteIfExists(path);
            }
        }
    }
}
