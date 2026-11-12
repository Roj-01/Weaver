# Weaver config standalone proof

This proof exercises the Weaver 2.2 configuration API (`ConfigBuilder`,
`ConfigSpec`, `ConfigValue`, `ConfigManager`, migrations, validation, problems,
hot reload) together with the core file store (`ConfigFiles`,
`JsonConfigStore`, `WeaverConfig`) and the game-free client-server sync layer
(`ConfigSync`, `ConfigSyncDocument`, `ConfigSyncPolicy`).

It uses no game classes, no launcher, and no Minecraft jar. The only
dependencies are the Weaver API, the Weaver core config classes, Gson, and the
JDK. It runs as a plain `java` process.

## Build

```powershell
.\build.ps1
```

The script resolves JDK 26 from `JAVA_HOME` or
`C:\Program Files\Java\jdk-26.0.1`, requires `core\build\agent\lib`, compiles
everything with `javac --release 26 -encoding UTF-8`, and prints:

```text
CONFIG PROOF BUILD ok=true classes=...\out\classes
```

If the agent lib is missing run:

```powershell
.\gradlew.bat :core:agentJar
```

Note: this repository's `core\build\agent\lib` contains the API, Gson, and the
other runtime dependencies, while the core config classes live in
`core\build\libs\core-*.jar` (or `core\build\agent\weaver-agent.jar`). The build
script adds that jar to the classpath explicitly.

## Run

```powershell
.\run.ps1
```

`run.ps1` builds first, then runs:

```text
java -cp "out\classes;<agent lib>\*;<core jar>" net.weaver.configproof.ConfigProof
```

The proof deletes and recreates `out\work`, so every run starts from a clean
directory.

## What it proves

1. A v1 `client.json` is seeded with a legacy key (`general.message`), an int,
   an enum name, and a string list.
2. Opening a v2 spec through `WeaverConfig.open` applies one `ConfigMigration`
   that renames `general.message` to `general.greeting`.
3. Exactly one `ConfigLoadedEvent` is posted on the mod's event bus, with the
   right mod id, side, schema version, logical side, and no problems.
4. An out-of-range `ConfigValue.set` raises `ConfigValidationException`, leaves
   the previous value untouched, and a subsequent valid set succeeds.
5. The config file is edited on disk and `ConfigManager.reload()` is called
   explicitly. The new values are visible, the generation advances from 1 to 2,
   and exactly one `ConfigReloadedEvent` reports the same result.
6. `ConfigManager.save` writes schema version 2 with the migrated and the new
   values. The file keeps its enum and list values.
7. After `ConfigManager.unregister`, a fresh spec re-opens the same file and
   observes the saved int, string, enum, and list values.
8. A second mod with garbage JSON is backed up, reports `corrupt=true`, keeps
   its problems, and falls back to defaults without crashing.
9. The client store resolves `client.json`; no `server.json` is created.
10. `ConfigManager.get(modId)` returns the same manager instance that was opened.
11. A `SERVER_TO_CLIENT` server manager sends its document with
    `ConfigSync.sendTo`, a fake connection carries the encoded payload, and a
    client manager applies it through `ConfigSync.applyTo`. The sync value is
    visible, exactly one `ConfigSyncedEvent` has logical side `CLIENT`, and no
    `client.json` is written.

## Expected output

```text
[config-proof] [weaver] CONFIG LOADED mod=config-proof ...
CONFIG PROOF EVENT count=1 mod=config-proof
CONFIG PROOF MIGRATED from=1 to=2 value=hello-from-v1
CONFIG PROOF LOADED schema=2 applied=4 defaults=0
[config-proof] [weaver] CONFIG RELOADED mod=config-proof ...
CONFIG PROOF RELOADED generation=2 value=hello-from-reload
CONFIG PROOF INVALID key=general.count message=config 'config-proof' key 'general.count': ...
CONFIG PROOF SAVED file=...\out\work\config-proof\client.json
CONFIG PROOF ROUNDTRIP value=4
[config-proof] [weaver] CONFIG CORRUPT mod=config-proof-corrupt ...
[config-proof] [weaver] CONFIG BACKUP mod=config-proof-corrupt ...
CONFIG PROOF CORRUPT backup=...\client.json.corrupt-....bak defaults=1
CONFIG PROOF SIDE file=client.json server-file-absent=true
CONFIG PROOF MANAGER identity=ok mod=config-proof
[config-proof] [weaver] CONFIG SYNC APPLIED mod=config-proof-sync side=CLIENT ...
CONFIG PROOF SYNC ok=true mod=config-proof-sync applied=2 logicalSide=CLIENT fileWritten=false
CONFIG PROOF RESULT ok=true checks=58
```

The last line is exactly `CONFIG PROOF RESULT ok=true checks=58`. On any failed
assertion the process prints `CONFIG PROOF RESULT ok=false reason=...` and exits
with code 1.
