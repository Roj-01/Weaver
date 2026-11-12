# Loot, biome, and structure modifiers, and data maps

Data packs (including the data inside mod jars) can change loot, biomes, and
structures without replacing vanilla's files. Two mods that both add to the grass drops
or to forest spawns no longer overwrite each other. The code is in
`api-game`: `net.weaver.api.game.loot.LootModifiers`,
`net.weaver.api.game.worldgen.BiomeModifiers`, and
`net.weaver.api.game.worldgen.StructureModifiers`.

Files are read on every data pack load, as `<namespace>:<path>` ids from:

| Kind | Folder |
|---|---|
| Loot modifiers | `data/<namespace>/weaver/loot_modifiers/<path>.json` |
| Biome modifiers | `data/<namespace>/weaver/biome_modifiers/<path>.json` |
| Structure modifiers | `data/<namespace>/weaver/structure_modifiers/<path>.json` |

A pack higher in the pack list replaces a file with the same id, so a pack
can switch another pack's file off by overriding it with
`"enabled": false`. A file that fails to parse is skipped with a
`[weaver] LOOT MODIFIER SKIPPED`, `BIOME MODIFIER SKIPPED`, or
`STRUCTURE MODIFIER SKIPPED` line naming the id and the reason; the rest still
load. After each load the server prints
`[weaver] WORLD MODIFIERS loot=<n> biome=<n> structure=<n>`.

Files are parsed after vanilla binds the reloaded tags and item components,
so they can use `#tags` and item stacks.

## Loot modifiers

```json
{
  "type": "weaver:add_items",
  "tables": ["minecraft:blocks/short_grass"],
  "conditions": [{"type": "minecraft:random_chance", "chance": 0.05}],
  "items": [{"id": "examplemod:seed_pouch"}]
}
```

| Field | Meaning |
|---|---|
| `type` | The modifier kind (below). |
| `tables` | Optional. Loot table ids the file applies to. Absent or empty: every table that has an id. |
| `conditions` | Optional. Vanilla loot conditions; all must pass for the roll. They see the roll's context (tool, killer, luck, position). |
| `enabled` | Optional, default `true`. |

A condition or modifier that throws during a roll skips that file for the
roll, with a `[weaver] LOOT MODIFIER FAILED` line; the loot is still
generated.

Kinds:

| Type | Fields | Effect |
|---|---|---|
| `weaver:add_items` | `items`: item stacks | Appends copies. |
| `weaver:remove_items` | `items`: an item id, a list, or a `#tag` | Removes matching stacks. |
| `weaver:replace_items` | `from`: ids or a tag; `to`: an item id | Swaps the item, keeping counts and dropping components. |

Files apply in id order, each to the output of the ones before. They run on
the full result of a top-level roll (block drops, entity drops, chest loot,
fishing, archaeology, ...). Tables referenced from inside another table are
not modified separately, so a modifier on the outer table sees everything
once. Tables built in code, without a registry id, are not modified.

A mod adds its own kind with a codec:

```java
LootModifiers.registerType(Identifier.fromNamespaceAndPath("examplemod", "double_ores"),
        DoubleOres.CODEC); // MapCodec<? extends LootModifier>
```

`LootModifier#apply(List<ItemStack> loot, LootContext context)` changes the
list in place. Register types during mod setup, before the first data load.
`LootModifiers.loaded()` lists the active files, and `tableId(level, table)`
gives a table's id.

## Biome modifiers

```json
{
  "type": "weaver:add_spawns",
  "biomes": "#minecraft:is_forest",
  "category": "creature",
  "spawners": [{"type": "examplemod:deer", "count": {"type": "minecraft:uniform", "min_inclusive": 2, "max_inclusive": 4}, "weight": 8}]
}
```

