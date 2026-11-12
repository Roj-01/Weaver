package net.weaver.gradle.dependency;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;

/**
 * Resolves {@code weaverMod}, {@code weaverOptional}, and local mod jars,
 * prints deterministic diagnostics, and writes
 * {@code build/weaver/dependencies.json}.
 *
 * <p>The task fails when the resolver reports a fatal conflict: duplicate mod
 * ids always fail, while version and missing-module conflicts fail only when
 * {@link #getStrict()} is true.
 */
public abstract class WeaverResolveModsTask extends DefaultTask {

    private Configuration modsConfiguration;
    private Configuration optionalConfiguration;

    /**
     * Returns the required mod configuration.
     *
     * @return the {@code weaverMod} configuration, or null when not set
     */
    @Internal
    public Configuration getModsConfiguration() {
        return modsConfiguration;
    }

    /**
     * Sets the required mod configuration.
     *
     * @param modsConfiguration the {@code weaverMod} configuration
     */
    public void setModsConfiguration(Configuration modsConfiguration) {
        this.modsConfiguration = modsConfiguration;
    }

    /**
     * Returns the optional mod configuration.
     *
     * @return the {@code weaverOptional} configuration, or null when not set
     */
    @Internal
    public Configuration getOptionalConfiguration() {
        return optionalConfiguration;
    }

    /**
     * Sets the optional mod configuration.
     *
     * @param optionalConfiguration the {@code weaverOptional} configuration
     */
    public void setOptionalConfiguration(Configuration optionalConfiguration) {
        this.optionalConfiguration = optionalConfiguration;
    }

    /**
     * Returns the local mod jars that are not resolved from a repository.
     *
     * @return the local mods file collection
     */
    @InputFiles
    public abstract ConfigurableFileCollection getLocalMods();

    /**
     * Returns whether version and missing-module conflicts are fatal.
     *
     * @return the strict conflict flag
     */
    @Input
    public abstract Property<Boolean> getStrict();

    /**
     * Returns the JSON report destination.
     *
     * @return the report file property
     */
    @OutputFile
    public abstract RegularFileProperty getReportFile();

    /** Resolves the mod graph, writes the report, and fails on fatal conflicts. */
    @TaskAction
    public void resolveMods() throws IOException {
        if (modsConfiguration == null) {
            throw new GradleException("weaverResolveMods: weaverMod configuration is not configured");
        }
        if (!getReportFile().isPresent()) {
            throw new GradleException("weaverResolveMods: reportFile is not configured");
        }
        ModDependencyResolver resolver = new ModDependencyResolver(
                modsConfiguration, optionalConfiguration, getLocalMods(),
                getStrict().getOrElse(false), getLogger());
        ModResolution resolution = resolver.resolve();
        File report = getReportFile().get().getAsFile();
        resolver.writeReport(report, resolution);
        if (resolver.hasFatalConflicts()) {
            ModConflict first = resolver.getFatalConflicts().get(0);
            throw new GradleException("WEAVER DEPS FAILED conflicts="
                    + resolver.getFatalConflictCount() + " report=" + report.getAbsolutePath()
                    + " first=" + first.kind() + ":" + first.module() + " " + first.message());
        }
    }
}
