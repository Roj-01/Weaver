package net.weaver.gradle.jij;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Stages resolved mod dependencies as nested jars under
 * {@link JijLayout#ROOT} and writes {@link JijLayout#MANIFEST}.
 *
 * <p>The output directory is owned by this task and is cleared before staging,
 * so a removed dependency can never survive as a stale nested jar.
 */
public abstract class WeaverJijTask extends DefaultTask {

    /** The mod jars to embed, usually the {@code weaverMod} configuration. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getJars();

    /** The staging directory that becomes part of the mod jar. */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /**
     * Clears the staging directory and stages every input jar.
     *
     * @throws IOException if a jar cannot be read or the manifest cannot be
     *         written
     */
    @TaskAction
    public void stage() throws IOException {
        File output = getOutputDirectory().get().getAsFile();
        getProject().delete(output);
        Files.createDirectories(output.toPath());
        List<File> jars = new ArrayList<>(getJars().getFiles());
        JijPackager.stage(output, jars, line -> getLogger().lifecycle(line));
    }
}
