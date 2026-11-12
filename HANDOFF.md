# Handoff: continuing Weaver work

> ملخص بالعربية: هذا المستند لمن سيكمل العمل على ويفر (مثلًا Claude Cowork).
> الوضع الحالي، كيف تبني وتختبر، القواعد التي يجب اتباعها، وقائمة العمل المتبقي
> بالترتيب. اقرأه كاملًا قبل أي تعديل.

Written 2026-11-06 at the end of the session that produced the commits below.
Everything here is checked against the tree at commit `ac4cff4`.

## 1. What Weaver is

Weaver is a standalone Minecraft 26.3 mod loader (Java agent, JDK 26,
LGPL-2.1-only). The goal of this work: harden it, and give mod authors the event
coverage NeoForge provides, using NeoForge 26.3.0.101 only as a reference for
*where* vanilla needs hooks. Weaver keeps its own API; it is not NeoForge
compatible. NeoForge is LGPL-2.1: read it for call-site locations, never copy
its code.

## 2. Repository state

Branch to continue on: **`feature/game-events-batch1`** (it contains the
hardening commit too). `main` is the untouched original. Nothing is pushed:
there is no git remote yet.

| Commit | Content |
|---|---|
| `69d93ce` | Hardening: 33 audit defects fixed (boot, events/registries, network identity, persistence, sync), NOTES.md issues, self-sufficient tooling (foojay JDK, `tools/jdk.ps1`, classpath rebasing, CI workflow) |
| `6e1be55` | Game events batch 1 (22 events) and the `weaver.events.mixins.json` system |
| `b485684` | Batch 2: blocks and world (16 events) |
| `ac4cff4` | Batch 3: entities and combat (24 events) |

Earlier commits were authored as "Roj"; these four use a per-commit identity
(`git -c user.name=... -c user.email=...`). No git identity is configured on the
original machine.

## 3. Environment and setup

- **JDK 26.** Gradle provisions it automatically (foojay resolver in
  `settings.gradle.kts`). Scripts find one through `tools/jdk.ps1`, which also
  scans `%USERPROFILE%\.jdks` and `%USERPROFILE%\.gradle\jdks`.
- **Gradle wrapper** in the repo (9.7.1). Do not use a system Gradle.
- **Minecraft 26.3 files are not in this archive** (Mojang files must not be
  redistributed). `api-game` and every proof need them in
  `work/game-26.3` (client, libraries, assets, `classpath.txt`) and
  `work/server-26.3`. Get them with `tools/fetch-game` (see its README) or copy
  `work/` from the original `Weaver-v2.rar`. Stale absolute paths in
  `classpath.txt` are rebased automatically, so the folder can live anywhere.
- **Windows.** Proof and gate scripts are Windows PowerShell 5.1. Gradle builds
  and unit tests run anywhere (`.github/workflows/ci.yml` runs them on Linux
  and Windows without game files).

## 4. How to verify

```powershell
.\gradlew.bat :api:test :core:test :api-game:test :sync:test   # 955 unit tests
powershell -ExecutionPolicy Bypass -File examples\proof\build.ps1
powershell -ExecutionPolicy Bypass -File examples\proof\run-server.ps1   # dedicated server proof
powershell -ExecutionPolicy Bypass -File tools\verify-ci.ps1             # full 19-step release gate
```

`run-server.ps1` must print `PROOF RESULT ok=true side=server checks=20` and
`GAME EVENTS PROOF RESULT ok=true checks=185`. The gate step `H-proof-server`
requires both. Rebuild the agent after changing `api-game`:
`.\gradlew.bat :api-game:jar :core:agentJar`.

**Known environment problem:** on the original machine the client proof
(`H-client-proof`) crashed natively (`java.exe` 0xc0000005 on the title screen,
no Java exception, no hs_err file) in about half of the launches late in the
session, with or without the new events (5/8 with batch 3, 4/8 without, 2/6
with `-XX:TieredStopAtLevel=1`). Treat it as a graphics-driver or machine
state issue: reboot, update the GPU driver, rerun. If it persists on a clean
machine, investigate before blaming it on the environment.

