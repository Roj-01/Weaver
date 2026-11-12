# Contributing

## Requirements

- JDK 26
- The Gradle wrapper in this repository

## Build

```powershell
.\gradlew.bat build
```

On Unix, use `./gradlew` with the same tasks. Run a single subproject build when
working on one area:

```powershell
.\gradlew.bat :core:build
```

## Verification

Run the `api` and `core` suites after changing the event bus, registry API,
hooks, scanner, lifecycle, or mod loader:

```powershell
.\gradlew.bat :api:test :core:test --rerun-tasks
```

For an end-to-end check, build and run the proof mod on a dedicated server:

```powershell
.\gradlew.bat :core:agentJar
cd examples\proof
.\build.ps1
.\run-server.ps1
```

Use `.\run-client.ps1` or `.\run-all.ps1` in the same directory for the client
side. The scripts read the game from `WEAVER_GAME_DIR` and the server from
`WEAVER_SERVER_DIR`, defaulting to `work/game-26.3` and `work/server-26.3`.

## Build and CI

The default build does not require Minecraft jars. When no game classpath is
available, `api-game` is skipped, the build logs
`[weaver] api-game SKIPPED reason=no-game-classpath` once, and the rest of the
tree still builds and tests.

```powershell
# No-game build (fresh clone and default CI gate).
.\gradlew.bat clean build --no-build-cache --rerun-tasks --no-daemon "-Pweaver.forceNoGameApi=true"

# With-game build (requires WEAVER_GAME_DIR/classpath.txt or the server jar fallback).
.\gradlew.bat clean build --no-build-cache --rerun-tasks --no-daemon

# Require the game API; fails with the remediation options when it is absent.
.\gradlew.bat build "-Pweaver.forceNoGameApi=true" "-Pweaver.requireGameApi=true"

# Skip the game API explicitly, even when a classpath is present.
.\gradlew.bat build "-Pweaver.skipGameApi=true"

# Included Gradle plugin build and TestKit suite.
.\gradlew.bat -p tools\gradle-plugin build
.\gradlew.bat -p tools\gradle-plugin test --no-daemon --console=plain

# Published POM metadata and no-game-dependency gates.
.\gradlew.bat :api:verifyApiPom :api-game:verifyPomHasNoGameDependencies --no-daemon

# Toolchain fixture: range + transitive + optional dependency, jar-in-jar,
# loader discovery, and an optional real-server proof.
cd examples\toolchain-fixture
.\build.ps1
.\proof.ps1

# Full reproducibility gate (nine PASS/FAIL/SKIP checks).
# Step 9 rebuilds the with-game agent lib after the explicit skip build.
powershell -ExecutionPolicy Bypass -File tools\verify.ps1
```

When adding a toolchain feature, add a TestKit test under
`tools/gradle-plugin/src/test/java`, update `docs/toolchain.md`, and keep the
fixture proof green. `weaverMod`/`weaverOptional` dependencies must not leak into
published POMs or the runtime classpath by default.

`examples\proof\run-client.ps1` copies the deterministic fixture world from
`examples\proof\fixture-world\` to `saves\WeaverProofFixture` before each run.
`run-server-shared.ps1` exits 1 when the shared-mode proof does not pass;
`-AllowDocumentedBlocker` only acknowledges the documented blocker and is not a
passing proof.

Quote `-Pname=value` arguments in PowerShell; unquoted property arguments can
be split at the dot. See `docs/toolchain.md` for the full behavior matrix and
the exact meaning of `weaver.requireGameApi`, `weaver.skipGameApi`, and
`weaver.forceNoGameApi`.

## Code style

- Java 26, UTF-8 source files.
- Package root is `net.weaver`.
- Keep the loader and the mod API separate: `core/` must not depend on optional
  API wrappers.
- Prefer the JDK class-file API or ASM already used by the project; do not add a
  new bytecode library without a reason.
- Keep changes small and add a runnable proof under `spike/` when changing the
  class space, Mixin, or access transformers.

## Scope

The new trunk does not include:

- runtime reload, watch, or swap logic;
- compatibility layers for other mod loaders;
- per-module isolation or permission gates.

Do not add those back.

## License

By contributing you agree that your contribution is licensed under LGPL-2.1-only.
