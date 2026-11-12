# Datagen proof

This example generates a data pack with the standalone datagen tool, verifies
it, builds a small mod, loads the pack on a real Minecraft 26.3 dedicated
server, and reads the generated data through Weaver's data registry API.

## What it proves

- `datagen.json` declares a block, an item, a shaped recipe, an advancement,
  block and item tags, a custom registry (`datagenproof:gems`), and a custom
  tag (`datagenproof:shiny` with priority 10).
- `build.ps1` runs `tools/datagen/build.ps1` and then
  `tools/datagen/run.ps1 --manifest datagen.json --out out/pack --verify
  --fingerprint`.
- The generated pack is copied to `out/server-pack` and its
  `pack.data.mcmeta` is copied over `pack.mcmeta`, so the same directory is a
  valid data pack for the server. Both metadata files are kept.
- The mod creates a `DataRegistry<Gem>` under `datagenproof:gems`, opens the
  data registration phase, adds a programmatic `datagenproof:programmatic`
  entry from `DataRegistryRegisterEvent`, loads the generated pack with
  `DataPackLoader`, applies the snapshot, and freezes all data registries.
- `run-server.ps1` copies the pack into the dedicated server world's
  `datapacks/` directory before launch, so it is picked up by the server's
  normal startup pack scan. The proof never calls `reload()`.
- On server start the proof asserts the pack files exist, the registry values
  are `ruby=red/7`, `sapphire=blue/9`, and `programmatic=green/1`, the custom
  tag has two values, the generated item is in `minecraft:item`, the generated
  recipe is in the server recipe manager, the generated advancement is in the
  server advancement manager, and the freeze report is complete.

## Build

Prerequisites from the repository root:

```powershell
.\gradlew.bat :api:jar :api-game:jar :core:agentJar
```

The game classpath must exist at `work/game-26.3/classpath.txt`. Then:

```powershell
cd examples\datagen-proof
.\build.ps1
```

`build.ps1` also compiles the proof mod and writes `out/mods/datagen-proof.jar`.
If script execution is disabled, invoke it as
`powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1`.

## Run

```powershell
.\run-server.ps1
```

The scripts read the game from `WEAVER_GAME_DIR` (default
`work/game-26.3`) and the server from `WEAVER_SERVER_DIR` (default
`work/server-26.3`), both relative to the repository root. The default server
port is `25567`; set `WEAVER_SERVER_PORT` to change it.

`run-server.ps1` copies `out/server-pack` to
`<serverDir>/weaver-test/datapacks/datagen-proof` before launch, then starts
the dedicated server and waits for `DATAGEN PROOF ok=true` for up to 180
seconds.

## Expected evidence

Build markers:

```text
DATAGEN BUILD ok=true classes=...
DATAGEN GEN namespace=datagenproof blocks=1 items=1 recipes=1 tagKinds=3
DATAGEN verify ok=true files=<n> dir=...
DATAGEN FINGERPRINT <sha256>
DATAGEN PROOF BUILD ok=true jar=... mods=...
```

Server log: `out/logs/server.log`

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
checks that `datagenproof:ruby` exists in `minecraft:item`; it does not mean an
advancement trigger was executed.

## Pack layout

```text
out/pack/
  pack.mcmeta
  pack.data.mcmeta
  .weaver-datagen
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
```

`out/server-pack` is the same tree with its `pack.mcmeta` replaced by the data
metadata. `out/mods/datagen-proof.jar` is the proof mod.

## What is proven

- The manifest generates a pack, `--verify` passes, and the fingerprint is
  printed.
- The server starts with the pack placed in the world's `datapacks/` directory;
  no reload call is made.
- `DataPackLoader` reads the pack with no problems and the data registry exposes
  `ruby=red/7`, `sapphire=blue/9`, and the programmatic `green/1` entry.
- The custom tag resolves to two values.
- `datagenproof:ruby` is registered in the game item registry.
- The server's recipe manager contains `datagenproof:gem_block` and its
  advancement manager contains `datagenproof:root`, proving the vanilla data
  pack loader parsed the generated files.
- Every data registry freezes completely.

## What is not proven

- Vanilla recipe execution: the proof does not craft the recipe or assert a
  result.
- Advancement triggers or display: the proof checks the generated advancement
  is loaded by the server and the item is registered, not that the advancement
  can be completed.
- Client rendering, model baking, or resource-pack loading. Only a dedicated
  server is started.
- The server pack repository's parsed/enabled pack list. The proof reads the
  pack files from the world directory through `DataPackLoader`; it does not
  enumerate enabled packs or assert that the game parsed every generated JSON
  file.
- Worldgen or any datagen output beyond the files listed above.