After a Windows and GPU driver update (RTX 5070 Ti, driver 621.14) the rate
dropped, and an A/B on 2026-11-06 showed it is not Weaver's: vanilla without
the agent crashed 1/8 launches, Weaver to the title screen 0/8, and Weaver
launched exactly like the client proof 1/8 (the other 7 passed the proof).
The Windows event log records each crash as an access violation in an
unknown module (runtime-generated code) with no JVM error report, which
points at a driver thread. The gate's single client retry still fails
sometimes; rerun the gate.

The window state matters more than Weaver: with visible windows, vanilla
crashed 3/6 and Weaver 5/6 (too few runs to call a difference); minimized,
vanilla 1/8 and Weaver 0/8 (title) and 1/8 (full client proof, which passed
in the other 7). So `tools/launcher/launch.ps1` now starts every
non-interactive client minimized (`-Visible` or `WEAVER_CLIENT_VISIBLE=1` shows
it), which covers the client proof in the gate.

## 5. The game-event system (read before adding events)

New events are typed, live in `api-game`, and are fired by Mixin, not by the
ASM `HookSpec` system in `core/hook` (which only injects at method entry/exit
and cannot change arguments).

- Event classes: `api-game/src/main/java/net/weaver/api/game/event/`
  `living/`, `player/`, `world/`, `block/`. Never put them in the top-level
  `net.weaver.api.game.event` package: `TypedEventCatalogCompatibilityTest`
  requires every class there to be the typed twin of a base event in `api`.
- Mixins: `.../event/mixin/` (only mixin classes may live there), registered in
  `api-game/src/main/resources/weaver.events.mixins.json` (keep the list
  sorted; `defaultRequire` is 1, so a target that does not match crashes at
  class load, which is what you want).
- Posting: `support/GameEvents.post(event, level)` sets the logical side and
  isolates failures. On hot paths (ticks, neighbor updates, drops, fluids,
  invulnerability checks) call `GameEvents.hasListeners(Type.class)` first and
  build nothing when nobody listens.
- The loader arms the config on both sides right after the Mixin engine boots
  (`LoaderAgent.installGameEventMixins`, log line `[weaver] GAME EVENTS armed`).

Rules learned the hard way:

1. **Get exact vanilla signatures with javap** against
   `work/game-26.3/client.jar` (26.3 is unobfuscated). NeoForge renames or
   changes some vanilla methods (for example `applyBonemeal` is vanilla
   `growCrop`), so never copy a signature from NeoForge's patched source.
2. **Argument-changing events** cancel the original call and re-invoke the
   method with new values, guarded by a per-thread marker
   (`weaver$reinvoking`). See `LivingEntityEventsMixin#weaver$heal`.
3. **Overrides that skip `super`:** hook the call site instead of the callee
   (lightning is hooked in `LightningBolt#tick` because pigs and villagers
   override `thunderHit`). `Player#actuallyHurt` overrides
   `LivingEntity#actuallyHurt`, so damage hooks exist in both mixins.
4. **No `switch` on an enum in mixin code.** javac emits a synthetic
   `$SwitchMap` class that Mixin cannot carry into the target
   (`NoClassDefFoundError: ...$Anonymous$...`). Use `if`/`else`.
5. **MixinExtras 0.5.5:** `WrapWithCondition` is
   `com.llamalad7.mixinextras.injector.v2.WrapWithCondition`. Weaver's engine
   supports `@Inject`, `@ModifyArg`, `@ModifyVariable`, `@Redirect`,
   `@WrapOperation`, `@WrapWithCondition`, `@ModifyExpressionValue`,
   `@ModifyReceiver`, `@Local`, `@Share`; not `@WrapMethod`.
