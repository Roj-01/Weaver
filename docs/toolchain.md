# Toolchain

Reference for building, testing, packaging, publishing, and running Weaver mods.
Commands use the PowerShell wrapper on Windows; on Unix, replace
`.\gradlew.bat` with `./gradlew`.

Requirements: JDK 26 and the repository Gradle wrapper (9.7.1).

## Module layout

| Module | Artifact | Contents |
| --- | --- | --- |
| `api` | `api/build/libs/api-2.3.2.jar` | Game-independent mod API: lifecycle contracts, event bus, registry API, SPI. No Minecraft classes. |
| `api-game` | `api-game/build/libs/api-game-2.3.2.jar` | Optional game-typed API (`net.weaver.api.game.*`): `GameWeaverMod`, typed events, `GameRegistries`, `TypedGameEventFactory`. Compiled against Minecraft 26.3; game classes are compile-only and never bundled. |
| `core` | `core/build/agent/weaver-agent.jar` | Loader agent: shared class space, metadata, lifecycle, event scanner, hooks, registry backend, access transformers, and the `core/jij` nested-jar reader. The runnable agent jar plus `core/build/agent/lib/`. |
| `mixin` | `mixin/build/libs/mixin-2.3.2.jar` | Mixin engine integration. |
| `diagnostics` | `diagnostics/build/libs/diagnostics-2.3.2.jar` | Crash-report diagnostics. |
| `sync` | `sync/build/libs/sync-2.3.2.jar` | Manifest, mirror, and resume support. |

`core` has no compile dependency on `api-game`. When the game API is available
it adds it as a `runtimeOnly` project dependency, so the agent runtime classpath
contains it without making the loader source game-typed. `LoaderAgent` installs
`net.weaver.api.game.TypedGameEventFactory` reflectively when the class is
visible and falls back to the base factory when it is not.

## Build and test

```powershell
.\gradlew.bat clean build --no-build-cache --rerun-tasks --console=plain
.\gradlew.bat :api:test :core:test :api-game:test --rerun-tasks --console=plain
.\gradlew.bat :api:benchmark
```

`build` compiles every module that can be built, runs the test suites, builds
the agent jar and lib, and runs the packaging verification tasks. `api-game` is
optional: when no Minecraft 26.3 classpath is available the build succeeds and
skips it instead of failing (see the behavior matrix below).

Quote Gradle property arguments in PowerShell, for example
`"-Pweaver.skipGameApi=true"`. Windows PowerShell can otherwise split an
unquoted `-Pname=value` argument at the dot. `tools\verify.ps1` passes these
arguments through an array, so it does not need the quotes.

The standalone Gradle plugin is an included build with its own wrapper run and
its own TestKit suite:

```powershell
.\gradlew.bat -p tools\gradle-plugin build
.\gradlew.bat -p tools\gradle-plugin test --no-daemon --console=plain
```

The plugin suite contains pure resolver/JIJ tests plus Gradle TestKit builds for
dependency resolution, version conflicts, jar-in-jar packaging, run-task
command construction, and the API POM gates. TestKit creates isolated temporary
projects and does not start Minecraft.

## Game classpath resolution

`api-game`, the Gradle plugin, and the agent all need the Minecraft 26.3
classpath. The root build resolves it once and exposes it to subprojects:

1. `WEAVER_GAME_DIR`, when set, otherwise `<repo>/work/game-26.3`.
2. `classpath.txt` in that directory, split on `;`.
3. Fallback: `<repo>/work/server-26.3/versions/26.3/server-26.3.jar`. The server
   jar does not contain client classes, so a client-typed build of `api-game`
   can still fail with missing-symbol errors; the build logs a warning when it
   uses this fallback.
4. Otherwise the classpath is empty.

`api-game` compiles with the game classpath on `compileOnly` and `testImplementation`.
It is never on `api`, `implementation`, `runtimeOnly`, or a published POM, so
the artifact never leaks Minecraft or Mojang classes.

### Build behavior matrix

The root build resolves the classpath and the three build properties once, then
publishes the results to subprojects as `weaver.requireGameApi`,
`weaver.skipGameApi`, `weaver.forceNoGameApi`, `weaver.gameApiAvailable`, and
`weaver.gameClasspath`.

