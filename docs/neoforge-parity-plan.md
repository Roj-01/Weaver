# NeoForge parity plan

Status: in progress. 1.1 and 1.2 are implemented (2026-11-06), 2.1, 2.2, 3.1, 4.1 and 4.2 (2026-11-07); the plan is complete; the rest are proposals. Weaver stays its own loader with its own
API; "parity" here means covering the jobs NeoForge's systems do for mod
authors and server owners, not copying NeoForge's API or being compatible
with NeoForge mods.

Each item lists what NeoForge provides, where Weaver stands today (checked
against this tree on 2026-11-06), and the proposed work. Phases are ordered by
how much they protect real players and servers, then by how much they unlock
for mod authors.

## Phase 1: multiplayer safety

### 1.1 Mod set and registry check on join

- **NeoForge:** the client and server exchange mod lists and registry
  snapshots during login; mismatches disconnect with a readable reason, and
  numeric ids are remapped to the server's.
- **Weaver today:** no registry id sync or mod-set check. A client with a
  different mod set can join and hit silent id mismatches (wrong blocks,
  crashes on unknown entries).
- **Proposal:** in the configuration phase, over the existing `weaver:bridge`
  payload, the server sends its accepted mod list (id, version, side) and a
  per-registry fingerprint (ordered ids hashed) for every registry Weaver mods
  write to. The client compares and disconnects with a precise reason
  ("server has examplemod 1.2.0, you have 1.1.0"; "minecraft:block differs:
  examplemod:ruby missing"). Version ranges use `VersionRange`. Remapping is
  out of scope for the first cut: refuse instead of remap.
- **Size:** medium. Reuses the bridge handshake and `ModList`.
- **Done:** the loader advertises `both`-side mods in the bridge handshake
  (exact versions), and `RegistrySyncCheck` compares modded registry entries
  in numeric id order before world data is sent, refusing with the first
  difference. See docs/network.md, "Mod set and registry check on join".
  Version ranges instead of exact versions are not done.

### 1.2 Data registries driven by real data packs

- **NeoForge:** datapack registries load from vanilla data packs, reload with
  `/reload`, and sync to clients.
- **Weaver today:** `DataRegistry` reads only its own `weaver/registry` and
  `weaver/tags` trees through `DataPackRepository`; loading packs and
  `freezeAll()` are left to each mod.
- **Proposal:** register a vanilla reload listener (Weaver already hooks
  resource reload) that reads `data/<ns>/weaver/...` from the game's active
  packs, applies them with `loadAll`/`reloadAll`, freezes on server start, and
  sends values to clients through the capability-style clientbound channel.
- **Size:** medium.
- **Done:** `PackDataRegistries` loads `data/<ns>/weaver/...` from the
  server's active packs on every data pack load and syncs the layers to
  clients. Registries are not frozen, so `/reload` works. Mod jars are built-in
  data packs and resource packs (`ModPacks`), kept just above vanilla.

## Phase 2: content APIs

### 2.1 Creative tabs

- **NeoForge:** `BuildCreativeModeTabContentsEvent` plus registering new tabs.
- **Weaver today:** `BuildCreativeTabContentsEvent` (api-game) adds items to
  existing tabs; registering new tabs is not done.
- **Proposal:** a typed event in `api-game` fired when tab contents are built
  (add, insert after, remove), plus `GameRegistries` support for new tabs.
- **Size:** small to medium.
- **Done:** mods register tabs in the vanilla registry; `CreativeTabPages`
  lays mod tabs onto pages 2 and up (ten per page) and vanilla validation
  checks only vanilla tabs. The creative screen gets page buttons and a
  page label. `ModCreativeTabs` gives a builder and an item callback that
  mod code can compile against (vanilla's `Output` type is protected).
  Insert-after and remove for existing tabs are not done; adding still goes
  through `BuildCreativeTabContentsEvent`. See docs/registry.md, "Creative
  tabs".

### 2.2 Level and chunk attachments with persistence

- **NeoForge:** data attachments on levels, chunks, entities, and block
  entities, saved automatically, optionally copied on player death and synced.
- **Weaver today:** capabilities persist on items, entities, and block
  entities; `Level` and `Chunk` stores expose only `saveTo`/`loadFrom` because
  26.3 has no public save hook (docs/capabilities.md).
- **Proposal:** add hook specs for chunk serialization and a Weaver-owned
  `SavedData` per level so level and chunk values persist automatically; add a
  `copyOnDeath` flag for player values, applied in the existing respawn hook.
  Keep the raw bytes of unknown ids (already planned in the persistence fixes).
- **Size:** medium.
- **Done:** chunk values go into the chunk's save data and move from proto
  to full chunks; server level values go into a Weaver saved-data file per
  dimension; `copyOnDeath` on `AttachmentType` keeps player values through
  death, and an End return keeps all. Unknown ids keep their raw bytes
  (except on death). Client sync is the existing capability channel:
  `RUNTIME` values are sent when they change, with no resend when a player
  joins or starts watching a chunk. See
  docs/capabilities.md, "Persistence matrix".

## Phase 3: interoperability between mods

### 3.1 Capability lookup with providers

- **NeoForge:** block, entity, and item capabilities looked up through
  registered providers, with standard item, fluid, and energy handlers so mods
  can move resources between each other's machines.
- **Weaver today:** typed attachments only; docs/capabilities.md lists "no
  provider interfaces, attach events, per-block-entity or per-entity
  registrations".
- **Proposal:** a `Lookup<T, C>` API (`BlockLookup`, `EntityLookup`,
  `ItemLookup`) with providers registered per block, block entity type, entity
  type, or item, plus three standard handler interfaces: items, fluids,
  energy. Ship one reference implementation (a chest-like block entity) and a
  proof.
- **Size:** large. Needs a design review before code.
- **Done:** `BlockLookup`, `EntityLookup`, `ItemLookup` (providers per
  block, block entity type, entity type, item, plus fallbacks; shared by id),
  `ItemHandler`, `FluidHandler`, `EnergyHandler` with simple implementations,
  and standard lookups in `Lookups`. Vanilla containers are item handlers
  through fallbacks (`ContainerItemHandler`, sided for furnaces). The
  reference implementations are the vanilla container wrapper and an item
  battery in the proof rather than a new chest block. No caching or
  invalidation. See docs/lookups.md.

## Phase 4: data-driven world changes and testing

### 4.1 Global loot modifiers, biome modifiers, data maps

- **NeoForge:** JSON-driven loot changes, biome feature/spawn changes, and
  per-registry data maps.
- **Weaver today:** loot and world hooks exist (59 hook specs) but there is no
  data-driven layer on top.
- **Proposal:** build each on 1.2 (pack-driven data registries) so they reload
  with `/reload`. Start with loot modifiers; biome modifiers need a worldgen
  hook review first.
- **Size:** large, staged.
- **Done (loot and biome modifiers):** `LootModifiers` reads
  `data/<ns>/weaver/loot_modifiers` on every data pack load (add, remove,
  replace items; table filters; vanilla conditions; mod-registered kinds) and
  applies them to every top-level roll. `BiomeModifiers` reads
  `weaver/biome_modifiers`: feature changes rebuild biome generation settings
  once per world load, spawn changes apply when spawns are chosen. They are a
  Weaver reload listener rather than 1.2 data registries, because they need
  vanilla codecs and tags. See docs/world-modifiers.md.
- **Done (data maps):** `DataMapType` and `DataMaps`: values per registry
  entry from `weaver/data_maps/<registry>/<map>.json`, merged across packs
  with tags, `remove`, and `replace`; synced maps go to clients. Vanilla 26.3
  already covers compost and fuel with item components.

### 4.2 Game tests for mods

- **NeoForge:** mods register game tests and run them headless in CI.
- **Weaver today:** proofs are custom scripts with log markers; there is no
  per-mod in-game test runner, and they need a local game install.
- **Proposal:** a `weaverGameTest` Gradle task that boots the dedicated server
  with the agent, runs vanilla game-test structures registered by mods, and
  writes JUnit XML so CI can show results. Pairs with the CI workflow added in
  this branch once game files can be fetched in CI (`tools/fetch-game`).
- **Size:** medium.
- **Done:** `GameTests` registers test functions and generates their
  `test_instance` entries; the Gradle plugin's `weaverGameTest` task runs
  vanilla's game test server with the agent and writes JUnit-style XML, and
  fails the build on a required failure. Gate step `H-gametest` runs
  `examples/gametest`. See docs/gametest.md.

## Not proposed

- NeoForge API or mod compatibility: out of scope by design (see
  docs/compatibility.md).
- Per-mod classloader isolation: a documented non-goal of Weaver.
