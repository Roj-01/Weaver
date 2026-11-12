# Platform operations

Operational reference for running Weaver 2.2, inspecting the mod list, applying
class-space policies, checking the update contract, and verifying version
compatibility. Every runtime behavior described here is implemented by the
loader classes named in the text; the update endpoint shape is a non-normative
contract, not an implementation. See [Architecture](architecture.md) for the
runtime design.

## Supported modes

`ClassSpace` has two modes. `SYSTEM` is the default and the supported
production mode; `SHARED` is opt-in and was verified on the dedicated server.

| Mode | Selection | Game classpath | Classloader | Status |
|---|---|---|---|---|
| `SYSTEM` | Default; no shared request. | The normal game classpath passed to the JVM. | System classloader; mod jars are appended with `Instrumentation.appendToSystemClassLoaderSearch` (`ClassSpace.java:115-119`). | Supported production mode and the 2.1 baseline. |
| `SHARED` | `tools/launcher/launch.ps1 -SharedClassSpace`, or `WEAVER_SHARED_CLASS_SPACE=true`; the launcher then sets `-Dweaver.classspace.shared=true`. | Passed to the agent in a file; the system classpath is the agent jar and its manifest class path. | One `SharedModClassLoader` (name `weaver-shared`) for the game and every mod (`SharedModClassLoader.java:13-17`). | Opt-in; verified on the dedicated server and the client. |

`ClassSpace.selectMode` returns `SHARED` only for an explicit request. There is
no implicit shared mode (`ClassSpace.java:140-145`). When shared mode is
selected, the launcher prints:

```text
WARNING: shared class space is opt-in and experimental; it requires the net.weaver.core.SharedMain bootstrap.
WARNING: shared mode passes the game classpath to the agent by file and hosts game and mod classes on the shared loader; the default system mode is unchanged.
```

The first line is the launcher's own warning. It is not a claim that shared
mode is the default or production mode.

## Launcher

`tools/launcher/launch.ps1` is the supported launcher for development,
verification, and operators. It:

- finds JDK 26 from `JAVA_HOME` or `C:\Program Files\Java\jdk-26.0.1`;
- requires the agent jar at `core\build\agent\weaver-agent.jar`;
- defaults `ModsDir` to `<repo>\mods`, `ConfigDir` to `<repo>\run\config`,
  `DataDir` to `<repo>\run\data`, `Log` to `<repo>\run\logs\<side>.log`, and
  `Markers` to `<repo>\run\logs\<side>-markers.log`;
- creates the config and data directories, and deletes the existing log and
  marker files before starting;
- passes the agent argument
  `-javaagent:<agent>=side=<side>;integratedServer=<bool>;modsDir=<dir>;configDir=<dir>;dataDir=<dir>;markersFile=<file>`;
  `integratedServer` is `true` for every client (it hosts its single-player
  worlds, so the server hooks such as ticks and player joins must apply there
  too) unless `-NoIntegratedServer` is given, and `false` for a server; the
  Gradle `runClient` task does the same. `-IntegratedServer` is still accepted
  and changes nothing;
- for the client, requires `<GameDir>\classpath.txt` and uses the game
  directory for natives/assets;
- for the server, requires `<ServerDir>\versions\26.3\server-26.3.jar` and
  builds the classpath from `libraries\**\*.jar` plus that jar;
- waits up to `-TimeoutSeconds` for `-WaitFor` (a regex matched against the
  log), stops the process, and prints `LAUNCH RESULT side=... ok=...`.

A launch is reported `ok=true` only when `-WaitFor` matches. Without
`-WaitFor`, the process is still run and stopped at timeout or exit, but the
result line is `ok=false`. Use `-DryRun` to print the command without starting
the game and `-Interactive` to run in the foreground.

A non-interactive client starts minimized (`LAUNCH WINDOW minimized`): on some
GPU drivers a visible window makes the game crash natively at startup much more
often, vanilla included. Pass `-Visible`, or set `WEAVER_CLIENT_VISIBLE=1`, to
show the window. Proofs that render (HUD, hands, tooltips) still pass
minimized.

Default client and server launches:

```powershell
powershell -ExecutionPolicy Bypass -File tools\launcher\launch.ps1 -Side client -GameDir work\game-26.3 -ModsDir mods
powershell -ExecutionPolicy Bypass -File tools\launcher\launch.ps1 -Side server -ServerDir work\server-26.3 -ModsDir mods
```

