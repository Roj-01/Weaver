# datagen

Standalone data generator for Weaver mods. It turns one `datagen.json` manifest
into blockstates, models, item definitions, lang, loot tables, recipes,
advancements, tags, and custom data-registry files, then optionally verifies or
merges the result. It is plain Java and does not need the game for normal
generation.

Full reference: [docs/datagen.md](../../docs/datagen.md).

## Build and run

```powershell
.\build.ps1
.\run.ps1 --manifest datagen.json --out out --verify --fingerprint
```

`build.ps1` compiles with JDK 26 (`JAVA_HOME` or
`C:\Program Files\Java\jdk-26.0.1`). `run.ps1` builds first when
`build\classes` is missing.

## Options

| Option | Default | Behavior |
|---|---|---|
| `--manifest`, `-m` | `datagen.json` | Manifest to generate. |
| `--out`, `-o` | `out` | Output pack root. |
| `--verify` | off | Re-read the output from disk and check it. Generates first when the manifest exists. |
| `--quiet`, `-q` | off | Suppress per-file lines. |
| `--fingerprint` | off | Print `DATAGEN FINGERPRINT <sha256>` after success. |
| `--merge <priority>=<dir>` | off | Merge generated packs instead of generating; repeatable. |
| `--game-jar`, `-g` | none | Game jar for `--biome-merge`. |
| `--biome-merge <biome>=<feature>@<step>` | off | Patch a biome feature list; repeatable. |
| `--help`, `-h` | off | Print usage. |

Exit codes: `0` success, `2` bad usage, unreadable or rejected manifest (nothing
written), or a write/merge failure, `3` verification failed or merge
conflicts/problems.

## Manifest sections

- `namespace` (required, not `minecraft`) and `description`;
- `blocks`: `id`, `name`, `texture`, `drops`, `tags`;
- `items`: `id`, `name`, `model` (`generated` or `handheld`), `texture`, `tags`;
- `recipes`: shaped or shapeless, `pattern`, `key`, `ingredients`, `result`,
  `count`, `category`;
- `advancements`: `id`, `parent`, `title`/`description`/`icon` (all or none),
  `frame`, `background`, display flags, `trigger`, `item`;
- `registries`: custom registry `id` and `entries` (`id`, `data`);
- `tags`: array form or `{values, priority, replace}`.

`tools/datagen` writes vanilla tags under `data/<ns>/tags/...` and custom tags
under `data/<ns>/weaver/tags/...`. Priority is only accepted for custom tag
kinds.

## Merge and fingerprint

```powershell
.\run.ps1 --merge 5=.\packs\base --merge 10=.\packs\addon --out .\out\merged
```

Non-tag files use the highest layer priority; equal-priority files with
different bytes are conflicts. Tags merge high-priority first and always end
with `replace: true`. Merge mode re-verifies the output, writes a
`.weaver-datagen` marker, and prints `DATAGEN MERGE FINGERPRINT <sha256>` on
success.

## Test

```powershell
.\test.ps1
```

Builds the tool and runs `weaver.datagen.DatagenSelfTest`. Success prints
`DATAGEN SELFTEST ok=true checks=<n>`; failure exits 1.