| Classpath | `skipGameApi` | `requireGameApi` | `forceNoGameApi` | Result |
| --- | --- | --- | --- | --- |
| present | false | false | false | `api-game` compiles, tests, jars, and publishes. `core` adds `runtimeOnly(project(":api-game"))`, and the agent lib and manifest `Class-Path` contain one `api-game-*.jar`. |
| absent | false | false | false or true | Build succeeds. `api-game` is skipped and logs `[weaver] api-game SKIPPED reason=no-game-classpath`. `core` omits the `runtimeOnly` dependency and the verification tasks are no-ops. |
| present | false | false | true | Same as absent: `forceNoGameApi` pretends the classpath does not exist. Intended for tests and CI. |
| absent | false | true | false or true | Configuration fails with the three remediation options. `forceNoGameApi` makes this failure testable even when jars are present. |
| present or absent | true | false | any | Build succeeds. `api-game` is skipped and logs `[weaver] api-game SKIPPED reason=skip-game-api`. |
| present or absent | true | true | any | Configuration fails: skipping and requiring are contradictory. |

`weaver.gameApiAvailable` is true only when the classpath is present and
`skipGameApi` is false. In every skipped mode all `:api-game` tasks except
`:api-game:clean` are disabled, so `build` succeeds and no new jar is produced.
`core` omits `runtimeOnly(project(":api-game"))`, and `agentLibs` (a `Sync`)
removes any stale `api-game-*.jar` left by an earlier with-game build.

The same command adapts to the environment: with a resolved classpath it builds
`api-game`; on a fresh clone or CI without game jars it skips `api-game` and
succeeds. No switch is needed:

```powershell
.\gradlew.bat clean build --no-build-cache --rerun-tasks --no-daemon
```

Force the no-classpath behavior even when jars are present (the test/CI path
used by `tools\verify.ps1` Step 1):

```powershell
.\gradlew.bat clean build --no-build-cache --rerun-tasks --no-daemon "-Pweaver.forceNoGameApi=true"
```

Explicit modes:

```powershell
# Skip the optional game API even when a classpath is present.
.\gradlew.bat build "-Pweaver.skipGameApi=true"

# Fail if no classpath is available (used by packaging/CI jobs that need api-game).
.\gradlew.bat build "-Pweaver.requireGameApi=true"

# Exercise the failure path without deleting jars or renaming the work directory.
.\gradlew.bat build "-Pweaver.forceNoGameApi=true" "-Pweaver.requireGameApi=true"
```

The require failure message names all three remediations: set
`WEAVER_GAME_DIR`, provide
`work/server-26.3/versions/26.3/server-26.3.jar`, or pass
`-Pweaver.skipGameApi=true`.

A skipped build is an intentionally incomplete distribution:
`tools/installer/install.ps1` rejects an agent lib without an
`api-game-*.jar`.

## Agent lib and Class-Path

`:core:agentJar` builds `weaver-agent.jar` and copies the resolved
`runtimeClasspath` into `core/build/agent/lib/`. The jar manifest lists every
lib entry in `Class-Path` as `lib/<file name>`, including exactly one
`lib/api-2.3.2.jar`. When `api-game` is built it also includes exactly
one `lib/api-game-2.3.2.jar`; when it is skipped that entry and the jar
are absent, and `agentLibs` removes any stale copy. The agent jar itself
contains only `net.weaver.core` classes (including `net.weaver.core.jij`).

Inspect the output:

```powershell
.\gradlew.bat :core:agentJar
Get-ChildItem core\build\agent\lib
jar tf core\build\agent\weaver-agent.jar
jar xf core\build\agent\weaver-agent.jar META-INF/MANIFEST.MF
Get-Content META-INF\MANIFEST.MF
```

If `jar` is not on `PATH`, use `& "$env:JAVA_HOME\bin\jar.exe"` instead.

The agent lib is the distribution unit. `tools/installer/install.ps1` copies
the whole directory into `<game>/weaver/agent/lib`, so `api-game` ships
automatically.

## Mod projects and the Gradle plugin

A mod project uses the included plugin build:

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
    gameApiJar.set(rootProject.file("api-game/build/libs/api-game-2.3.2.jar").absolutePath)
}
```

`weaver.apiJar` and `weaver.gameApiJar` are added to `compileOnly` when the
files exist. Their defaults are relative to the root project:
`<root>/api/build/libs/api-2.3.2.jar` and
`<root>/api-game/build/libs/api-game-2.3.2.jar`. Those defaults fit
the Weaver repository; a mod project outside it should set `apiJar` and
`gameApiJar` explicitly or use `WEAVER_API_JAR` / `WEAVER_GAME_API_JAR`. A
missing game API jar only logs a warning, so base-API-only projects keep
working.

The plugin adds `gameDir/classpath.txt`, the server jar, and server libraries to
`compileOnly`, writes `weaver.mod.json`, and registers `weaverModJson`,
`weaverResolveMods`, `weaverJij`, `weaverBuild`, `weaverStage`,
`weaverExportJij`, `runClient`, `runServer`, and `runDatagen`.
`weaverBuild` writes the mod jar manifest with `Weaver-Api-Version: V2`.

## Mod dependency resolution

Declare runtime mod dependencies on the `weaverMod` configuration:

```kotlin
repositories {
    maven("https://repo.example.invalid/") // any Maven repository works
    maven { url = uri("libs/repo") }
}