Shared client and server launches:

```powershell
powershell -ExecutionPolicy Bypass -File tools\launcher\launch.ps1 -Side client -GameDir work\game-26.3 -ModsDir mods -SharedClassSpace
powershell -ExecutionPolicy Bypass -File tools\launcher\launch.ps1 -Side server -ServerDir work\server-26.3 -ModsDir mods -SharedClassSpace
```

`WEAVER_SHARED_CLASS_SPACE=true` is equivalent to `-SharedClassSpace`
(`launch.ps1:62`).

### Patched artifacts

The launcher accepts `-PatchedClientJar <path>` and
`-PatchedServerJar <path>`, or `WEAVER_PATCHED_CLIENT_JAR` and
`WEAVER_PATCHED_SERVER_JAR` when the flags are omitted:

```powershell
powershell -ExecutionPolicy Bypass -File tools\launcher\launch.ps1 -Side client -GameDir work\game-26.3 -PatchedClientJar work\patched\client-patched.jar
powershell -ExecutionPolicy Bypass -File tools\launcher\launch.ps1 -Side server -ServerDir work\server-26.3 -PatchedServerJar work\patched\server-patched.jar
```

The client flag replaces the game jar entry in `<GameDir>\classpath.txt` (the
first entry, `client.jar`, or an equivalent official client jar). The server
flag replaces the `versions\26.3\server-26.3.jar` entry in the classpath built
from `<ServerDir>\libraries`. Every other entry keeps its position. In
`-SharedClassSpace` mode the adjusted client classpath is written to
`shared-classpath.txt` next to the log file so `SharedModClassLoader` sees the
patched entry.

Before launching, the launcher prints
`LAUNCH PATCH side=<side> patched=true jar=<path>` or
`LAUNCH PATCH side=<side> patched=false`. A supplied path that does not exist
fails the launch with `patched client jar missing` or
`patched server jar missing`. When neither the flags nor the environment
variables are set, the classpath and launch behavior are unchanged.