`biomes`, `features`, and `entities` take an id, a list of ids, or a `#tag`.
Mods add kinds with `BiomeModifiers.registerType(id, codec)`; a
`BiomeModifier` changes features (`modifyFeatures`, once per biome at load)
and spawn lists (`modifySpawns`, whose answer may depend only on the biome
and category, since it is cached).

| Type | Fields | Effect |
|---|---|---|
| `weaver:add_features` | `biomes`, `features` (placed features), `step` | Adds the features at the end of that generation step. |
| `weaver:remove_features` | `biomes`, `features`, optional `steps` | Removes them from the listed steps, or from every step. |
| `weaver:add_spawns` | `biomes`, `category`, `spawners` (entries of `type`, `count`, `weight`) | Adds spawn entries. |
| `weaver:remove_spawns` | `biomes`, `entities`, optional `categories` | Removes spawn entries of those entity types. |

Steps are vanilla's generation steps in lower case (`underground_ores`,
`vegetal_decoration`, ...); categories are mob categories (`monster`,
`creature`, `ambient`, ...).

**When they apply.**

- Feature changes are made once, when a world's data first loads and
  before any level exists, by rebuilding each matching biome's generation
  settings. Biome features are fixed for a running server, so changing a
  feature file needs a restart. They affect chunks generated afterwards.
- Spawn changes apply to the biome's own spawn list each time spawns are
  chosen: natural spawning (the list `PotentialSpawnsEvent` then sees) and the
  creatures placed with a new chunk. Structure spawn overrides (swamp hut
  witches, monument guardians, outpost pillagers) are not changed. The result
  is cached per biome and category until the next data load. They follow
  `/reload`.

**Feature order.** Vanilla requires one consistent order of features across
all biomes. Adding a feature that other biomes already use, in a different
step or in a different relative position, can stop world generation with a
"Feature order cycle" error. Add your own placed features, or add a vanilla
one in the step it already uses.

## Structure modifiers

```json
{
  "type": "weaver:add_biomes",
  "structures": "minecraft:igloo",
  "biomes": "#examplemod:is_tundra"
}
```

`structures`, `biomes`, and `entities` take an id, a list of ids, or a `#tag`.
Mods add kinds with `StructureModifiers.registerType(id, codec)`. A
`StructureModifier` gets a `StructureSettingsEditor` with the structure's
biomes, spawn overrides, generation step, and terrain adaptation, and changes
them in place.

| Type | Fields | Effect |
|---|---|---|
| `weaver:add_biomes` | `structures`, `biomes` | The structures may also generate in these biomes. |
| `weaver:remove_biomes` | `structures`, `biomes` | The structures no longer generate in these biomes. |
| `weaver:disable` | `structures` | The structures generate nowhere. |
| `weaver:add_spawns` | `structures`, `category`, `spawners`, optional `bounding_box` (`piece` or `full`) | Adds mobs that spawn inside the structure. Without an override for the category one is created, which then replaces the biome's spawns there. |
| `weaver:remove_spawns` | `structures`, `entities`, optional `categories` | Removes those mobs from the structure's spawn overrides. |
| `weaver:clear_spawns` | `structures`, `categories` | Removes the overrides: the biome's spawns apply inside the structure again. |
| `weaver:set_generation` | `structures`, optional `step`, optional `terrain_adaptation` (`none`, `bury`, `beard_thin`, `beard_box`, `encapsulate`) | Changes when the structure is placed and how the terrain fits around it. |

Spawn overrides are vanilla's mobs inside structures: swamp hut witches,
monument guardians, outpost pillagers, fortress blazes.

**When they apply.**

- On every data pack load, each structure's settings are rebuilt from its own
  file's settings with the loaded modifiers. Removing a file undoes it.
- Spawn, step, and terrain changes follow `/reload`.
- Biome changes decide where a structure is planned. The plan is made when a
  level starts, so a biome change needs a restart and affects chunks generated
  afterwards. On the first load the modifiers apply before any level exists, so
  a new world uses them from the start.
