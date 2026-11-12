# Weaver Platform Ops Proof

Self-contained acceptance proof for the Weaver 2.2 Platform Ops workstream.
It builds synthetic mod jars in a temporary directory, runs real loader
discovery against them, runs the real mod-list CLI in child processes, and
checks the version and update contracts.

## What it proves

- `ClassSpace.selectMode` selects `SHARED` only for an explicit request;
  `SYSTEM` is the default for every unrequested combination. `Mode` declares
  both `SYSTEM` and `SHARED`.
- `ModLoader.discover(..., ModPolicies.warn())` reports synthetic mods with the
  expected statuses and deterministic load order:
  - `base` and `dependent` load, with `dependent` after `base`;
  - a missing required dependency is `REJECTED_DEPENDENCY`;
  - an unsatisfied dependency version is `REJECTED_VERSION`;
  - a dependency cycle is reported as a `CYCLE` diagnostic while the cycle
    members still load in filename order;
  - two mods shipping the same class produce a `DUPLICATE_CLASS` warning;
  - a mod physically containing `net/weaver/api/...` produces an `API_SHADOW`
    warning.
- `ModPolicies.fatal()` aborts a duplicate-class-only fixture with
  `ModPolicyException`, `FATAL` severity, and the duplicate class subject.
- The real agent fails fast under the same fatal policy: a child JVM started
  with `-javaagent` and `-Dweaver.policy.duplicateClasses=fatal` exits non-zero,
  prints `MOD POLICY FATAL`, and never reaches `AGENT READY`.
- The real agent writes the boot mod list when `modListFile`/`modListFormat`
  are set; the child JVM writes a JSON report with the schema, mods, statuses,
  and diagnostics, and logs `MOD LIST path=...`.
- `ModListCommand` text and JSON output carry the contracted fields
  (`schemaVersion`, `loader`, `side`, `summary`, `mods[].id/version/side/
  dependencies/mixins/accessTransformers/loadOrder/status`, `diagnostics`) and
  the documented exit codes: `0` success, `1` fatal policy, `2` bad arguments.
- The API/version matrix agrees with the 2.2 workstream: `WeaverApi.VERSION` is
  `2.1.0`, `FEATURE_LEVEL` is `2.2`, `GAME_VERSION` and `HOOK_TARGET` are
  `26.3`, `VersionChecks.check(null, "26.3")` is compatible, and
  `CompatibilityMatrix.currentLevel()` is `SUPPORTED`.
- The update contract returns `NOT_CONFIGURED` by default, accepts a fake
  installed `UpdateCheck`, returns its result unchanged, and resets cleanly.
- `ModWatchService.scanOnce` detects added, modified, and removed `.jar` files,
  ignores non-jar files, is idempotent for an unchanged directory, logs
  `action=restart-required`, and remains safe after `close()`.
- `DataPackRepository.load` assigns priorities in input order, reads
  `pack.mcmeta`, lets a higher-priority pack replace a lower-priority entry,
  and honors a replacing tag declaration.
- `DataRegistryManager.reloadAll` refuses a frozen registry, reports its id, and
  still applies the snapshot to an open registry.

The optional shared-mode checks run the existing
`examples/proof/run-server-shared.ps1` (exit `0` plus
`SHARED MODE RESULT ok=true`) and, for the client,
`run-shared-client.ps1` (exit `0` plus `OPS SHARED CLIENT RESULT ok=true` and a
`PROOF RESULT ok=true side=client` line). Both launch the real game.

## Run

```powershell
powershell -ExecutionPolicy Bypass -File examples\ops-proof\run.ps1
```

Optional shared class-space server proof (slow; launches the real game server):

```powershell
powershell -ExecutionPolicy Bypass -File examples\ops-proof\run.ps1 -Shared
```

The thin wrapper is equivalent:

```powershell
powershell -ExecutionPolicy Bypass -File examples\ops-proof\run-shared.ps1
```

Optional shared class-space client proof (slow; launches the real client with
the deterministic fixture world):

```powershell
powershell -ExecutionPolicy Bypass -File examples\ops-proof\run-shared-client.ps1
```

### Prerequisites

- JDK 26 on `JAVA_HOME` or at `C:\Program Files\Java\jdk-26.0.1`.
- Agent artifacts at `core/build/agent/lib` and
  `core/build/agent/weaver-agent.jar`. Build them once with:

  ```powershell
  .\gradlew.bat :core:agentJar
  ```

  `run.ps1` attempts that build when the artifacts are missing, but the proof
  itself does not use Gradle after they exist.
- For `-Shared` and `run-shared-client.ps1`: the prerequisites of the real
  proof scripts, including the game/server directories, the fixture world, and
  a built `examples/proof/out/proof.jar`.

### Markers and exit codes

Each successful check prints:

```
OPS PROOF CHECK ok=true <name>
```

The run ends with:

```
OPS PROOF RESULT ok=true checks=<n>
```

The current check set ends with `checks=13` (the original ten checks plus
`mod-watch`, `data-pack-precedence`, and `data-registry-reload`).

A failure prints `OPS PROOF CHECK ok=false <name> reason=...` followed by
`OPS PROOF RESULT ok=false ...` and exits `1`. The optional shared run prints
`OPS SHARED PROOF ok=true` or `OPS SHARED PROOF ok=false ...`.

## Output layout

The proof compiles to `examples/ops-proof/out/classes`. Temporary fixtures live
under the operating system temp directory and are deleted before the process
exits.

## Limitations

- The synthetic jars contain descriptor JSON and dummy class entries. The proof
  validates discovery, reporting, and policy behavior; it does not load or link
  the dummy classes, so duplicate-class and API-shadow detection is proven at
  the class-entry scan level, not through JVM linkage.
- The default run never launches Minecraft and never uses instrumentation.
  `ClassSpace.create` in shared mode is not exercised without a real game
  classpath; only mode selection is proven here, with `ClassSpace.create`
  covered by the optional shared server and shared client proofs.
- The standard `examples/proof/run-server.ps1` and
  `examples/proof/run-client.ps1` system-mode proofs passed in the 2.2
  verification run. This proof does not re-run them by default.
- The update contract proof installs an in-process fake `UpdateCheck`; it
  performs no network I/O and does not test an HTTP implementation.
- The scripts require Windows PowerShell. The Java proof itself is portable.
