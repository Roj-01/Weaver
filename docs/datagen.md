# Datagen

`tools/datagen` is a standalone Java data generator for Weaver mods. It reads
one `datagen.json` manifest and writes a resource/data tree: blockstates,
block and item models, item definitions, language files, block loot tables,
recipes, tags, advancements, custom data-registry entries, and pack metadata.
It can also verify the tree from disk and merge several generated packs by
priority.

The tool is plain Java. It does not load the game and does not need the game
classpath unless the optional biome-merge path is used. Vanilla-facing JSON
shapes were measured against real 26.3 files; the writer names the measured
file in its source comments. The `weaver/registry` and `weaver/tags` families
are Weaver-specific and are defined by this tool.

## What it does

- Generates the files listed under [Output families and paths](#output-families-and-paths).
- Validates the whole manifest before writing anything.
- Re-parses the generated tree with `--verify` and checks each file family and
  the cross-file references.
- Computes a SHA-256 fingerprint of the output tree with `--fingerprint`.
- Merges generated packs with `--merge <priority>=<dir>`.
- Produces the same bytes for the same manifest.

## What it does not do

- It is not a full data-pack engine. It does not load packs at runtime, order
  packs in the game, replace the server pack repository, or parse arbitrary
  vanilla data paths.
- It does not design worldgen content: no biomes, features, structures, or
  dimensions are authored. `--biome-merge` copies an existing vanilla biome and
  adds one placed-feature reference at a chosen generation step.
- It does not bake or render client models. `--verify` checks JSON shape and
  references, not the client model bakery or a rendered result.
- It does not register game content. A manifest entry produces files; the mod
  still registers its blocks, items, and entities in code.
- It does not execute recipes or advancements. Verification checks their JSON
  shape, not gameplay behavior.

## Requirements

- JDK 26. `build.ps1` uses `JAVA_HOME` or
  `C:\Program Files\Java\jdk-26.0.1`.
- PowerShell 5.1 or later.
- No Gradle project or external library is required for the standalone tool.

## Standalone usage

```powershell
cd tools\datagen
.\build.ps1
.\run.ps1 --manifest datagen.json --out out --verify --fingerprint
```

`build.ps1` compiles `src` with `javac --release 26` into `build\classes` and
prints `DATAGEN BUILD ok=true classes=...`. `run.ps1` runs
`weaver.datagen.Main` and builds first only when `build\classes` is missing.

From the repository root, the same commands are:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\datagen\build.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\datagen\run.ps1 `
    --manifest examples\datagen-proof\datagen.json --out out --verify --fingerprint
```

If script execution is disabled, invoke every script with
`powershell -NoProfile -ExecutionPolicy Bypass -File`.

### Self-test

```powershell
.\test.ps1
```

`test.ps1` builds the tool and runs `weaver.datagen.DatagenSelfTest`, a
dependency-free check of deterministic generation, verifier corruption cases, a
full feature manifest, merge priority and conflicts, manifest rejections, and
tag deduplication. Success prints
`DATAGEN SELFTEST ok=true checks=<n>`; failure exits 1.

### Options

| Option | Default | Behavior |
|---|---|---|
| `--manifest`, `-m` | `datagen.json` | Manifest to validate and generate. |
| `--out`, `-o` | `out` | Output pack root. |
| `--verify` | off | Generate first when a manifest exists, then re-read the output from disk and check it. With no manifest at the default path, verify the existing output only. |
| `--quiet`, `-q` | off | Suppress per-file `DATAGEN FILE` and per-file `DATAGEN CHECK` lines. Summary lines still print. |
| `--fingerprint` | off | After a successful generation or verification, print `DATAGEN FINGERPRINT <sha256>`. |
| `--merge <priority>=<dir>` | off | Switch to merge mode. Repeat the option for each layer. See [Pack merge and priority](#pack-merge-and-priority). |
| `--game-jar`, `-g` | none | Game jar used by `--biome-merge`. |
| `--biome-merge <biome>=<feature>@<step>` | off | Patch a biome feature list in the generated output. Requires `--game-jar`; not documented further here. |
| `--help`, `-h` | off | Print usage and exit 0. |

Generation prints one `DATAGEN FILE <relative-path> bytes=<n>` line per file
unless `--quiet` is set, then:

```text
DATAGEN GEN namespace=examplemod blocks=1 items=1 recipes=2 tagKinds=3
DATAGEN ok=true files=<n> dir=<absolute-path>
```

Without `--verify`, that `DATAGEN ok=true` line ends the run. With `--verify`,
generation still prints `DATAGEN GEN`, but the final line is one of the
verification lines below instead of `DATAGEN ok=true`. Verification prints
warnings as `DATAGEN WARN ...`, problems as `DATAGEN FAIL ...`, and ends with
one of:

```text
DATAGEN verify ok=true files=<n> dir=<absolute-path>
DATAGEN verify ok=false files=<n> problems=<n> dir=<absolute-path>
```

### Exit codes

| Code | Meaning |
|---|---|
| `0` | Success. |
| `2` | Bad usage, unreadable or rejected manifest, write failure, a merge that could not produce output, or a fingerprint read failure after generation. A rejected manifest writes nothing. |
| `3` | Verification failed, verification found no output, or a merge reported problems or conflicts. A fingerprint read failure after verification also exits 3. |

## Manifest

The manifest is one JSON object. Validation completes before any file is
written; every error is collected and reported, and the process exits 2 with
`DATAGEN REJECT <error>` lines.

| Key | Required | Default | Meaning |
|---|---|---|---|
| `namespace` | yes | none | Output namespace for assets and data. Must match `[a-z0-9_.-]+`; `minecraft` is reserved and rejected. |
| `description` | no | `Weaver datagen output` | `pack.mcmeta` description. The data metadata appends ` (data)`. |
| `blocks` | no | empty | Block declarations. |
| `items` | no | empty | Item declarations. |
| `recipes` | no | empty | Shaped and shapeless crafting recipes. |
| `advancements` | no | empty | Advancement declarations. |
| `registries` | no | empty | Custom data-registry entries. |
| `tags` | no | empty | Vanilla and custom tag declarations. |

At least one of `blocks`, `items`, `recipes`, `advancements`, `registries`, or
`tags` must be non-empty.

### blocks

| Field | Required | Default | Behavior |
|---|---|---|---|
| `id` | yes | none | Resource path, for example `gem_block`. Duplicate block ids are rejected. |
| `name` | yes | none | English name. Becomes `block.<namespace>.<id>`. |
| `texture` | no | `<namespace>:block/<id>` | Texture id used by the block model's `all` slot. |
| `drops` | no | `self` | `self`, `none`, or a namespaced item id. `self` drops the block itself. `none` emits no loot table. |
| `tags` | no | empty | Block tag paths. Each adds `<namespace>:<id>` to `data/<namespace>/tags/block/<path>.json`. |

When `texture` is omitted, the tool also writes a deterministic 16x16
placeholder PNG at `assets/<namespace>/textures/block/<id>.png`. A declared
texture is not copied or generated.

```json
{
  "id": "gem_block",
  "name": "Gem Block",
  "drops": "self",
  "tags": ["gem_blocks"]
}
```

A block already gets `assets/<namespace>/items/<id>.json` and a
`block.<namespace>.<id>` translation key. Declaring an item with the same id is
rejected to avoid emitting a second `assets/<namespace>/items/<id>.json`.

### items

| Field | Required | Default | Behavior |
|---|---|---|---|
| `id` | yes | none | Resource path. Duplicate item ids are rejected. |
| `name` | yes | none | English name. Becomes `item.<namespace>.<id>`. |
| `model` | no | `generated` | `generated` or `handheld`; selects the item model parent. |
| `texture` | no | `<namespace>:item/<id>` | `layer0` texture id. |
| `tags` | no | empty | Item tag paths. Each adds `<namespace>:<id>` to `data/<namespace>/tags/item/<path>.json`. |

When `texture` is omitted, the tool writes a placeholder PNG at
`assets/<namespace>/textures/item/<id>.png`.

```json
{
  "id": "ruby",
  "name": "Ruby",
  "model": "handheld",
  "tags": ["gems"]
}
```

### recipes

| Field | Required | Default | Behavior |
|---|---|---|---|
| `id` | yes | none | Resource path. Duplicate recipe ids are rejected. |
| `type` | no | `shaped` | `shaped` or `shapeless`. |
| `category` | no | omitted | One of `building`, `redstone`, `equipment`, `misc`. |
| `result` | yes | none | Namespaced result item id. |
| `count` | no | `1` | Integer from 1 to 64. Omitted from the output when 1. |
| `pattern` | shaped only | none | 1 to 3 rows, each 1 to 3 characters. All rows must have the same width. |
| `key` | shaped only | none | Non-empty object mapping one-character symbols to namespaced item ids. |
| `ingredients` | shapeless only | none | 1 to 9 namespaced item ids. |

For a shaped recipe:

- A space in the pattern is an empty slot.
- Every non-space symbol must be defined in `key`.
- Every key symbol must appear in the pattern.
- A key must be exactly one character.
- `pattern` and `key` must not be declared on a shapeless recipe.

```json
{
  "id": "gem_block",
  "type": "shaped",
  "category": "building",
  "pattern": ["RRR", "RRR", "RRR"],
  "key": {"R": "examplemod:ruby"},
  "result": "examplemod:gem_block",
  "count": 1
}
```

```json
{
  "id": "ruby_from_block",
  "type": "shapeless",
  "ingredients": ["examplemod:gem_block"],
  "result": "examplemod:ruby",
  "count": 9
}
```

The generator writes `minecraft:crafting_shaped` or
`minecraft:crafting_shapeless`, the optional `category`, the shaped `key` and
`pattern` or the shapeless `ingredients`, and a `result` object. It does not
check that the result or ingredient items exist.

### advancements

| Field | Required | Default | Behavior |
|---|---|---|---|
| `id` | yes | none | Resource path. Duplicate advancement ids are rejected. |
| `parent` | no | omitted | Namespaced advancement id. Not checked for existence or cycles. |
| `title` | all-or-none | none | Display title. |
| `description` | all-or-none | none | Display description. |
| `icon` | all-or-none | none | Namespaced item id used as the display icon. |
| `frame` | no | `task` | `task`, `goal`, or `challenge`. |
| `background` | no | omitted | Namespaced texture id. Written only when a display exists. |
| `show_toast` | no | `true` | Display `show_toast`. |
| `announce_to_chat` | no | `true` | Display `announce_to_chat`. |
| `hidden` | no | `false` | Display `hidden`. |
| `trigger` | no | `minecraft:impossible` | Criterion trigger. |
| `item` | no | none | Item id for the item form. Forces `minecraft:inventory_changed`. |

`title`, `description`, and `icon` must be declared together or not at all.
Declaring only one or two is a manifest error. With all three, the generator
writes a `display` and adds `advancements.<namespace>.<id>.title` and
`.description` translations. Without them, the advancement has no display and
is an invisible child or root.

The criterion id is `has_item` for the item form and the trigger path otherwise,
so the default trigger produces a criterion id `impossible`. The item form adds
`conditions.items` as `[{"items": "<item-id>"}]`. The output also contains
`requirements: [[<criterion>]]` and `sends_telemetry_event: false`.

```json
{
  "id": "root",
  "title": "Gem Hunter",
  "description": "Find a ruby",
  "icon": "examplemod:ruby",
  "frame": "task",
  "background": "minecraft:gui/advancements/backgrounds/stone"
}
```

```json
{
  "id": "ruby_child",
  "parent": "examplemod:root",
  "trigger": "minecraft:inventory_changed",
  "item": "examplemod:ruby"
}
```

Parent existence and parent cycles are not validated. A parent cycle can make
the game reject or fail to load the advancement.

### registries

| Field | Required | Default | Behavior |
|---|---|---|---|
| `id` | yes | none | Namespaced custom registry id, for example `examplemod:gems`. The path must be one segment; `/` is rejected. Duplicate registry ids are rejected. |
| `entries` | no | empty | Entry declarations in manifest order. |
| `entries[].id` | yes | none | Entry path. May contain `/`. Duplicate entry ids inside one registry are rejected. |
| `entries[].data` | no | `{}` | Arbitrary JSON object passed to the runtime codec. |

```json
{
  "id": "examplemod:gems",
  "entries": [
    {"id": "ruby", "data": {"color": "red", "hardness": 7}},
    {"id": "sapphire", "data": {"color": "blue", "hardness": 9}}
  ]
}
```

Entries are written to:

```text
data/<namespace>/weaver/registry/<registryNamespace>/<registryPath>/<entryId>.json
```

The payload is not validated beyond "must be a JSON object". The runtime
`DataRegistryCodec` decides what fields mean and what is required. The generator
does not register anything in a game registry.

### tags

`tags` maps a tag kind to a map of tag paths. A kind without `:` is a vanilla
tag directory such as `block`, `item`, or `entity_type`. A kind with `:` is a
namespaced custom registry id such as `examplemod:gems`.

The value for each tag path is either an array of values or an object:

```json
{
  "values": ["examplemod:ruby", "#examplemod:shiny_gems"],
  "priority": 10,
  "replace": true
}
```

| Field | Required | Default | Behavior |
|---|---|---|---|
| `values` | yes in object form | none | Resource ids. A leading `#` makes the value a tag reference. Duplicates are removed, keeping the first occurrence. |
| `priority` | no | `0` | Integer. Only allowed for a namespaced custom registry kind. |
| `replace` | no | `false` | Writes `replace: true`. |

The array form is shorthand for `{"values": [...]}`.

Vanilla kinds write:

```text
data/<namespace>/tags/<kind>/<tagPath>.json
```

Custom kinds write:

```text
data/<namespace>/weaver/tags/<registryNamespace>/<registryPath>/<tagPath>.json
```

For vanilla kinds, `priority` must be 0; a nonzero value is a manifest error.
The generator writes `replace: true` only when requested. For custom kinds, a
nonzero priority is written as `priority`; a zero priority is omitted.

Block and item declarations also contribute tags from their own `tags` fields.
Those generated values are merged with explicit `block` and `item` tags for the
same path; values stay deduplicated in first-seen order. An explicit
declaration's `replace: true` applies to the merged file.

```json
{
  "block": {
    "gem_blocks": ["examplemod:gem_block"]
  },
  "item": {
    "gems": {"values": ["examplemod:ruby", "#examplemod:shiny_gems"]}
  },
  "examplemod:gems": {
    "shiny": {
      "values": ["examplemod:ruby", "examplemod:sapphire"],
      "priority": 10
    }
  }
}
```

A custom registry kind with `/` in its path is rejected because the runtime
loader nests files under `weaver/tags/<namespace>/<path>/`. A vanilla tag kind
may contain `/` but not start or end with one.

## Output families and paths

All paths are relative to `--out`. `<ns>` is the manifest namespace.

| Family | Path | Notes |
|---|---|---|
| Blockstate | `assets/<ns>/blockstates/<block>.json` | One unparameterized `""` variant pointing at the block model. |
| Block model | `assets/<ns>/models/block/<block>.json` | `parent: minecraft:block/cube_all`, one `textures.all` value. |
| Item model | `assets/<ns>/models/item/<item>.json` | `minecraft:item/generated` or `minecraft:item/handheld`, `textures.layer0`. |
| Item definition | `assets/<ns>/items/<block-or-item>.json` | 26.3 item model definition: `model.type: minecraft:model`, `model.model`. A block item points at the block model. |
| Language | `assets/<ns>/lang/en_us.json` | Block, item, and advancement display keys. Alphabetically sorted. |
| Block loot table | `data/<ns>/loot_table/blocks/<block>.json` | `type: minecraft:block`, pool-level singular `condition`, item entry, `rolls: 1`, `random_sequence`. Omitted when `drops` is `none`. |
| Recipe | `data/<ns>/recipe/<recipe>.json` | Shaped or shapeless crafting recipe. |
| Vanilla tag | `data/<ns>/tags/<kind>/<tag>.json` | `values` array; `replace` only when requested. |
| Advancement | `data/<ns>/advancement/<id>.json` | Singular `advancement` directory. |
| Custom registry entry | `data/<ns>/weaver/registry/<registryNs>/<registryPath>/<entry>.json` | The payload is the manifest `data` object. |
| Custom tag | `data/<ns>/weaver/tags/<registryNs>/<registryPath>/<tag>.json` | Supports `priority` and `replace`. |
| Resource metadata | `pack.mcmeta` | Resource format `[97, 1]` (`min_format` and `max_format`). |
| Data metadata | `pack.data.mcmeta` | Data format `[121, 0]` (`min_format` and `max_format`). |
| Placeholder texture | `assets/<ns>/textures/block/<id>.png` or `assets/<ns>/textures/item/<id>.png` | 16x16 deterministic placeholder, only when `texture` is omitted. |
| Marker | `.weaver-datagen` | Written by the tool; used as the overwrite-safety marker. |

JSON files use two-space indentation and end with a newline.

`pack.mcmeta` is the resource-pack metadata. `pack.data.mcmeta` is the
data-pack twin. A directory can only have one root `pack.mcmeta`, so a server
data pack uses the data metadata as its `pack.mcmeta`. The datagen proof keeps
both files in the generated tree, copies the tree to a server pack, and then
copies `pack.data.mcmeta` over `pack.mcmeta` in the copy. A jar that is only a
data pack ships the data metadata renamed to `pack.mcmeta` for the same reason.

## Verification

`--verify` reads the output directory from disk and re-parses every file. It
does not call the generator, so a writer bug cannot vouch for itself.

For each regular file, except the `.weaver-datagen` marker:

- `.png` files are decoded with `ImageIO` and must be 16x16.
- `.json` and `.mcmeta` files are parsed and checked by file family.
- Any other file type is a problem.

Per-family checks include:

| Family | Checks |
|---|---|
| Blockstate | `variants` is a non-empty object, contains the `""` key, and each variant has a string `model`. |
| Block model | `parent` is `minecraft:block/cube_all`; `textures.all` is a string. |
| Item model | `parent` is `minecraft:item/generated` or `minecraft:item/handheld`; `textures.layer0` is a string. |
| Item definition | `model` is an object with `type: minecraft:model` and a string `model`. |
| Language | Non-empty object; every value is a string; keys match block/item or advancement title/description patterns. |
| Block loot table | `type: minecraft:block`, non-empty `pools`, numeric `rolls`, non-empty `entries`; each entry is a `minecraft:item` with a string `name`; pool `condition` is an object; the plural `conditions` key is rejected. |
| Recipe | Shaped/shapeless type, valid category, non-empty pattern or ingredients, shaped key rows and symbols, result object with a string `id`. |
| Vanilla tag | Non-empty `values`; every value is a namespaced id or `#` reference. |
| Advancement | Non-empty `criteria`; each criterion has a trigger; `requirements` reference declared criteria; display title/description and icon shape; valid frame; boolean display flags; namespaced parent; boolean `sends_telemetry_event`. |
| Custom registry entry | Root must be a JSON object. The payload schema is module-defined and is not checked. |
| Custom tag | Non-empty `values`; values are namespaced ids/references or `{id, required}` objects; integer `priority`; boolean `replace`. |
| Pack metadata | `pack.description` is a string; `min_format` and `max_format` are `[major, minor]`; the old `pack_format` key is rejected. |

Cross-file checks:

- An `items/` definition's `model.model` must exist in the generated tree
  unless it is a `minecraft:` model. A missing non-minecraft model is a problem;
  a missing vanilla model is a warning.
- A block/item language key must correspond to a generated blockstate or item
  file. Otherwise it is a warning. Advancement translation keys are skipped.

Warnings print as `DATAGEN WARN` and do not fail the run. Problems print as
`DATAGEN FAIL` and make `report.ok()` false; the process exits 3. The verifier
does not compare the output with the manifest, so a hand-written tree that has
the right shape can pass.

Example failure:

```text
DATAGEN FAIL data/examplemod/advancement/root.json: missing required key "criteria"
DATAGEN verify ok=false files=14 problems=1 dir=D:/work/out
```

`--verify` alone keeps the default manifest path. If `datagen.json` exists in
the working directory, the run generates first and then verifies. There is no
separate verify-only flag; run from a directory without a `datagen.json` at the
manifest path when you want to verify an existing tree without regenerating it.

## Determinism

The writer preserves insertion order at every level:

- Generated files are written from a `LinkedHashMap` in generation order.
- Blocks, items, recipes, advancements, registry entries, tag kinds, and tag
  values follow manifest order. Values are deduplicated with a `LinkedHashSet`.
- Nested JSON objects parsed from the manifest keep their source order.
- The language map is the one exception: it is a `TreeMap`, so
  `assets/<ns>/lang/en_us.json` is alphabetically sorted on purpose.

The same manifest therefore produces the same bytes. `DatagenSelfTest` checks
this directly by generating the same manifest twice and comparing every file
and the fingerprint.

`--fingerprint` prints the SHA-256 of the output tree:

```text
DATAGEN FINGERPRINT <64 lowercase hex characters>
```

The digest covers each regular file's `/`-normalized relative path and exact
bytes in sorted path order, so the order in which the files were written does
not matter. The `.weaver-datagen` marker is included because it is a regular
file. Merge output is fingerprinted the same way.

## Pack merge and priority

`--merge` combines already-generated pack directories instead of generating
from a manifest:

```powershell
.\run.ps1 --merge 5=.\packs\base --merge 10=.\packs\addon --out .\out\merged
```

Each `--merge` value is `<priority>=<directory>`. The option is repeatable;
layers are identified in reports by their directory string. Merge mode ignores
the manifest. It re-verifies the finished tree and always prints the merged
fingerprint on success; `--verify` and `--fingerprint` are not needed in merge
mode.

Rules:

- Non-tag files (including `pack.mcmeta` and `pack.data.mcmeta`): the
  highest-priority layer wins. If several layers share the highest priority and
  their bytes differ, the first layer in input order is kept and a conflict is
  recorded. Identical bytes are not a conflict.
- Tag files (under `data/<ns>/tags/` and `data/<ns>/weaver/tags/`) are merged
  across layers. Each layer's `values` are parsed, ordered by effective
  priority (the file's own `priority` when declared, otherwise the layer
  priority) descending and then by input order, and included until and
  including the first `replace` layer. Values are concatenated in that order
  and deduplicated exactly, including `#` references.
- A merged tag always carries `"replace": true`. `priority` is written for
  custom tags and for vanilla tags only when a contributing layer declared one.
- A layer with malformed tag JSON contributes nothing and adds a problem.
- Output files are written in sorted path order. Non-tag bytes are preserved
  exactly, so the same layers in the same order produce a byte-identical tree
  and fingerprint.
- The merged output gets a `.weaver-datagen` marker with a merge note. The
  existing `--out` directory is cleared only when it is empty or carries that
  marker; any other non-empty directory is left untouched and the merge fails.

After writing, the merger runs the same verifier used by `--verify`. Verifier
problems become merge problems. The run prints:

```text
DATAGEN MERGE ok=true files=<n> conflicts=0 problems=0 dir=<path>
DATAGEN MERGE FINGERPRINT <sha256>
```

and, unless `--quiet` is set, one `DATAGEN MERGE FILE <path>` line per output
file. Conflicts print as `DATAGEN CONFLICT ...`; other diagnostics print as
`DATAGEN WARN ...`. A conflict or problem makes the exit code 3.

A common conflict is `pack.mcmeta`: two packs at the same priority with
different descriptions produce different bytes, so the merge keeps the first
and reports a conflict even when their other files are disjoint. Give the
layers different priorities or use the same metadata bytes.

This is a build-time merge of generated trees. It is not a runtime pack engine
and does not change how Minecraft orders or loads packs.

## Gradle integration

The Gradle plugin in `tools/gradle-plugin` registers a `runDatagen` task. What
exists in 2.1/2.2 is:

- `runDatagen` is a `JavaExec` task.
- It runs only when `weaver.datagenClass` is set and not blank (`onlyIf`).
- Its main class is `weaver.datagenClass`.
- Its classpath is the mod's `main` runtime classpath.
- It passes `--out <project buildDir>/weaver/datagen` and then appends
  `weaver.programArgs`.
- It applies `weaver.jvmArgs`, the debug agent options, and
  `weaver.environment`, and runs in `weaver.workingDir` or the project
  directory.

The standalone tool is not a Gradle subproject in this tree and is not
automatically on a mod's runtime classpath. The plugin does not add
`tools/datagen` to the classpath and does not invoke `weaver.datagen.Main`
itself. The supported path today is the standalone `build.ps1` / `run.ps1`
workflow described above. A fully wired `runDatagen` that runs this tool from a
mod build is a toolchain integration request; it is not implemented here.

## Proof

`examples/datagen-proof` generates a pack, verifies it, builds a small mod,
loads the pack on a real 26.3 dedicated server, and reads the generated data
through the runtime `DataRegistry` API.

Prerequisites, from the repository root:

```powershell
.\gradlew.bat :api:jar :api-game:jar :core:agentJar
```

The game classpath must exist at `work/game-26.3/classpath.txt`. Build and run:

```powershell
cd examples\datagen-proof
.\build.ps1
.\run-server.ps1
```

`build.ps1`:

- runs `tools/datagen/build.ps1`;
- runs `tools/datagen/run.ps1 --manifest datagen.json --out out/pack --verify
  --fingerprint`;
- copies `out/pack` to `out/server-pack`;
- copies `out/server-pack/pack.data.mcmeta` over
  `out/server-pack/pack.mcmeta`;
- compiles the proof mod and writes `out/mods/datagen-proof.jar`.

`run-server.ps1` copies `out/server-pack` to
`<serverDir>/weaver-test/datapacks/datagen-proof` before launch, then starts
the dedicated server with the launcher and waits for
`DATAGEN PROOF ok=true`. The pack is present in the world's `datapacks/`
directory at startup, so it is picked up by the server's normal startup pack
scan; the proof never calls `reload()`. The scripts use `WEAVER_GAME_DIR`
(default `work/game-26.3`), `WEAVER_SERVER_DIR` (default `work/server-26.3`),
and `WEAVER_SERVER_PORT` (default `25567`).

Expected build markers:

```text
DATAGEN BUILD ok=true classes=...
DATAGEN GEN namespace=datagenproof blocks=1 items=1 recipes=1 tagKinds=3
DATAGEN verify ok=true files=<n> dir=...
DATAGEN FINGERPRINT <sha256>
DATAGEN PROOF BUILD ok=true jar=... mods=...
```

Expected markers in `out/logs/server.log`:

```text
DATAGEN PROOF INIT registry=datagenproof:gems
DATAGEN PROOF CONTENT ok=true block=datagenproof:gem_block items=datagenproof:ruby,datagenproof:gem_block
DATAGEN PROOF PACK path=... advancement=1 loot_table=1 recipe=1
DATAGEN PROOF REGISTRY ruby=red/7
DATAGEN PROOF REGISTRY sapphire=blue/9
DATAGEN PROOF REGISTRY programmatic=green/1
DATAGEN PROOF TAG size=2
DATAGEN PROOF VANILLA recipe=true advancement=true
DATAGEN PROOF ADVANCEMENT ok=true
DATAGEN PROOF ok=true
```

`DATAGEN PROOF VANILLA recipe=true advancement=true` means the server's own
recipe manager contains `datagenproof:gem_block` and its advancement manager
contains `datagenproof:root`, so the vanilla data pack loader parsed the
generated files. `DATAGEN PROOF ADVANCEMENT ok=true` is printed after the proof
checks that `datagenproof:ruby` exists in `minecraft:item`; the marker name is
historical and does not mean the advancement was executed.

The generated pack contains:

```text
assets/datagenproof/blockstates/gem_block.json
assets/datagenproof/models/block/gem_block.json
assets/datagenproof/models/item/ruby.json
assets/datagenproof/items/gem_block.json
assets/datagenproof/items/ruby.json
assets/datagenproof/lang/en_us.json
assets/datagenproof/textures/block/gem_block.png
assets/datagenproof/textures/item/ruby.png
data/datagenproof/advancement/root.json
data/datagenproof/loot_table/blocks/gem_block.json
data/datagenproof/recipe/gem_block.json
data/datagenproof/tags/block/gem_blocks.json
data/datagenproof/tags/item/gems.json
data/datagenproof/weaver/registry/datagenproof/gems/ruby.json
data/datagenproof/weaver/registry/datagenproof/gems/sapphire.json
data/datagenproof/weaver/tags/datagenproof/gems/shiny.json
pack.mcmeta
pack.data.mcmeta
.weaver-datagen
```

The proof asserts:

- the pack files exist and `--verify` passed;
- `DataPackLoader` reports no problems;
- the ruby entry decodes to `red/7` and sapphire to `blue/9`;
- the programmatic `datagenproof:programmatic` entry decodes to `green/1` and
  survived the pack load at `Integer.MAX_VALUE` priority;
- the custom `datagenproof:shiny` tag has two values;
- `datagenproof:ruby` exists in the game item registry;
- the server recipe manager contains `datagenproof:gem_block` and the server
  advancement manager contains `datagenproof:root`;
- `freezeAll()` reports complete.

The proof does not assert vanilla recipe execution, advancement triggers or
display, any client rendering or model baking, or the full pack-repository
state. It does not prove that the game parsed every generated JSON file, only
that the recipe and advancement samples above were loaded.

## Limitations and troubleshooting

- Custom registry paths are one segment. `examplemod:gems` is valid;
  `examplemod:gems/sub` is rejected by the manifest. In a hand-written pack,
  `weaver/registry/examplemod/gems/sub/ruby.json` means entry `sub/ruby` in
  registry `examplemod:gems`, not registry `examplemod:gems/sub`.
- `priority` is only supported for namespaced custom tag kinds. A nonzero
  priority on `block` or `item` is a manifest error. The game-facing vanilla
  tag files do not carry priority.
- Advancement parents are not checked for existence or cycles. The verifier
  checks only that a parent is namespaced.
- There is no worldgen datagen. `--biome-merge` copies an existing vanilla biome
  and adds one placed-feature reference using `--game-jar`; it does not author
  biomes, features, structures, or dimensions.
- There is no client baking parity. The verifier checks JSON shape and
  references; it does not run the client model bakery or render a texture.
- `pack.mcmeta` is resource metadata and `pack.data.mcmeta` is data metadata.
  A server data pack needs the data metadata as `pack.mcmeta`; copying
  `pack.data.mcmeta` over it is the proof's approach. Keeping only
  `pack.mcmeta` with resource format `[97, 1]` is not a valid data pack.
- Generation clears `--out` only when it is empty or carries
  `.weaver-datagen`. An unmarked non-empty directory is left in place with
  `DATAGEN WARN out-dir not created by this tool, leaving existing files: ...`;
  stale files then count as output and can affect verification. Merge refuses
  an unmarked non-empty directory.
- An empty tag array is accepted by the manifest. A custom tag with an empty
  array is written and then fails verification (`a tag with no values is
  refused by the loader`); a vanilla tag with no values and no content
  contributions is skipped by the generator. Give every emitted tag at least
  one value.
- The verifier rejects unexpected file types. Do not put notes, `.gitkeep`, or
  other files in a generated output directory that will be verified.
- Recipe results and ingredients are not checked against declared items, and
  custom registry payloads are not schema-checked beyond being JSON objects.
- `--verify` alone still uses `datagen.json` if it exists in the working
  directory; it generates and verifies in that case. Run from another directory
  or pass `--manifest` when you mean to verify an existing tree.
- Merging same-priority packs with different `pack.mcmeta` descriptions reports
  a conflict. Use different priorities or identical metadata.
- JDK 26 is required. `build.ps1` falls back to
  `C:\Program Files\Java\jdk-26.0.1` when `JAVA_HOME` is unset.

## Related

- [Registry API](registry.md)
- [Toolchain](toolchain.md)
- [Datagen proof](../examples/datagen-proof/README.md)
