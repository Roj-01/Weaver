import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.io.IOException
import java.nio.file.Files

plugins {
    base
}

allprojects {
    group = "net.weaver"
    version = "2.3.2"
    // The copyright holder named in LICENSE and NOTICE. Stamped into every jar
    // manifest so an artifact found on its own still says who owns it.
    extra["weaver.vendor"] = "Abdullah Haitham Al-Nuaimi"
}

fun booleanProperty(name: String): Boolean =
    providers.gradleProperty(name)
        .map { value ->
            value.toBooleanStrictOrNull()
                ?: throw GradleException("$name must be 'true' or 'false' (got '$value')")
        }
        .orElse(false)
        .get()

val weaverRequireGameApi = booleanProperty("weaver.requireGameApi")
val weaverSkipGameApi = booleanProperty("weaver.skipGameApi")
val weaverForceNoGameApi = booleanProperty("weaver.forceNoGameApi")

val weaverGameDir = providers.environmentVariable("WEAVER_GAME_DIR")
    .map { File(it) }
    .orElse(layout.projectDirectory.dir("work/game-26.3").asFile)
    .get()

val weaverGameClasspathFile = File(weaverGameDir, "classpath.txt")
val weaverServerJar = layout.projectDirectory
    .file("work/server-26.3/versions/26.3/server-26.3.jar").asFile

// classpath.txt holds absolute paths written by tools/fetch-game. When the
// repository or game directory moves, rebase stale entries onto the current
// game directory instead of compiling against files that no longer exist.
fun rebaseGameEntry(entry: File): File {
    if (entry.exists()) {
        return entry
    }
    val normalized = entry.path.replace('\\', '/')
    val librariesAt = normalized.lastIndexOf("/libraries/")
    val rebased = when {
        librariesAt >= 0 -> File(weaverGameDir, normalized.substring(librariesAt + 1))
        normalized.endsWith("/client.jar") -> File(weaverGameDir, "client.jar")
        else -> entry
    }
    return if (rebased.exists()) rebased else entry
}

val weaverResolvedGameClasspath: List<File> = when {
    weaverGameClasspathFile.isFile -> try {
        val entries = Files.readString(weaverGameClasspathFile.toPath())
            .trim()
            .split(";")
            .filter { it.isNotBlank() }
            .map { rebaseGameEntry(File(it)) }
        val missing = entries.filterNot { it.exists() }
        if (missing.isNotEmpty()) {
            logger.warn("weaver: {} of {} game classpath entries are missing (first: {}); "
                    + "rerun tools/fetch-game/fetch-game.sh to refresh {}",
                missing.size, entries.size, missing.first(), weaverGameDir)
        }
        entries
    } catch (failure: IOException) {
        logger.warn("weaver: cannot read {}: {}", weaverGameClasspathFile, failure.message)
        emptyList()
    }
    weaverServerJar.isFile -> {
        logger.warn("weaver: using the server jar as the game classpath; "
                + "client classes may be missing, so api-game compilation may fail")
        listOf(weaverServerJar)
    }
    else -> emptyList()
}

val weaverGameClasspath: List<File> =
    if (weaverForceNoGameApi) emptyList() else weaverResolvedGameClasspath

if (weaverSkipGameApi && weaverRequireGameApi) {
    throw GradleException(
        """
        [weaver] Contradictory build configuration: -Pweaver.skipGameApi=true and
        -Pweaver.requireGameApi=true cannot be combined. Remove one of the two properties:
        skip the optional game API with only -Pweaver.skipGameApi=true, or require it with
        only -Pweaver.requireGameApi=true.
        """.trimIndent()
    )
}

if (weaverRequireGameApi && weaverGameClasspath.isEmpty()) {
    throw GradleException(
        """
        [weaver] api-game is required (-Pweaver.requireGameApi=true) but no Minecraft 26.3
        classpath is available${if (weaverForceNoGameApi) " because -Pweaver.forceNoGameApi=true was set" else ""}.
        Fix one of the following:
          1. Set WEAVER_GAME_DIR to a directory containing classpath.txt
             (currently resolved to: $weaverGameDir).
          2. Provide work/server-26.3/versions/26.3/server-26.3.jar.
          3. Pass -Pweaver.skipGameApi=true if the optional game API is not needed.
        """.trimIndent()
    )
}

val weaverGameApiAvailable = weaverGameClasspath.isNotEmpty() && !weaverSkipGameApi

if (!weaverGameApiAvailable) {
    val reason = if (weaverSkipGameApi) "skip-game-api" else "no-game-classpath"
    logger.lifecycle("[weaver] api-game SKIPPED reason=$reason")
}

extra["weaver.requireGameApi"] = weaverRequireGameApi
extra["weaver.skipGameApi"] = weaverSkipGameApi
extra["weaver.forceNoGameApi"] = weaverForceNoGameApi
extra["weaver.gameApiAvailable"] = weaverGameApiAvailable
extra["weaver.gameClasspath"] = weaverGameClasspath

subprojects {
    apply(plugin = "java-library")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(26))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(26)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // -PrecordApi rewrites api-baseline/weaver-api.txt from the current API (ApiSurfaceTest).
        if (project.hasProperty("recordApi")) {
            systemProperty("weaver.api.record", "true")
            outputs.upToDateWhen { false }
        }
    }

    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/")
    }
}
