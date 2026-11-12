# Weaver capability standalone proof

This proof exercises the Weaver 2.2 capabilities pipeline end to end:

- `net.weaver.api.capability` (`AttachmentRegistry`, `AttachmentType`,
  `CapabilitySync`, `RuntimeAttachmentStore`)
- `net.weaver.api.data` (`DataCodecs`)
- `net.weaver.core.data` (`ReflectiveItemDataAccess`, `ItemDataLimits`)
- `net.weaver.core.capability` (`CapabilityStores`, `ItemStackAttachmentStore`,
  `EntityAttachmentStore`, `BlockEntityAttachmentStore`, `CapabilityPersistence`)

It is a plain `java` main class. It has no mod jar, no Weaver agent, and no
JUnit dependency. It does use the real Minecraft game jar, because the proof
deliberately tests real `ItemStack` instances, the real `minecraft:custom_data`
data component, and a real NBT encode/decode cycle.

Main class: `net.weaver.capabilityproof.CapabilityProof`.

## Requirements

- JDK 26 from `JAVA_HOME` or `C:\Program Files\Java\jdk-26.0.1`.
- A prepared game directory with `classpath.txt`, by default
  `work\game-26.3` (override with `WEAVER_GAME_DIR`).
- The newest `api-*.jar` under `api\build\libs` or
  `core\build\agent\lib`.
- The newest `core-*.jar` under `core\build\libs`, or
  `core\build\agent\weaver-agent.jar`, plus the jars in
  `core\build\agent\lib`.

If the API or core artifacts are missing:

```powershell
.\gradlew.bat :api:jar
.\gradlew.bat :core:jar
.\gradlew.bat :core:agentJar
```

## Build

```powershell
cd examples\capability-proof
.\build.ps1
```

`build.ps1` resolves the JDK, requires the game `classpath.txt`, picks the
newest API and core jars, compiles every source with
`javac --release 26 -encoding UTF-8` into `out\classes`, and writes
`out\run-classpath.txt`. It prints:

```text
CAPABILITY PROOF BUILD ok=true classes=...\out\classes
```

If the selected core jar does not contain
`net/weaver/core/capability/CapabilityStores.class` (for example while a
parallel core build is broken), the script prints a note and compiles only
`core\src\main\java\net\weaver\core\data` and
`core\src\main\java\net\weaver\core\capability` into `out\core-classes`. The
normal build line is unchanged. Once the core jar or agent jar is fresh, the
fallback is skipped and the packaged classes are used.

## Run

```powershell
.\run.ps1
```

`run.ps1` runs `build.ps1`, then:

```text
java -cp "<out\classes>;<api jar>;<core jar/agent jar>;<agent lib jars>;<game classpath>" net.weaver.capabilityproof.CapabilityProof
```

It propagates the proof's exit code. If PowerShell blocks local scripts, run
`powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1`.

## Expected output

The successful run prints one check line per check and this final line:

```text
CAPABILITY PROOF CHECK ok=true item_set_get
CAPABILITY PROOF CHECK ok=true item_missing_default
CAPABILITY PROOF CHECK ok=true item_copy_semantics
CAPABILITY PROOF CHECK ok=true item_save_load
CAPABILITY PROOF CHECK ok=true block_entity_save_load
CAPABILITY PROOF CHECK ok=true item_remove_returns_previous_and_clears
CAPABILITY PROOF CHECK ok=true runtime_attach_get_remove
CAPABILITY PROOF CHECK ok=true isolated_registry_side_behavior
CAPABILITY PROOF CHECK ok=true invalid_payload_falls_back
CAPABILITY PROOF CHECK ok=true oversize_rejected
CAPABILITY PROOF CHECK ok=true concurrent_runtime_access
CAPABILITY PROOF CHECK ok=true sync_declaration
CAPABILITY PROOF RESULT ok=true checks=12
```

Minecraft's bootstrap installs a log4j console appender, so in a real run each
`System.out.println` marker is prefixed by the game logger, for example
`[10:23:11] [main/INFO]: [STDOUT]: CAPABILITY PROOF CHECK ok=true item_set_get`.
The marker text after that prefix is exact.

On any failed assertion the process prints
`CAPABILITY PROOF RESULT ok=false reason=...` and exits with code 1.

## What each check proves

1. `item_set_get` - persistent string and varint values are stored on a real
   `ItemStack` through the core item store and read back; defaults apply first.
2. `item_missing_default` - a stack without a value returns the declared
   default from `get`, while `find` stays empty and `has` is false.
3. `item_copy_semantics` - `ItemStack.copy()` carries the component-based
   values; removing a value on the copy leaves the original untouched.
4. `item_save_load` - `ItemStack.CODEC` + `NbtOps` encode and decode a stack
   with `CUSTOM_DATA` present on both sides, and both values survive.
5. `block_entity_save_load` - a real `ChestBlockEntity` value is written through
   `CapabilityPersistence.saveBlockEntity` into a real `TagValueOutput`, the
   `weaver:capabilities` root is present, and a new `ChestBlockEntity` restores
   the value through a real `TagValueInput`.
6. `item_remove_returns_previous_and_clears` - `remove` returns the previous
   explicit value, then `get`/`find`/`has` report absence.
7. `runtime_attach_get_remove` - a runtime-only capability works on a plain
   Java holder; equal-but-not-same holders are distinct by identity.
8. `isolated_registry_side_behavior` - a new isolated `AttachmentRegistry`
   does not see a runtime value stored on the global registry.
9. `invalid_payload_falls_back` - corrupt bytes written through
   `ReflectiveItemDataAccess` make `get` return the capability default,
   `find` return empty, and the registry record a diagnostic.
10. `oversize_rejected` - a value encoded beyond the capability's declared
   byte bound is rejected with `CapabilityException` and is not stored.
11. `concurrent_runtime_access` - eight threads set and read distinct runtime
    holders 100 times each; every holder reads back its own value.
12. `sync_declaration` - a persistent type declares `CapabilitySync.ITEM_STACK`
    and `persistent() == true`; a runtime type declares `CapabilitySync.NONE`
    and `persistent() == false`.

## Real vs simulated

Real in this proof:

- Minecraft bootstrap and registries (`SharedConstants.tryDetectVersion`,
  `Bootstrap.bootStrap`), including the STONE item component binding.
- Real `ItemStack` construction, copying, count handling, and
  `minecraft:custom_data`.
- Real `ItemStack.CODEC` and `NbtOps` serialization.
- Real reflective bridging through `ReflectiveItemDataAccess` and the real
  `ItemStackAttachmentStore` installed by `CapabilityStores.install`.
- Real `RuntimeAttachmentStore` synchronization under eight concurrent
  threads.
- Real `DataCodecs` encoders/decoders and registry diagnostics.

Simulated or not covered:

- This is not a mod: no mod jar, loader, agent classes, lifecycle hooks,
  game tick, server, or world. Bootstrap is standalone.
- No network is involved. `CapabilitySync.ITEM_STACK` is only a declaration
  that the value lives in the game's own stack component; the proof checks the
  declaration, not an actual sync channel. `CapabilitySync.RUNTIME` and
  `CapabilitySyncChannel` are covered by unit tests, not by this proof.
- Entity, level, and chunk hooks are not exercised: the proof has no running
  server. Block entity persistence is exercised directly through the store and
  the real value carriers, not through the injected game hook.
- The corrupt payload is injected directly through the same core bridge a
  corrupt save would use. It is not read from an external corrupted file.
- The isolated registry check is a second in-process registry, not a second
  physical game side.
- Item copy behavior is the game's own component copy behavior, not a Weaver
  copy implementation.