The patched jars are produced by `tools/patcher/patch-game.ps1 -OutDir <dir>`
as `client-patched.jar` and `server-patched.jar`. See
[Toolchain: patched game artifact](toolchain.md#patched-game-artifact) for the
local generation and determinism rules.

## Shared mode

### What the launcher does

In shared mode the launcher starts `net.weaver.core.SharedMain`, not the game
main class. The system classpath is the agent jar only; the agent jar's
manifest `Class-Path` supplies the agent lib, including `api`, `api-game`,
Gson, ASM, and the Mixin integration.

For the server, the launcher writes the game classpath to
`shared-classpath.txt` next to the log file and adds:

```text
-Dweaver.shared.classpathFile=<absolute path to shared-classpath.txt>
-Dweaver.shared.main=net.minecraft.server.Main
-Dweaver.classspace.shared=true
```

For the client, it reuses `<GameDir>\classpath.txt` and adds
`-Dweaver.shared.main=net.minecraft.client.main.Main` plus the same shared
properties. Both paths pass the game arguments unchanged after
`net.weaver.core.SharedMain`.

`ClassSpace` also accepts an inline `-Dweaver.shared.classpath=<entries>`
instead of the classpath file. Entries are split on semicolons, the platform
path separator, or line breaks. If neither property is set, shared mode fails
with `IllegalStateException`.

### What the agent does

`ClassSpace.create` reads the game classpath, builds URLs from the game
entries, the accepted mod jars, and the `api-game` jar, and constructs
`SharedModClassLoader` with the system loader as parent
(`ClassSpace.java:89-109`). It sets both `gameLoader` and `modLoader` to the
shared loader, so the hook and lifecycle transforms see the loader they expect
and apply.

`SharedModClassLoader` resolves `net.weaver.api.game.*` child-first: it
defines those classes from the `api-game` jar itself, lazily on first use, and
asks the parent only when it cannot find one. This is required because typed
event accessors reference game types; defining them (and their `api-game`
supertypes) in the shared loader keeps those references linkable while base
`net.weaver.api` and `net.weaver.core` classes stay shared from the agent
library through the parent. Because nothing is defined eagerly, no game class
is loaded before the access transformers, Mixin, and the hook transformers are
installed.

`SharedMain` sets the thread context classloader to the shared loader, loads
`-Dweaver.shared.main` with `Class.forName(name, true, loader)`, invokes its
`main(String[])`, and restores the previous context loader in a `finally` block
(`SharedMain.java:42-63`). It exits with code `2` and prints `SHARED MAIN FAILED`
when the class space is missing or `weaver.shared.main` is blank.

### Signals to check

A successful shared boot contains:

```text
[weaver] CLASS SPACE mode=shared game-entries=<n> mods=<n> loader=weaver-shared api-game=<child-first|absent>
[weaver] AGENT READY side=<side> mods=<n> configs=<n> class-space=shared
[weaver] SHARED MAIN class=<main> loader=weaver-shared
```

`api-game=child-first` means the `api-game` jar was located and its classes
are resolved child-first by the shared loader; `api-game=absent` means the jar
was not located. Earlier builds pre-seeded the classes eagerly and logged a
count (`api-game=36` in the runs recorded below). When `api-game` is not
available, `LoaderAgent` logs `TYPED EVENTS unavailable` and the base factory
remains in use.

### Prerequisites and limits

- JDK 26, the agent jar, and `core\build\agent\lib`.
- A complete game classpath. The server path requires the real server jar and
  its libraries; the client path expects `classpath.txt` and passes the
  natives/assets paths to the game.
- `api-game` on the agent lib for typed events. It is optional; without it the
  base event factory is used.
- The game classpath entries must be readable by `ClassSpace`; a missing or
  empty classpath file is an error.
- Shared mode is still one class space. It does not isolate mods from one
  another or from the game; `SharedModClassLoader` uses normal parent-first
  delegation for everything except `net.weaver.api.game`.
- The shared client proof passed with the deterministic fixture world through
  `examples/ops-proof/run-shared-client.ps1`; the dedicated-server shared proof
  and the ops proof also passed.

For internal details, see
[Architecture: class-space modes](architecture.md#class-space-modes).

## Mod operations

### Mod-list CLI

`net.weaver.core.modops.ModListCommand` discovers a mods directory and prints a
report. It does not start the game and does not use instrumentation
(`ModListCommand.java:9-22`).

```powershell
java -cp "core\build\agent\weaver-agent.jar;core\build\agent\lib\*" net.weaver.core.modops.ModListCommand --mods mods --side client --json
```

On Unix, use `:` between classpath entries and `core/build/agent/lib/*`.

| Argument | Default | Meaning |
|---|---|---|
| `--mods <dir>` | `mods` | Directory scanned for `*.jar`. |
| `--side` (`client` or `server`) | `client` | Process side used for the side filter and report. |
| `--json` | off | Emit the JSON report on standard output. Without it, emit line-oriented text. |
| `--policy` (`warn` or `fatal`) | system properties | Set both class-space policies for this run. Overrides `weaver.policy*` properties. |
| `--help`, `-h` | | Print usage and exit `0`. |

Loader log lines go to standard error, so `--json` output on standard output
stays machine-readable. Exit codes:

| Code | Meaning |
|---|---|
| `0` | Report produced. |
| `1` | A fatal policy aborted discovery. |
| `2` | Invalid arguments. |

`ModListCommand` catches `ModPolicyException`, prints each policy diagnostic
and `MOD POLICY FAILED`, and returns `1` (`ModListCommand.java:100-119`).

### Agent mod-list report

The agent can write the same report at boot. The agent option takes precedence
over the system property:

| Setting | Agent option | System property | Default |
|---|---|---|---|
| Report path | `modListFile=...` | `-Dweaver.modListFile=...` | none |
| Format | `modListFormat` (`json` or `text`) | `-Dweaver.modListFormat=...` | `json` |

Example agent argument:

```text
-javaagent:core\build\agent\weaver-agent.jar=side=server;modsDir=mods;modListFile=run\mods.json;modListFormat=json
```

Only the exact value `text` (case-insensitive) selects the text format; every
other value selects JSON (`LoaderAgent.java:182-201`). The report is written
after discovery and before the class space is created. A
`ModReportWriter.write` failure logs `MOD LIST FAILED` and does not fail boot.
Success logs:

```text
[weaver] MOD LIST path=<absolute path> format=json mods=<n> diagnostics=<n>
```

### JSON contract

`ModList.SCHEMA_VERSION` is `1` (`ModList.java:31-32`). `toJson` emits stable,
pretty-printed JSON; map-valued fields are sorted by key
(`ModList.java:130-167`).

| Root key | Type | Meaning |
|---|---|---|
| `schemaVersion` | number | Report schema version, currently `1`. |
| `loader.name` | string | Always `weaver`. |
| `loader.version` | string | Loader build version from `Weaver.VERSION`; currently `2.3.2`. This is not `WeaverApi.VERSION`. |
| `side` | string | Process side the report was produced for. |
| `summary.total` | number | Every discovered candidate, including rejected jars. |
| `summary.loaded` | number | Entries with status `LOADED`. |
| `summary.rejected` | number | Entries with any status other than `LOADED`. |
| `summary.diagnostics` | number | Diagnostic count. |
| `summary.cycles` | number | Number of `CYCLE` diagnostics. |
| `mods` | array | One object per candidate, sorted by jar file name and then id. |
| `diagnostics` | array | Diagnostics in loader emission order. |

Each `mods[]` object carries:

| Field | Type | Meaning |
|---|---|---|
| `id` | string | Mod id from `weaver.mod.json`; for an unreadable jar, the file name without `.jar`. |
| `version` | string | Declared version, or `unknown` for an unreadable jar. |
| `name` | string | Display name. |
| `side` | string | Declared side: `client`, `server`, or `both`. |
| `minecraft` | string | Declared Minecraft version. |
| `mainClass` | string | Declared entry point class. |
| `dependencies` | object | Required dependency id to version-range map. Ranges: `*`, an exact version, `>=`/`>`/`<=`/`<`/`=` (whitespace after the operator is allowed), `~1.2` (`>=1.2 <1.3`), `^1.2` / `^0.2` (pins the first non-zero component), Maven `[1.0,2.0)` style ranges, and comma/space-separated conjunctions. An unparseable range rejects the mod. |
| `optional` | object | Optional dependency id to version-range map. |
| `breaks` | object | Incompatible mod id to version-range map. |
| `mixins` | array | Declared mixin configuration names. |
| `accessTransformers` | array | Declared access-transformer file names. |
| `loadOrder` | number | Zero-based load position, or `-1` when not loaded. |
| `status` | string | Final disposition. |
| `statusDetail` | string | Human-readable rejection detail, or empty when loaded. |
| `file` | string | Jar file name without any directory. |

Each `diagnostics[]` object carries `severity` (`INFO`, `WARN`, or `FATAL`),
`type`, `subject` (mod id, class entry, or file name), and `message`. The
current loader emits `WARN` for non-fatal findings and `FATAL` for policy
violations; `INFO` is declared but not emitted.

A compact skeleton:

```json
{
  "schemaVersion": 1,
  "loader": {"name": "weaver", "version": "2.3.2"},
  "side": "client",
  "summary": {"total": 1, "loaded": 1, "rejected": 0, "diagnostics": 0, "cycles": 0},
  "mods": [
    {
      "id": "example",
      "version": "1.0.0",
      "name": "Example",
      "side": "both",
      "minecraft": "26.3",
      "mainClass": "com.example.ExampleMod",
      "dependencies": {},
      "optional": {},
      "breaks": {},
      "mixins": [],
      "accessTransformers": [],
      "loadOrder": 0,
      "status": "LOADED",
      "statusDetail": "",
      "file": "example.jar"
    }
  ],
  "diagnostics": []
}
```

### Statuses

`ModStatus` values (`ModStatus.java:10-35`):

| Status | Meaning |
|---|---|
| `LOADED` | Passed side, duplicate-id, breaks, and dependency validation. A fatal policy aborts discovery instead of marking an entry. |
| `WRONG_SIDE` | Declared side does not match the process side. |
| `DUPLICATE_ID` | Another jar with the same mod id won: it has a higher version, or the same version and it is top-level while this one is nested, or the same version and it was discovered first. |
| `READ_FAILED` | The jar or its descriptor could not be read. |
| `REJECTED_BREAKS` | A `breaks` entry matched a discovered mod. |
| `REJECTED_DEPENDENCY` | A required dependency is absent or was itself rejected (`reason=dependency-rejected`), or this is a nested jar whose parent was not loaded or was rejected. |
| `REJECTED_VERSION` | A required dependency exists with an incompatible version, or a dependency range cannot be parsed (`invalid-range=`). |
| `REJECTED_POLICY` | Declared, but the current loader aborts on a fatal policy rather than marking an entry. |

### Dependency and cycle diagnostics

`ModDiagnostic.Type` values (`ModDiagnostic.java:45-79`):

| Type | Severity | Effect |
|---|---|---|
| `WRONG_SIDE` | `WARN` | Jar skipped. |
| `DUPLICATE_ID` | `WARN` | Highest version wins (top-level beats nested on a tie, then first jar); the other jar is skipped. The message names the kept jar (`kept=`, `kept-version=`). |
| `READ_FAILED` | `WARN` | Jar skipped; other jars continue. |
| `MISSING_DEPENDENCY` | `WARN` | Declaring mod rejected. |
| `VERSION_MISMATCH` | `WARN` | Declaring mod rejected. |
| `OPTIONAL_VERSION` | `WARN` | Mod still loads; optional target exists but its version does not match. |
| `BREAKS` | `WARN` | Declaring mod rejected. |
| `CYCLE` | `WARN` | Cycle members still load after the acyclic prefix. |
| `DUPLICATE_CLASS` | `WARN` or `FATAL` | WARN: first accepted mod supplies the class and jars still load. FATAL: discovery aborts. |
| `API_SHADOW` | `WARN` or `FATAL` | WARN: jar loads. FATAL: discovery aborts. |
| `POLICY` | not emitted | Declared category; the current loader emits the specific `DUPLICATE_CLASS` or `API_SHADOW` diagnostic instead. |

A cycle produces this exact log line:

```text
[weaver] MOD ORDER cycle=true remaining=<n> action=filename-order
```

`ModLoader.order` then records one `CYCLE` diagnostic per strongly connected
component from `ModDependencyGraph.cycles()` and appends the remaining members
in deterministic order (the implementation sorts them by mod id).

### Policies

`ModPolicies` reads the two class-space safeguards from system properties
(`ModPolicies.java:21-57`):

| Property | Default | Scope |
|---|---|---|
| `weaver.policy` | `warn` | Base mode for both policies. |
| `weaver.policy.apiShadow` | inherits `weaver.policy` | Mod jars that physically contain classes under `net/weaver/api/`. |
| `weaver.policy.duplicateClasses` | inherits `weaver.policy` | Class entries shipped by more than one accepted mod. |

Values are case-insensitive `warn` or `fatal`. An unrecognized non-blank value
resolves to `warn`; it does not fall back to the base value
(`ModPolicies.java:95-104`).

**API shadow.** A mod jar is an API shadow when it physically contains a class
file under `net/weaver/api/`, including `net/weaver/api/game/`
(`ModLoader.java:212-229`). Compiling a mod against `api` or `api-game` is not
a shadow; shipping the loader API classes inside the mod jar is. This matters
because a second copy of `EventBus` or registry classes can create a second
`EventBus.GAME` identity.

**Duplicate classes.** Two accepted mods shipping the same class entry produce
`MOD CLASS CONFLICT class=<entry> mods=<a,b> first=<a>` and a
`DUPLICATE_CLASS` diagnostic (`ModLoader.java:181-210`). The class space is
first-wins: the duplicate that appears first on the load-ordered classpath
supplies the class, and the JVM ignores later copies. Under `warn`, the jars
still load.

**FATAL behavior.** For either policy, `fatal` changes the log suffix to
`action=fatal` and the diagnostic severity to `FATAL`. After collecting all
failures, `ModLoader` closes the accepted jars and throws
`ModPolicyException` (`ModLoader.java:231-240`). The standalone CLI prints the
diagnostics and exits `1`. The agent prints `MOD POLICY FATAL` plus each
diagnostic and rethrows, so agent startup aborts (JVM fail-fast)
(`LoaderAgent.java:151-159`).

Policies are evaluated on accepted mods after side, duplicate-id, read, breaks,
and dependency validation (`ModLoader.java:137-138`). A mod rejected for a
missing dependency is not part of the duplicate-class or API-shadow scan.

Set the mode for the game JVM, for example:

```text
-Dweaver.policy=fatal
-Dweaver.policy.apiShadow=fatal
-Dweaver.policy.duplicateClasses=warn
```

The launcher does not expose a dedicated policy parameter; the properties are
read by `ModPolicies.fromSystemProperties()` from the JVM that hosts the agent.
For a report-only run, the CLI `--policy warn|fatal` sets both policies and
overrides the system properties.

## Update checks

`net.weaver.core.modops.update` defines a contract only. There is no network
implementation, no HTTP client, no server, and no marketplace. The package
Javadoc states this explicitly, and `UpdateChecks` opens no sockets.

### Contract

`UpdateCheck` (`UpdateCheck.java:19-29`) has one method:

```java
UpdateCheckResult check(UpdateCheckRequest request);
```

Implementations must be non-blocking, or explicitly document in their own class
that `check` may block and on which thread it must run. They must not mutate mod
state, the class path, the mod list, or files under the mods directory. They
should return a non-null result and should map ordinary network or service
failures to `UpdateStatus.ERROR` rather than throwing.

`UpdateChecks` is the process-wide holder (`UpdateChecks.java:48-104`):

- the default is a no-op returning `NOT_CONFIGURED` with the message
  `no update check installed`;
- `install(check)` replaces it; `install(null)` and `reset()` restore the
  no-op;
- `check(request)` returns the installed check's result, converting a `null`
  result or a thrown `RuntimeException` into `ERROR`;
- a `null` request throws `NullPointerException`.

`UpdateChecks` is not called by `LoaderAgent`; a launcher or UI layer must
install and invoke it explicitly.

### Status and records

`UpdateStatus` values: `UP_TO_DATE`, `UPDATE_AVAILABLE`, `UNKNOWN`,
`NOT_CONFIGURED`, `ERROR`. `NOT_CONFIGURED` is the default and is not an error.

| Record | Fields |
|---|---|
| `InstalledMod` | `id`, `version` |
| `UpdateCheckRequest` | `loaderApiVersion`, `gameVersion`, `side`, `installed` |
| `ModUpdate` | `id`, `installedVersion`, `availableVersion`, `downloadUrl`, `notes` |
| `UpdateCheckResult` | `status`, `updates`, `message` |

All record components are non-null; the request copies `installed` and the
result copies `updates` into immutable lists. `downloadUrl` and `notes` use an
empty string when unknown, not `null`.

### Non-normative endpoint scaffold

The `UpdateChecks` class comment defines field names for a future HTTP-backed
implementation so implementations agree. This is a scaffold, not a running
service.

Request body:

```json
{
  "schemaVersion": 1,
  "loaderApiVersion": "2.1.0",
  "gameVersion": "26.3",
  "side": "client",
  "installed": [
    {"id": "example", "version": "1.0.0"}
  ]
}
```

Response body:

```json
{
  "schemaVersion": 1,
  "status": "UPDATE_AVAILABLE",
  "message": "",
  "updates": [
    {
      "id": "example",
      "installedVersion": "1.0.0",
      "availableVersion": "1.1.0",
      "downloadUrl": "https://example.invalid/example-1.1.0.jar",
      "notes": "release notes"
    }
  ]
}
```

`status` is one of the `UpdateStatus` names. Unknown response statuses should
map to `UNKNOWN`; transport or parsing failures should map to `ERROR`. The
field names follow `UpdateChecks`; the values are illustrative for the current
build (`WeaverApi.VERSION` is `2.1.0`, game `26.3`). No server, endpoint, or
marketplace is implemented by this repository.

## Versioning and compatibility

### Public API constants

`WeaverApi` (`api/src/main/java/net/weaver/api/WeaverApi.java`):

| Constant | Value | Meaning |
|---|---|---|
| `VERSION` | `2.1.0` | Binary API version. Intentionally unchanged for the additive 2.2 feature level. |
| `API_MAJOR` | `2` | Major version of the binary API contract. |
| `API_MINOR` | `1` | Minor version of the binary API contract. |
| `MIN_SUPPORTED_API` | `2.1.0` | Oldest accepted API version within major version 2. |
| `FEATURE_LEVEL` | `2.2` | Platform feature level. Gate new 2.2 capabilities on this. |
| `GAME_VERSION` | `26.3` | Targeted Minecraft version. |
| `HOOK_TARGET` | `26.3` | Hook-spec target. |
| `PATCH_LEVEL` | `1` | Game patch level expected by this build; `0` reports an unpatched artifact. |

`isCompatibleApi` accepts the same major version at or above
`MIN_SUPPORTED_API`; `isCompatibleGame` accepts `26.3` and numeric equivalents
such as `26.3.0`. `requireCompatibleApi` and `requireCompatibleGame` throw
`IllegalStateException` on a mismatch. `isGameApiCompatible(loader)` reads the
public static `VERSION` field of `net.weaver.api.game.WeaverGameApi` and checks
it with `isCompatibleGame`; a missing class, invalid field, or unsupported
value returns `false` (`WeaverApi.java:187-202`).

`stabilityPolicy()` states the additive-first policy: new types, overloads,
constants, and event subclasses ship in minor releases; removal or a
signature/descriptor change requires a major release; deprecated members remain
for at least one minor release; base event accessors stay `Object`-typed so
typed `api-game` events remain binary compatible.

### Compatibility matrix

`CompatibilityMatrix` (`core/src/main/java/net/weaver/core/version/CompatibilityMatrix.java`)
declares:

| API row | Game | Hook target | Level | Note |
|---|---|---|---|---|
| `2.1` | `26.3` | `26.3` | `SUPPORTED` | Accepted baseline: Weaver 2.1 mods on Minecraft 26.3 with the 26.3 hook set. |
| `2.2` | `26.3` | `26.3` | `SUPPORTED` | Additive-only feature level over 2.1. |

API rows match by `major.minor`; game and hook-target values match by exact id
or numeric version equality. When several rows match, the highest API version
wins; unknown combinations are `UNSUPPORTED`. `supports` accepts both
`SUPPORTED` and `EXPERIMENTAL`; `UNSUPPORTED` is rejected. The running build
reports the `2.1` row because `VERSION` stays `2.1.0`, while `FEATURE_LEVEL` is
`2.2`.

`net.weaver.core.version` also provides `SemVer` (tolerant `major.minor.patch`
parser; optional `v`, suffixes stripped, one to three components),
`GameVersion` (`CURRENT` is `26.3`/`26.3`), and `PlatformTarget`
(`current()` and `supported()`).

### Boot log

`VersionChecks.check(loader, gameVersion)` never throws and returns a
`Result(compatible, apiLevel, gameLevel, message)`. `LoaderAgent` logs the
message at boot:

```text
weaver version check: api=2.1.0 (SUPPORTED) game=26.3 (SUPPORTED) typedGameApi=26.3 (compatible) compatible=true
```

The typed probe is optional. When `WeaverGameApi` is absent, the message says
`typedGameApi=absent (optional)` and the base check is unaffected. When it is
present but incompatible, `compatible=false`. The check is diagnostic only;
`LoaderAgent` does not abort the boot on `compatible=false`.

Patch detection runs immediately before the version check and logs one of:

```text
[weaver] GAME PATCH level=<n> artifact=patched
[weaver] GAME PATCH level=0 artifact=unpatched action=runtime-only
[weaver] GAME PATCH probe=unavailable reason=<reason>
```

A patched line appends `patch-set=<id>` when
`META-INF/weaver/patch.json` is readable. The probe is diagnostic only and
never aborts the boot.

### Migration: 2.1 to 2.2

The 2.2 feature level is additive only over 2.1:

- no public type, method, constructor, or field is removed or changes its
  descriptor;
- event dispatch and event behavior are unchanged;
- mods compiled against 2.1 keep linking without a recompile;
- `WeaverApi.VERSION` intentionally stays `2.1.0`; `FEATURE_LEVEL` is `2.2`.
  Consumers should gate new 2.2 capabilities on `FEATURE_LEVEL`, not on
  `VERSION`;
- `GAME_VERSION` and `HOOK_TARGET` remain `26.3`;
- the loader build version (`Weaver.VERSION`, used in the mod-list
  `loader.version`) is separate from the API version. It is currently
  `2.3.2`.

Only Minecraft 26.3 is implemented. Adding a second game version is not a new
matrix row: it requires new `HookSpec` sets, Mixin refmaps/remapping for the new
runtime, an `api-game` recompilation against the new game, and new proofs
(`CompatibilityMatrix` and `VersionChecks` class comments).

## Verification

### Ops proof

The self-contained ops proof builds synthetic mod jars, runs real discovery and
the real mod-list CLI in child processes, and checks the version and update
contracts (`examples/ops-proof/README.md`):

```powershell
powershell -ExecutionPolicy Bypass -File examples\ops-proof\run.ps1
```

It prints `OPS PROOF CHECK ok=true <name>` per check and ends with
`OPS PROOF RESULT ok=true checks=<n>`. A failure exits `1`. The default run
proves mode selection and the report, version, and update contracts without
launching Minecraft; `ClassSpace.create` in shared mode is exercised by the
optional shared server proof.

The optional shared check:

```powershell
powershell -ExecutionPolicy Bypass -File examples\ops-proof\run-shared.ps1
```

This is a thin wrapper for `run.ps1 -Shared`, which runs
`examples/proof/run-server-shared.ps1` and requires exit code `0` plus
`SHARED MODE RESULT ok=true`. It requires the server/game directories and the
built `examples/proof/out/proof.jar`.

`tools/verify.ps1` verifies the build, agent layout, Gradle plugin build, and
`git diff --check`; it does not currently invoke `examples/ops-proof`.

### Verification status (2.2 workstream)

**Date: 2026-11-04.**

The standard system-mode dedicated-server proof passed after the final clean
build:

```text
LAUNCH side=server shared=false ...
SERVER RESULT ok=true
PROOF RESULT ok=true side=server checks=20
```

The opt-in shared-mode dedicated-server proof also passed:

```text
[weaver] CLASS SPACE mode=shared game-entries=40 mods=1 loader=weaver-shared api-game=36
[weaver] AGENT READY ... class-space=shared
PROOF RESULT ok=true side=server checks=20
SHARED MODE RESULT ok=true side=server
SHARED FACTORY PROBE ok=true
```

The shared client proof passed with the deterministic fixture world:

```text
[weaver] CLASS SPACE mode=shared game-entries=65 mods=1 loader=weaver-shared api-game=36
[weaver] SHARED MAIN class=net.minecraft.client.main.Main loader=weaver-shared
PROOF RESULT ok=true side=client checks=20
OPS SHARED CLIENT RESULT ok=true
```

`examples/ops-proof/run.ps1` passed all ten checks,
`examples/ops-proof/run-shared.ps1` passed the ops checks plus the shared server
proof, and `examples/ops-proof/run-shared-client.ps1` passed the shared client
proof. The standalone `examples/proof/run-server.ps1` and
`examples/proof/run-client.ps1` system-mode proofs also passed.

An earlier standard-mode run during concurrent hook workstream edits failed
with `LinkageError: loader 'app' attempted duplicate class definition for
net.minecraft.server.level.ServerPlayer`. The failure did not reproduce after
the final clean build; it is recorded here only so the transient is not
mistaken for a platform-ops design limit. The standard proof was rerun and is
green.

## Limitations and open items

- **Shared mode is opt-in.** The launcher still prints an experimental warning.
  `SYSTEM` remains the supported production mode and the 2.1 baseline.
- **No classloader isolation.** One shared class space; mods see the game and
  one another.
- **Class scans skip nested and multi-release entries.** Duplicate-class and
  API-shadow detection use the regular `.class` entries exposed by
  `ModJar.classEntries`; nested jars and `META-INF/versions/**` entries are not
  audited, matching the 2.1 conflict scan.
- **No hot reload of mods for players.** Mods load once at boot; adding or
  replacing a mod needs a restart. Developers get method-body hot swap from a
  project's class output ([toolchain](toolchain.md#developer-hot-swap)).
- **Only Minecraft 26.3.** No second game version or hook set is implemented.
- **No network update service or marketplace.** `UpdateChecks` is a contract
  and a no-op holder only.
- **Version checks are advisory.** `VersionChecks` logs `compatible=false` but
  does not abort.
- **Agent failures are fatal by default.** `ModPolicyException` is rethrown by
  the agent; any other agent failure is logged as `AGENT FAILED` (also to the
  markers file) and rethrown, aborting the JVM.
  `-Dweaver.agent.failurePolicy=warn` logs it and continues without Weaver.

## Related

- [Documentation index](README.md)
- [Architecture](architecture.md)
- [Hook points](hooks.md)
- [Typed game API](api-game.md)
- [Toolchain](toolchain.md)
- [Migration to 2.1](migration-2.1.md)
- [Ops proof](../examples/ops-proof/README.md)
- [Real 26.3 proof](../examples/proof/README.md)
