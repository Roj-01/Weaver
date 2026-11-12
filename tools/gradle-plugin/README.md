# Weaver Gradle plugin

Registers the Weaver toolchain tasks for a mod project: `weaverModJson`,
`weaverResolveMods`, `weaverJij`, `weaverBuild`, `weaverStage`,
`weaverExportJij`, `runClient`, `runServer`, `runDatagen`, and
`weaverGameTest`. It also adds the
base API, the optional game API, and the Minecraft classpath to `compileOnly`.

## Using it

```kotlin
pluginManagement {
    includeBuild("../../tools/gradle-plugin")
}

plugins {
    id("net.weaver.gradle")
}

weaver {
    mainClass.set("com.example.MyMod")
    modId.set("my-mod")
    modVersion.set("1.0.0")
    minecraftVersion.set("26.3")
}
```

## Toolchain paths

| Property | Environment variable | Default |
| --- | --- | --- |
| `apiJar` | `WEAVER_API_JAR` | `<root>/api/build/libs/api-2.3.2.jar` |
| `gameApiJar` | `WEAVER_GAME_API_JAR` | `<root>/api-game/build/libs/api-game-2.3.2.jar` |
| `gameDir` | `WEAVER_GAME_DIR` | `<root>/work/game-26.3` |
| `serverDir` | `WEAVER_SERVER_DIR` | `<root>/work/server-26.3` |
| `agentJar` | `WEAVER_AGENT_JAR` | `<root>/core/build/agent/weaver-agent.jar` |
| `launcher` | `WEAVER_LAUNCHER` | `<root>/tools/launcher/launch.ps1` |

`apiJar` and `gameApiJar` are added to `compileOnly` only when the file exists.
The plugin warns and continues when they are missing, so a project that uses
only the base API still compiles. The path defaults are relative to the root
project and fit the Weaver repository; a mod project outside it should set the
properties or the environment variables. The game API jar is optional: mods
that use the game-typed extensions set `weaver.gameApiJar` (or
`WEAVER_GAME_API_JAR`) and import `net.weaver.api.game`.

`launcher` is kept for build-script compatibility; `runClient` and `runServer`
now construct the Java command directly and do not invoke the PowerShell
launcher. `tools/launcher/launch.ps1` remains the distribution/proof entry
point.

`gameDir/classpath.txt` is split on `;`. The plugin also adds
`serverDir/versions/26.3/server-26.3.jar` and every jar under
`serverDir/libraries` when they exist.

## Mod dependencies

```kotlin
repositories {
    maven { url = uri("libs/repo") }
}

dependencies {
    weaverMod("com.example:library:[1.0,2.0)")
    weaverMod(project(":companion-mod"))
    weaverOptional("com.example:addon:1.4.0")
}

weaver {
    localMods.from(layout.projectDirectory.file("libs/manual.jar"))
    strictConflicts.set(true)
    embedModDependencies.set(true)
    exportJijForRun.set(true)
}
```

`weaverResolveMods` resolves the graph deterministically, prints `WEAVER DEP`
lines, reports `WEAVER DEP CONFLICT`, and writes
`build/weaver/dependencies.json`. Duplicate mod ids always fail; multiple
requested module versions and unresolved required modules fail only with
`strictConflicts`. `weaverOptional` is validated but never embedded.

`weaverJij` stages resolved mods under `META-INF/weaver/jars/` and writes
`META-INF/weaver/jij.json`. `weaverBuild` verifies the layout. The loader has no
classloader isolation: nested mods join the shared class space, ordering is
deterministic, and duplicate class entries are first-wins. See
`docs/toolchain.md` for the loader integration limitation.

## Run configuration

```kotlin
weaver {
    jvmArgs.add("-Dweaver.example=true")
    programArgs.add("--bonus")
    environment.put("WEAVER_FIXTURE", "1")
    debug.set(true)
    debugPort.set(5005)
    debugSuspend.set(true)
    workingDir.set(rootProject.layout.projectDirectory.dir("run").asFile.absolutePath)
    serverPort.set(25566)
}
```

`runClient` and `runServer` construct the Java command directly (no PowerShell
launcher dependency) and stage the built mod plus extracted JIJ jars into
`build/weaver/mods`. Use `-Pweaver.dryRun=true` to print the exact command.
`runDatagen` runs `datagenClass` with `--out build/weaver/datagen` and appends
`programArgs`.

## Tests

```powershell
.\gradlew.bat -p tools\gradle-plugin test --no-daemon --console=plain
```

The suite uses Gradle TestKit for dependency resolution, version conflicts,
jar-in-jar packaging, run-task command construction, and the API POM gates.

## Game tests

`weaverGameTest` stages the mod like `runServer` and runs vanilla's game test
server (`net.minecraft.gametest.Main`) with the agent. Every registered test
runs, or those matching `weaver.gameTests` (`-Pweaver.gameTests=examplemod:*`
or `weaver { gameTests.set(...) }`). The report is
`build/weaver/gametest/report.xml`, the world `build/weaver/gametest/universe`
(emptied first), the log `build/weaver/logs/gametest.log`. A failed required
test fails the task. See `docs/gametest.md`.