- `StructureModifiers.original(structure)` returns the settings before any
  modifier.

## Data maps

A data map gives one value per entry of a registry: a sell value per item,
a danger level per biome. The mod declares the map; packs give the values.

```java
public static final DataMapType<Item, Integer> SELL_VALUE = DataMaps.register(DataMapType.create(
        Identifier.fromNamespaceAndPath("examplemod", "sell_value"), Registries.ITEM, Codec.INT).synced());

Integer value = SELL_VALUE.get(stack.typeHolder()); // or get(ResourceKey), values()
```

Register during mod setup. The values file is
`data/<map namespace>/weaver/data_maps/<registry>/<map path>.json`, where
`<registry>` is the registry path for a `minecraft` registry (`item`,
`block`, `worldgen/biome`) and `<namespace>/<path>` for a mod registry:

```json
{
  "replace": false,
  "values": {
    "#minecraft:logs": 4,
    "minecraft:diamond": 200
  },
  "remove": ["minecraft:birch_log"]
}
```

- Every pack's copy of the file is read, lowest pack first, and merged.
  `"replace": true` drops what lower packs gave.
- `values` keys are entry ids or `#tags`; later keys win, in a file and
  across packs. Values are read with the map's codec (registry-aware, so
  values can name items or other registry entries).
- `remove` takes ids or tags and drops their values.
- An unknown id or tag, or a value the codec rejects, is skipped with a
  `[weaver] DATA MAP problem` line; the rest loads. After each load the
  server prints `[weaver] DATA MAPS loaded maps=<n> values=<n>`.
- Maps reload with `/reload`. Synced maps (`.synced()`) are sent to clients
  on join and after each reload; a client in the same process as the
  server keeps the server's values directly. Values for dynamic registries
  (biomes, enchantments) are keyed by id and work on both sides.

Vanilla 26.3 already makes compost chance and furnace and brewing fuel
item data components, so Weaver ships no built-in data maps for them.

## Tests and proof

The server proof (`GameEventsModifiersProof`) loads modifier files from the
proof mod's jar data (`examples/proof/src/main/resources/data/weaverproof/`):

- dirt drops gain two diamonds;
- a file whose condition never passes adds nothing;
- a disabled file does nothing;
- stone drops mossy cobblestone instead of cobblestone;
- coarse dirt drops nothing, through a `#minecraft:dirt` tag;
- sand is untouched, and a file with an unknown type is skipped.

It also checks the biome at the proof position:

- a placed feature added to `vegetal_decoration`;
- `ore_coal_upper` removed while `ore_coal_lower` stays;
- mooshrooms added to the creature spawns;
- witches removed from the monster spawns.

For structures it checks, in the live registry:

- igloos also in deserts, and their own settings kept as the original;
- the igloo's step and terrain adaptation changed;
- desert pyramids out of deserts, and desert ruined portals disabled;
- zombies added next to the swamp hut witches;
- guardians removed from the monument;
- the outpost's monster override cleared;
- a file switched off with `"enabled": false` does nothing.

For data maps (`ProofDataMaps`), the mod jar gives base values (a `#logs`
tag, diamond, emerald, stick, an unknown id, a bad value) and the world data
pack overrides diamond and removes stick and birch log; the proof checks
the merged values, a biome map at the proof position, and that the sync
payload round-trips with only the synced map in it.

A `/reload` goes through the same listener but is not exercised by the
proof. `examples/multiplayer` checks that a separate client receives a synced
data map from a dedicated server (`DATA MAPS synced`).

## Limitations

- Biome feature changes need a restart; only spawns follow `/reload`.
- Structure biome changes need a restart (the structure plan is made when a level
  starts); their spawn, step, and terrain changes follow `/reload`.
- No modifiers for carvers, structure pieces and templates, or biome climate and
  effects.
- Data map values are not cleared when a client leaves a server; the next
  server that syncs the map replaces them.