6. **State between two injectors** of one method: use a `@Unique` field for
   per-object state, a `ThreadLocal` for static or shared objects (blocks and
   items are singletons). Shared cross-mixin state goes in `support/`
   (example: `DropCapture`).

For every new event: write the class with Javadoc saying where it fires and
what cancel and setters do; add the mixin; add a row to the table in
`docs/hooks.md` ("Game events"); update its row and the summary in
`docs/hook-inventory.md`; add an in-game check (below).

### In-game proof

`examples/proof/src/main/java/net/weaver/proof/GameEventsProof.java` (batch 1,
plus the "mixin applied" check for every target class), `GameEventsBlockProof`
(batch 2), and `GameEventsCombatProof` (batch 3) run once on the dedicated
server's first tick. Rules:

- Add every new mixin target class to `GameEventsProof.TARGETS`.
- Call `proof.clearListeners()` between checks.
- Work high above spawn and restore every touched block to air.
- **Only entity-visible chunks** see spawned entities in
  `getEntitiesOfClass`/`getEntity`. `GameEventsCombatProof.checkAreaVisible`
  guards this; without it a "nothing dropped" check can pass vacuously.
- Spawned zombies can be babies; force adults (`setBaby(false)`) when size
  matters.
- Events that need a connected player (joining, tracking, chat, packet
  handlers) are covered only by the mixin-applied check. A detached
  `ServerPlayer` (see `GameEventsPlayerProof`) works for code that sends no
  packets.

## 6. Remaining work, in order

Coverage now: 256 of the 267 (plus 11 partial and 0 deferred; every NeoForge event is now either hooked or has a recorded reason) events NeoForge fires from vanilla code. The full
table with call sites is `docs/hook-inventory.md`.

1. **Batch 3b: done** (6 events: equipment change, armor damage, breathing,
   drowning, wither block breaking, spawn finalization). Left over: the
   mob-griefing pass (done: `MobGriefingEvent` covers all 38 vanilla reads of
   the rule), and hand swapping moved to
   batch 4. `SpreadPlayersCommand` teleports are deferred as niche.
2. **Batch 4a: done** (17 events: break speed, harvest check, display and
   tab-list names, flyable fall, set spawn, can sleep, continue sleeping,
   phantoms, stat award, op/deop, start/stop tracking, player load/save, item
   expiry, pickup post). The proof drives them with a detached `ServerPlayer`
   (`GameEventsPlayerProof`, no connection: avoid any code path that sends a
   packet). **Batch 4b: done** (12 events: anvil pre/post,
   smithing, grindstone result/take, brewed potion, cursor stacking, trades,
   item use on blocks, hotbar pre/post, hand swap; menu checks in
   `GameEventsMenuProof`).
3. **Batch 5: done** (17 NeoForge events in 15 classes: chunk data
   load/save, chunk tickets, `ChunkWatchEvent` WATCH/SENT/UNWATCH, custom click
   actions, enchanting levels, game rules, initial spawn, potential spawns,
   custom spawners, datapack sync, sleep finished, tags updated, vibration game
   events, village sieges). World checks are in `GameEventsWorldProof`.