dependencies {
    weaverMod("com.example:library:[1.0,2.0)")
    weaverMod(project(":companion-mod"))
    weaverMod(files("libs/local-mod.jar"))
    weaverOptional("com.example:addon:1.4.0")
}

weaver {
    localMods.from(layout.projectDirectory.file("libs/manually-built-mod.jar"))
    strictConflicts.set(true)
    embedModDependencies.set(true)
}
```

Resolution behavior:

- `weaverMod` is transitive, resolvable, and never consumable. Transitive Maven
  dependencies declared in the mods' POMs are resolved by Gradle.
- Maven and Gradle version syntax is delegated to Gradle, including Maven ranges
  such as `[1.0,2.0)`.
- `weaverOptional` is resolved leniently for validation and diagnostics, but its
  artifacts are never embedded and never required at runtime.
- `localMods` accepts files, directories, and task outputs such as another
  project's `weaverBuild` archive. A local jar must contain `weaver.mod.json`.
- Artifacts without `weaver.mod.json` are reported as libraries, not mods.
- Every resolved mod is sorted by id, version, and file name; the JSON report at
  `build/weaver/dependencies.json` is deterministic. The task prints stable
  `WEAVER DEP ...` lines and a final `WEAVER DEPS ok=true mods=<n> ...` line.
- Duplicate mod ids are always fatal because the shared class space would
  first-wins-skip one of them. Multiple requested versions of one Maven module
  are reported as `WEAVER DEP CONFLICT kind=module-version`; they fail only when
  `strictConflicts` is true (default `false`; Gradle selects deterministically).
- Unresolved required modules are reported as `kind=missing`; they fail only in
  strict mode. Unresolved optional modules are warnings.
- `addModDependenciesToCompileClasspath` (default `true`) adds `weaverMod` to
  the main source set's `compileOnly` classpath. It is never added to a
  published POM or the runtime classpath directly.

Inspect the report:

```powershell
.\gradlew.bat weaverResolveMods
Get-Content build\weaver\dependencies.json
```

Descriptor metadata (`weaver { dependencies.put(...) }`) is written to
`weaver.mod.json` for the loader's runtime validation. The loader's
`net.weaver.core.VersionRange` understands comparator constraints (`>=`, `<=`,
`>`, `<`, `=`, `~`, `^`, `*`); use that syntax in descriptor metadata. The
Gradle coordinate and the descriptor constraint are separate on purpose:
Gradle resolves artifacts, the loader validates the runtime set.

## Jar-in-jar (JIJ)

When `embedModDependencies` is true (default), `weaverJij` stages every resolved
`weaverMod` and `localMods` jar into the mod jar:

```text
META-INF/weaver/jij.json
META-INF/weaver/jars/<id>-<version>.jar
```

`jij.json` schema version 1 is deterministic:

```json
{
  "schemaVersion": 1,
  "firstWins": true,
  "jars": [
    {
      "file": "META-INF/weaver/jars/library-1.0.0.jar",
      "id": "library",
      "version": "1.0.0",
      "optional": false
    }
  ]
}
```

`weaverBuild` verifies every declared entry exists. `weaverExportJij` extracts
the nested jars into `build/weaver/mods` as `jij-<index>-<id>-<version>.jar` so
`runClient` and `runServer` work before loader-side extraction is wired. Set
`exportJijForRun.set(false)` once the loader extracts nested jars itself.

Policy:

- There is **no classloader isolation**. Nested jars join the same shared class
  space as every other mod and the game.
- Ordering is deterministic: manifest order, then extraction order, both sorted
  by mod id, version, and file name.
- Duplicate **mod ids** fail packaging and dependency resolution. Duplicate
  **class entries** across different mods remain first-wins, matched by
  `[weaver] MOD CLASS CONFLICT class=... mods=... first=...`; the first accepted
  mod on the classpath supplies the class.
- Optional dependencies are not embedded.

`core/jij` is a self-contained loader feature:

- `net.weaver.core.jij.JijLayout` documents the layout.
- `JijScanner.scan(Path)` reads the manifest, or discovers jars below
  `META-INF/weaver/jars/` and reads their descriptors when the manifest is
  absent. It validates paths and reports duplicate ids without deduplicating.
- `JijExtractor.extract(Path, Path, Consumer<String>)` extracts deterministic
  names; `JijCli --jar <mod.jar> --out <dir>` is the manual inspection tool.

**Limitation / integration request:** the current `ModLoader` only discovers
top-level `*.jar` files in the mods directory. `LoaderAgent`/`ModLoader` wiring
must extract or enumerate nested jars before discovery; until then the Gradle
`weaverExportJij` bridge is required for run tasks. `core/jij` deliberately does
not edit `LoaderAgent`, `ClassSpace`, or `ModLoader`; the integration request is
to call `JijExtractor.extract(mod.path(), configDir.resolve("jij/<id>"), log)`
for each accepted top-level mod and pass the extracted jars through discovery,
or to add a `ModLoader.discover(..., JijScanner)` overload.

## Developer hot swap

While a mod is being developed, every change reaches the running game.

- **Applied live** when the JVM allows it.
- **Otherwise, an automatic restart** that comes straight back into the same
  world or server.

Nothing has to be rebuilt or restarted by hand.

`runClient` and `runServer` turn it on by default; `weaver { hotSwap = false }`
turns it off. They start the game with:

```text
-Dweaver.dev.hotswap=true
-Dweaver.dev.hotswap.dirs=<the project's build/classes/java/main>
-Dweaver.dev.hotswap.resources=<the project's build/resources/main>
```

Recompile while the game runs (the IDE's build, or `gradlew classes`; for
resources, `processResources`). The agent
(`net.weaver.core.dev.HotSwapService`) looks at the folders every 0.7 s,
waits for the compiler to finish writing, and then:

| Change | What happens |
|---|---|
| Method bodies of a loaded class | Swapped live (`Instrumentation.redefineClasses`). The access transformers run again on the new bytes. |
| A class not loaded yet | Live: it loads the new bytes when first used. |
| A new class (not in the jar) | Live: it is added to the class path in an overlay jar before the classes that use it are swapped. |
| Assets (`assets/`) | Live: the resources folder is an always-on pack over the jar's, and the client reloads its resources. |
| Data (`data/`: recipes, tags, functions, loot, data registries) | Live: the server reloads its data, like `/reload`. |
| Config files | Live: config watching is on in dev runs (`ConfigReloadedEvent`). |
| A class's shape (fields or methods added or removed) | Automatic restart. |
| A mixin class | Automatic restart. Mixins were merged into game classes at boot. |
| A static initializer (`<clinit>`) of a loaded class | Automatic restart. The JVM would accept it and never run it, so the change is detected by comparing the code, not left stale. |
| A mod's startup methods (`onInitialize`, `onRegister`, `onCommonSetup`, ...) | Automatic restart, for the same reason. |
| A jar added to or replaced in the mods folder | Automatic restart. |

### The automatic restart

1. The game shows "restarting to apply your changes", saves, and quits normally.
2. It leaves `dev-restart.json` in its data folder, naming the singleplayer
   world it was in or the server address.
3. The launcher starts it again with `--quickPlaySingleplayer` or
   `--quickPlayMultiplayer`, so it lands back in the same place.
   - Gradle's `RunGameTask` and `tools/launcher/launch.ps1` both do this.
4. The restarted game runs the newest compiled code even though the mod jar
   was not rebuilt:
   - classes whose bytes differ from the jar are served from the class folder;
   - classes the jar lacks come from an overlay jar;
   - resources come from the resources folder.

Several changes in one compile cause one restart.
`-Dweaver.dev.restart=off` keeps the game running and only logs
`HOTSWAP RESTART NEEDED`.

**Shape changes on a standard JVM.** HotSpot cannot change a loaded class's
shape, so shape changes restart. A JVM with enhanced class redefinition (the
JetBrains Runtime with `-XX:+AllowEnhancedClassRedefinition`) swaps them
live instead.

### Logs and events

Every swap is logged:

```text
[weaver] HOTSWAP ok swapped=N skipped=M
[weaver] HOTSWAP SKIPPED class=... reason=...
[weaver] HOTSWAP RESOURCES assets=... data=...
[weaver] HOTSWAP RESTART requested why=...
```

Each swap is also posted as `HotSwapEvent` on `EventBus.GAME`, for a mod that
caches values computed by the old code.

### Outside Gradle

The launcher takes:

- `-HotSwapDirs <class folders separated by ;>`
- `-HotSwapResources <resource folders separated by ;>`

The agent manifest declares `Can-Redefine-Classes: true`.

Hot reload is a development feature: players' games never start with it.

### Proofs

`examples/hotswap-proof/run.ps1` proves all of it in a real game, across the
restart. Run it with `-Side server` and with `-Side client`.

**Phase 1:**

- a loaded class runs new code that uses a brand-new class;
- a class first used after the change loads it;
- a new function in the resources folder is present after a live data reload;
- a language file change reloads the client's assets;
- a shape change and a static-initializer change trigger one automatic
  restart.

**Phase 2**, after the restart, with the jar never rebuilt:

- the client is back in the same world;
- the reshaped class runs;
- the new static initializer value is set;
- the newest code and the data are there.

Unit tests: `HotSwapServiceTest` and `MethodPrintsTest`.

## Mods-directory change watch

`net.weaver.core.watch.ModWatchService` observes the mods directory without
changing the loaded mod set. It is opt-in:

```text
-Dweaver.mods.watch=true
```

When enabled, the agent integration starts the shared watcher after discovery:

```java
if (ModWatchService.isEnabled())
{
    ModWatchService.shared().start(modsDir, log);
}
```

The service uses one daemon thread and one NIO `WatchService`. It watches for
created, deleted, and modified `.jar` files, coalesces events with a 500 ms
debounce, and compares the directory with the previous scan. Changes are
reported as bounded `ModChangeReport` batches and logged as:

```text
[weaver] MODS CHANGED path=<jar> kind=ADDED|REMOVED|MODIFIED size=<n> modified=<millis> action=restart-required
[weaver] MODS CHANGED files=<n> emitted=<n> truncated=<true|false> action=restart-required
```

`ModsChangedEvent` is posted on `EventBus.GAME` with logical side `BOTH` so
mods and operations tooling can observe the change. It is informational only.
The loader does not apply the change in this version: mods load once at boot,
`Instrumentation.appendToSystemClassLoaderSearch` cannot remove a jar, and
already-defined classes cannot be replaced. Every reported change requires a
game restart. `scanOnce()` is the deterministic test/polling entry point, and
`ModWatchService.manual(...)` creates a watcher without the daemon thread.
`close()` is idempotent.

## Remapping

Minecraft 26.3 does **not** need a remap step in this repository:

- `work/game-26.3/client.jar` and
  `work/server-26.3/versions/26.3/server-26.3.jar` are the official artifacts.
  Their SHA-1 and `version.json` metadata match, and `jar tf` lists official
  `net/minecraft/*` and `com/mojang/*` names directly.
- `javap` shows readable official members such as
  `net.minecraft.world.entity.Entity.isSpectator()` and
  `net.minecraft.server.Main.forceUpgrade(...)`. There are no `class_*`,
  `method_*`, or `field_*` intermediary names.
- Access transformers are listed in `weaver.mod.json` (`accessTransformers`).
  Each line is an access (`public`, `protected`, `private`, or one of them with
  `-f` to drop `final`), a class, and optionally a member:
  `public net.minecraft.world.food.FoodData foodLevel`. A member with a
  descriptor (`tick(I)V`) is that one method. A bare name is the field with that
  name; when the class has no field with that name, it is every method with
  that name (all overloads). A field rule therefore never changes a method that
  shares the name (a record accessor, for example).
- `api-game`, the proof mod, hooks, and access transformers compile against
  those official names directly.

Therefore no remap pipeline is added. The `MappingService` SPI and the refmap
remapping in `mixin/src/net/weaver/mixin/core/RefMaps.java` remain as
groundwork: for 26.3 the mapper is unbound (identity path). A future obfuscated
artifact or intermediary-compiled third-party mod would bind a real
`MappingService` without changing the plugin layout.

## Patched game artifact

The patcher is a local build step:

```powershell
tools\patcher\patch-game.ps1 -OutDir work\patched
```

It writes `<OutDir>\client-patched.jar` and `<OutDir>\server-patched.jar`. The
patched jars add `net.weaver.patched.PatchedGame` with the
`weaver$patchLevel()I` method and `net.weaver.patched.WeaverAttachmentHolder`
to `ItemStack`, `Entity`, `BlockEntity`, `Level`, and `LevelChunk`; `ItemStack`
and `Level` also implement `PatchedGame`. `WeaverApi.PATCH_LEVEL` is `1` for
this build.

Generate the patched artifacts locally from the official inputs
(`work/game-26.3/client.jar` and
`work/server-26.3/versions/26.3/server-26.3.jar`). Never redistribute patched
Minecraft jars or commit them to the repository: the patcher and the official
artifacts are the distribution. The official 26.3 artifacts are unobfuscated
(see [Remapping](#remapping)), so no remap step is needed before patching; the
patch step is still applied.

The patcher is expected to be deterministic: the same official inputs and
patcher revision must produce byte-identical jars. Check a build by running the
patcher twice into separate output directories and comparing hashes:

```powershell
tools\patcher\patch-game.ps1 -OutDir work\patched-a
tools\patcher\patch-game.ps1 -OutDir work\patched-b
Get-FileHash work\patched-a\client-patched.jar, work\patched-b\client-patched.jar
Get-FileHash work\patched-a\server-patched.jar, work\patched-b\server-patched.jar
```

Pass a patched artifact to the launcher with the side-specific flag or
environment variable:

```powershell
powershell -ExecutionPolicy Bypass -File tools\launcher\launch.ps1 -Side client -GameDir work\game-26.3 -PatchedClientJar work\patched\client-patched.jar
powershell -ExecutionPolicy Bypass -File tools\launcher\launch.ps1 -Side server -ServerDir work\server-26.3 -PatchedServerJar work\patched\server-patched.jar
```

`WEAVER_PATCHED_CLIENT_JAR` and `WEAVER_PATCHED_SERVER_JAR` supply the same
paths when the flags are omitted. The launcher replaces only the game jar
entry, preserves every other classpath entry in order, and fails when a
supplied path does not exist. It prints
`LAUNCH PATCH side=<side> patched=true jar=<path>` or
`LAUNCH PATCH side=<side> patched=false` before starting the JVM. Unpatched
launches are unchanged and continue to use the runtime transformer path.

## Publishing

`api` publishes a `weaverApi` Maven publication with a sources jar and POM
metadata (name, description, LGPL-2.1-only license, developer, `weaver.apiVersion`,
`weaver.artifactType`). `api-game` publishes `weaverGame` with the same license
and developer metadata plus `weaver.gameApiVersion` and `weaver.minecraftVersion`
(`26.3` against API `V2`).

```powershell
.\gradlew.bat :api:publishWeaverApiPublicationToWeaverLocalRepository
.\gradlew.bat :api-game:publishWeaverGamePublicationToWeaverLocalRepository
```

The local repositories are `api/build/repo` and `api-game/build/repo`. POMs
contain no game or leaked dependencies: `api` has no runtime dependencies,
`api-game` has only the base `api` dependency, and game classes are
`compileOnly`. `:api:verifyApiPom` and
`:api-game:verifyPomHasNoGameDependencies` fail the build if metadata is missing
or if a POM references `net.minecraft`, `com.mojang`, a game jar, or
`api-game`.

Publish `api`, `api-game`, and the other modules as separate artifacts. Never
merge `api-game` into `api` or the agent jar, and never add a game jar as a
published dependency.

## Run configurations and IDE integration

`runClient`, `runServer`, and `runDatagen` are ordinary Gradle tasks. They build
the command directly, so they work from the command line, IntelliJ, and Eclipse
without a heavyweight IDE plugin.

```kotlin
weaver {
    jvmArgs.add("-Dweaver.example=true")
    programArgs.add("--bonus")           // appended after the required game args
    environment.put("WEAVER_FIXTURE", "1")
    debug.set(true)                       // JDWP agent
    debugPort.set(5005)
    debugSuspend.set(true)
    workingDir.set(rootProject.layout.projectDirectory.dir("run").asFile.absolutePath)
    serverPort.set(25566)                 // runServer only; default 25566
}
```

- `runClient` uses `gameDir/classpath.txt`, the client main class, the offline
  username, and optional `quickPlayWorld` / `quickPlayServer`.
- `runServer` uses `serverDir/versions/26.3/server-26.3.jar` plus every jar under
  `serverDir/libraries`.
- `runDatagen` runs `datagenClass` with `--out build/weaver/datagen`, then
  appends `programArgs`.
- All three accept `jvmArgs`, `environment`, `debug`, `debugPort`,
  `debugSuspend`, and `workingDir`. Use `-Pweaver.dryRun=true` to print the exact
  command without starting the game:
  `RUN DRY side=server workingDir=... env=... command=...`.
- IntelliJ IDEA: import the Gradle project and run the generated `runClient` /
  `runServer` configurations, or use the Gradle tool window. For debugging, set
  `debug` and attach to the configured port. Eclipse: use a Gradle launch
  configuration from the Gradle Tasks view. No Weaver-specific IDE plugin is
  installed.
- The plugin stages the built mod and extracted JIJ jars into
  `build/weaver/mods`; config, data, and logs live under `build/weaver/`.

## Versioning

- Project version: `2.3.2` (root `build.gradle.kts`, all projects).
- Base API version: `WeaverApi.VERSION = "2.1.0"`, also written to the `api`
  jar manifest as `Weaver-Api-Version: V2`.
- Game API version: `WeaverGameApi.VERSION = "26.3"`, also written to the
  `api-game` jar manifest as `Weaver-Game-Api-Version: 26.3`.
- Mod jars built by the plugin: `Weaver-Api-Version: V2`.
- Gradle plugin version: `2.1.0` (independent of the API artifact version).

The base API and the game API are separate artifacts with separate versions.
A new Minecraft release rebuilds `api-game` without changing `api`.

## Verification tasks

| Task | Checks |
| --- | --- |
| `:api:verifyApiPom` | The published `weaverApi` POM has the required name, license, and `weaver.apiVersion` metadata, and no `net.minecraft`, `com.mojang`, or `api-game` references. |
| `:api-game:verifyGameClasspath` | Defensive guard: fails with the three remediation options only when `-Pweaver.requireGameApi=true` is set and no classpath is available. The root build rejects that configuration first, so a normal build never reaches this task. |
| `:api-game:verifyNoGameClasses` | Fails if the `api-game` jar contains `net/minecraft/` or `com/mojang/` entries. |
| `:api-game:verifyPomHasNoGameDependencies` | Fails if the generated `weaverGame` POM lacks metadata or references game jars or Minecraft/Mojang coordinates. |
| `:core:verifyAgentLayout` | Fails unless `core/build/agent/lib` has exactly one `api-game-*.jar`, that jar declares `Weaver-Game-Api-Version: 26.3` and has no game classes or base `net/weaver/api/` classes, and the agent manifest `Class-Path` lists exactly one `lib/api-game-*.jar` and one base `lib/api-*.jar`. |
| `weaverResolveMods` | Resolves `weaverMod`, `weaverOptional`, and local mods; reports version/mod-id conflicts; writes `build/weaver/dependencies.json`. |
| `weaverJij` | Stages deterministic nested mod jars and `jij.json`; fails on duplicate ids. `weaverBuild` verifies every declared entry exists. |
| `weaverExportJij` | Extracts nested jars into `build/weaver/mods` for run tasks. |

`verifyNoGameClasses` runs as part of `:api-game:assemble`, `:api-game:check`,
and `:api-game:publish`; `verifyPomHasNoGameDependencies` runs as part of
`:api-game:check` and `:api-game:publish`. `verifyAgentLayout` runs as part of
`:core:check` and `:core:assemble` when the game API is available. When
`api-game` is skipped, all game-API verification tasks are skipped/no-op and
never inspect a missing jar.

## Toolchain fixture and proof

`examples/toolchain-fixture` is a runnable proof of the toolchain:

- a Maven-like dependency resolved from a generated local repository with a
  `[1.0,2.0)` version range;
- a transitive dependency declared in the library POM;
- an optional dependency resolved but never embedded;
- a nested subproject embedded as jar-in-jar;
- deterministic `jij.json` and nested entries;
- loader discovery through `core/jij` and `ModLoader`;
- a real dedicated-server run.

```powershell
cd examples\toolchain-fixture
.\build.ps1              # API/agent, fixture repo, mod build, JIJ verification, loader proof
.\build.ps1 -SkipRun     # stop after jar-layout verification
.\loader-proof.ps1       # loader-only proof
.\proof.ps1              # real server proof on port 25567
```

The fixture repository is generated at `examples/toolchain-fixture/repo` and is
not checked in. The current loader-side proof uses the toolchain extraction
bridge; native `LoaderAgent` extraction remains the integration request above.

## Build verification script and CI

`tools\verify.ps1` is the reproducibility gate. It runs ten checks, prints
`PASS`, `FAIL`, or `SKIP` for each, saves captured stdout/stderr under the
system temp directory, and exits non-zero if any check fails. The final line is
`WEAVER VERIFY ok=true steps=10` or `WEAVER VERIFY ok=false steps=10`, preceded by
the list of steps that ran.

| Step | Command | Assertion |
| --- | --- | --- |
| 1 | `clean build --no-build-cache --rerun-tasks --no-daemon -Pweaver.forceNoGameApi=true` | `BUILD SUCCESSFUL`, the `reason=no-game-classpath` log line, and no `api-game` jar. |
| 2 | `clean build --no-build-cache --rerun-tasks --no-daemon` | `BUILD SUCCESSFUL`, exactly one `api-game-*.jar` in `core/build/agent/lib`, and exactly one `lib/api-game-*.jar` entry in the agent manifest. Prints `SKIP` when no game classpath is present. |
| 3 | `build -Pweaver.forceNoGameApi=true -Pweaver.requireGameApi=true` | Fails and prints `WEAVER_GAME_DIR`, `server-26.3.jar`, and `-Pweaver.skipGameApi=true`. |
| 4 | `build -Pweaver.skipGameApi=true` | `BUILD SUCCESSFUL`, the `reason=skip-game-api` log line, and no stale `api-game` jar in the agent lib or manifest. |
| 5 | `-p tools\gradle-plugin build` | `BUILD SUCCESSFUL`, including the TestKit suite. |
| 6 | `:api:verifyApiPom :api-game:verifyPomHasNoGameDependencies --no-daemon` | Both POM gates run and pass; no game or leaked dependencies. |
| 7 | `examples\toolchain-fixture\build.ps1` | `TOOLCHAINFIXTURE BUILD ok=true` and `TOOLCHAINFIXTURE LOADER ok=true`. |
| 8 | `git diff --check` | No whitespace errors; LF/CRLF warnings are ignored. |
| 9 | `:core:agentJar` | Restores the with-game agent lib: exactly one `api-game-*.jar` and exactly one manifest entry. Prints `SKIP` when no game classpath is present. |
| 10 | `tools\patcher\verify.ps1` | Runs the binary patcher twice (byte-identical output, signatures and manifest digests stripped, added interfaces checked with `javap`, all classes loaded with `-Xverify:all`) and then `tools\sourcepatcher\verify.ps1` (Vineflower decompile, unified-diff patch, `javac --release 25` recompile, added member checked, all classes loaded). Prints `PATCHER VERIFY ok=true` and `PATCHER VERIFY source=ok`; `SKIP` when the official jars or patcher are missing. |

Run it from the repository root:

```powershell
powershell -ExecutionPolicy Bypass -File tools\verify.ps1
```

There is no GitHub remote and no `.github` directory in this repository, so no
workflow file is checked in. Any CI runner can execute the same gates directly:

```powershell
.\gradlew.bat clean build --no-build-cache --rerun-tasks --no-daemon "-Pweaver.forceNoGameApi=true"
.\gradlew.bat build "-Pweaver.forceNoGameApi=true" "-Pweaver.requireGameApi=true"
.\gradlew.bat build "-Pweaver.skipGameApi=true"
.\gradlew.bat -p tools\gradle-plugin build
.\gradlew.bat :api:verifyApiPom :api-game:verifyPomHasNoGameDependencies --no-daemon
powershell -ExecutionPolicy Bypass -File tools\verify.ps1
powershell -ExecutionPolicy Bypass -File examples\proof\run-server-shared.ps1 -AllowDocumentedBlocker
```

`run-server-shared.ps1` exits 1 when the shared-mode proof does not pass. The
`-AllowDocumentedBlocker` switch is the only way to make it exit 0 without a
passing proof; it acknowledges the documented architectural blocker and must
not be used to claim shared mode works. Shared class space is experimental and
unsupported as a proof gate.

Use JDK 26, the repository Gradle wrapper, and Windows PowerShell 5.1 or later.
The no-game build is the default CI gate; the with-game gate only runs when a
classpath is provisioned (`WEAVER_GAME_DIR` or the server jar fallback), which
the verify script detects automatically.

## Installer

```powershell
.\gradlew.bat :core:agentJar
powershell -File tools\installer\install.ps1 -GameDir work\game-26.3
```

The installer copies the agent, the whole agent lib (including `api-game`), and
the proof mods into `<GameDir>/weaver`. It fails when the installed agent lib
does not contain exactly one `api-game-*.jar`, which catches a packaging
regression early.

## Limitations

- The loader still discovers one jar level; native nested-jar discovery is the
  `LoaderAgent` integration request above. Run tasks use the toolchain
  `weaverExportJij` bridge until it is wired.
- `weaverMod` resolves Maven/Gradle ranges, but descriptor dependency ranges use
  the loader's comparator syntax. Keep the two in sync manually.
- Optional dependencies are validated and reported, not conditionally loaded;
  there is no runtime optional-resolution engine.
- Every `weaverMod` artifact must be a Weaver mod with `weaver.mod.json`. A
  plain Maven library belongs on `implementation`/`runtimeOnly`, not on
  `weaverMod`; the resolver reports such artifacts as libraries, but the JIJ
  packager rejects them rather than silently shipping an unloadable jar.
- Run tasks construct the Java command directly instead of calling
  `tools/launcher/launch.ps1`; the launcher remains the proof and distribution
  entry point.
- No multi-version toolchain, Modrinth/CurseForge automation, remap pipeline, or
  classloader isolation is provided.
- The mods-directory watcher is opt-in and report-only. It does not add,
  remove, or replace jars at runtime; every change requires a restart.