4. **Batch 6a: done** (32 client events in `event.client`, mixins in the
   config's `client` list; see `docs/hooks.md`). The client proof requires
   `CLIENT PROOF GAME EVENTS ok=true`. 3 more matched existing registries,
   the 9 level render stages are partial via `LevelRenderRegistry`.
   **Batch 6b: done** (17 more events; 2 matched existing registries; 7
   deferred: render-pipeline plumbing and effect-screen tooltips).
5. **API features: done** (6 registration-time events: server and client reload
   listeners, creative tab contents, particle providers, atlas stitching, model
   baking; 11 deferred, see `docs/hook-inventory.md`). Next value is the
   deferred render plumbing only if a mod needs it, and the parity plan below.
6. **NeoForge parity plan** (`docs/neoforge-parity-plan.md`), most valuable
   first: join-time mod-set and registry check (done: `RegistrySyncCheck`,
   advertised `both`-side mods), data registries driven by real
   data packs (done: `PackDataRegistries`, and mod jars as data and resource
   packs via `ModPacks`), creative tabs (done: mod tab pages via
   `CreativeTabPages`, `ModCreativeTabs`), level and chunk attachments (done:
   `HolderAttachments`, `copyOnDeath`), capability lookups (done: `Lookups`,
   docs/lookups.md), loot and biome modifiers (done: `LootModifiers`,
   `BiomeModifiers`, data maps `DataMaps`, docs/world-modifiers.md), and game
   tests (done: `GameTests`, `weaverGameTest`, gate step `H-gametest`,
   docs/gametest.md). The parity plan is complete
   with item/fluid/energy handlers, data-driven loot and biome modifiers, game
   tests in CI.
6b. **Deferred events, batch 7a: done** (15 gameplay events once deferred:
   entity construction, size, section entry, visibility, effect particles,
   projectiles, XP orb targeting, spawn cluster size, equipment drop chance,
   block and entity loot levels, tree ground blocks, fluid-placed blocks,
   `/spreadplayers`, mod mismatch; `WeaverMods` lists installed mods; proofs in
   `GameEventsExtrasProof`). **Batch 7b: done** (7 client render-pipeline events)
   and **7c: done** (7 registration events); **7d: done** (feature renderers,
   client datagen registries, structure conversions): nothing is deferred.
6c. **Robustness audit: done** (commit `7b317c5`; see docs/hooks.md "What a broken mod can no
   longer take down"). Known gaps left on purpose, most useful first:
   - Done since: dropping one bad mixin and re-arming, partial-config reports, the
     per-mod problem summary (log and crash report), shared jar-in-jar libraries,
     mid-vertex section geometry, and the crash shield (`CrashShield`).
   - Boot speed: the loaded-class probe scans `getAllLoadedClasses()` per lookup.
   - `ConfigParser` reads the literal key `injectors.defaultRequire`, so
     `validateInjectors` never runs (fixing it needs care: it treats the value as a name).
7. **Client lifecycle: done.** A client that only joins remote servers now reaches
   `onLoadComplete` after its own setup and first tick; it waits for the integrated
   server only once one has started (`LifecycleHooks.serverRequiredForCompletion`,
   `LifecycleCompletionTest`). The owner approved merging it.
8. **Release readiness** (from the session's review): an installer that adds a
   launcher profile to the official Minecraft launcher, publishing `api`,
   `api-game` and the Gradle plugin to Maven, a mod-project template, Linux
   versions of the proof scripts, pushing to GitHub so CI runs, and a non-SNAPSHOT
   release.

## 7. Reference data

`docs/hook-inventory-data/` holds the data the inventory was built from:

- `neoforge-event-call-sites.tsv`: every vanilla call site of
  `EventHooks`/`CommonHooks`/`ClientHooks`/`NeoForge.EVENT_BUS.post`, with the
  event it fires (`?` = a helper that fires no event). The method column is
  empty: find the method by searching the patched source for the helper name.
- `events-status-at-generation.tsv`: the inventory at generation time. Its
  status column is stale; `docs/hook-inventory.md` is the source of truth.

To regenerate or read the call sites in context, build the NeoForge 26.3 MDK
(`net.neoforged.moddev`, NeoForge `26.3.0.101`) once. MDG writes
`build/moddev/artifacts/minecraft-patched-26.3.0.101-sources.jar`; extract it
and search for the helper names. The NeoForge API sources are
`neoforge-26.3.0.101-sources.jar` on `maven.neoforged.net`.

## 8. Related project

`Weaver-NeoForge/` (shipped next to the repo in the handoff archive) is a
separate NeoForge 26.3 port of the Weaver minimap example mod, built from the
official MDK (`.\gradlew.bat build`, `runClient`). It builds and loads; the
minimap was not checked inside a world.
