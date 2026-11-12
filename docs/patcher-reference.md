# Patcher reference: NeoForge pipeline and Weaver implications

Status: 2026-11-05. Sources: `NeoFormRuntime` `binpatch-v3` (`0387eb8`, shallow,
2025-12-09) and `NeoForm` `main` (`64a0650`, 26.4-snapshot-7); no Gradle or game
launch was run for this reference. Weaver's patched-artifact workstream
(`docs/patcher.md`, `tools/patcher/`, `core/.../PatchDetection.java`) was
implemented after this reference pass; `tools/patcher/verify.ps1` and
`examples/patched-proof` record passing runs on 2026-11-05 (11,387 client and
7,766 server classes with zero load/verification failures; client and server
proof markers).

## 1. NeoForge pipeline

NFRT turns a NeoForm `config.json` into a graph; known step types are built in,
everything else is an external Java tool (`NeoFormEngine.java:308-414`;
`README.md:160-247`).

| Step | Produces | Evidence |
| --- | --- | --- |
| `downloadManifest`, `downloadJson`, `downloadClient`, `downloadServer`, `download*Mappings` | Manifests, official jars, ProGuard mappings | `NeoFormEngine.java:336-351` |
| `listLibraries`; `strip` | Library list; classes-only jar plus resources jar | `NeoFormEngine.java:459-500,352-384` |
| `extractServer`, `merge`, `rename` | Unbundled server, joined jar, deobfuscated jar (legacy) | NeoForm functions, `README.md:175-196` |
| `decompile`; `inject` | Source zip; sources/resources with injected data | `README.md:197-201`; `NeoFormEngine.java:385-409` |
| `patch`; `applyNeoforgePatches` | NeoForm-patched sources; NeoForge-patched sources plus rejects | `NeoFormEngine.java:393-401`; `RunNeoFormCommand.java:276-285` |
| `transformSources`; `recompile`; `mergeWithSources` | AT/Parchment/interface sources plus stubs; compiled jar; classes+sources | `ApplySourceTransformAction.java:82-146`; `NeoFormEngine.java:273-306` |
| `binaryPatch`, `copyUnpatchedClasses`, `binaryWithNeoForge` | Patched classes; full patched jar; patched game plus NeoForge classes | `RunNeoFormCommand.java:389-421`; `CopyUnpatchedClassesAction.java:13-52` |

The classic `strip -> merge -> rename` pipeline is **not** 26.x. NeoForm 26.3
generates `downloadManifest, downloadJson, downloadClient, downloadServer,
listLibraries, preProcessJar, decompile, patch`; `preProcessJar` runs
InstallerTools `PROCESS_MINECRAFT_JAR` and performs merge/deobfuscation
(`neoform/.../CreateConfig.java:59-73`; tag `v26.3-snapshot-9-1`). A graph
anchored on `rename` cannot run against 26.3.

```text
source: preProcessJar -> decompile -> patch -> applyNeoforgePatches -> transformSources -> recompile
binary: preProcessJar -> binaryPatch -> copyUnpatchedClasses -> applyDevTransforms -> inject NeoForge classes
```

## 2. `binpatch-v3`: config and wiring only

Present: `BinpatcherConfig(version,args)` (`.../BinpatcherConfig.java:5-7`);
`NeoForgeConfig.binpatches/.binpatcher` (`NeoForgeConfig.java:20-35`); `patch`
data source and nodes `binaryPatch`/`copyUnpatchedClasses`/`binaryWithNeoForge`
(`RunNeoFormCommand.java:298-317,389-421`); results `binary`/`binaryWithNeoForge`
(`ResultIds.java:39-49`). Not present: no bundle codec, LZMA/XZ code, `.binpatch`,
`.lzma`, `patches.lzma`, or `BINPATCHER` entry. The branch is **wiring plus
config**; the format lives in `net.neoforged.installertools:binarypatcher`.

Limitations: it anchors on `rename` (`RunNeoFormCommand.java:299`), lacks
NeoForge ATs on the binary result, and omits patch data from the cache key. NFRT
`main` fixes these (`vanillaDeobfuscated`, `ApplyDevTransformsAction`, and
`addDataSourceDependency("patch")`:
https://github.com/NeoForged/NeoFormRuntime/blob/main/src/main/java/net/neoforged/neoform/runtime/cli/RunNeoFormCommand.java; https://maven.neoforged.net/releases/net/neoforged/neoform-runtime/2.0.23/neoform-runtime-2.0.23-changelog.txt).

Bundle (InstallerTools `main`; pinned 4.0.17 not independently verified): LZMA2
stream, ASCII signature `NFPATCHBUNDLE001`, entry count, base mask (`CLIENT`
0x01, `SERVER` 0x02, `JOINED` 0x04), then entries. An entry is a flags byte (type
`CREATE` 0x00, `MODIFY` 0x08, `REMOVE` 0x10 plus mask 0x07), a US-ASCII path (u16
length, 0x20-0x7E, no empty/`.`/`..`), a 32-bit Adler32 base checksum for
`MODIFY`, then data length and data. `CREATE` copies, `MODIFY` checks the
checksum and applies javaxdelta GDiff, `REMOVE` deletes. Bad signature, type,
path, length, checksum, or missing target fails closed. Application preserves
base order, replaces/deletes entries, appends new ones, gives new entries ZIP time
`628041600000L`, and keeps copied times; new entries come from a `HashMap`, so
byte-identical output needs sorted paths and fixed ZIP metadata
(`InstallerTools/binarypatcher/src/main/java/net/neoforged/binarypatcher/`).

NeoForge's userdev config names `patches.lzma` and args
`--patch --base {clean} --base-type JOINED --output {output} --patches {patch}`
(`NeoForge/buildSrc/.../CreateUserDevConfig.java`; `Tools.BINPATCHER`;
InstallerTools 4.0.17 in `NeoForge/26.3.x/gradle.properties`); NFRT interpolates
it into `{patch}` (`RunNeoFormCommand.java:300-304`).

## 3. Interface injection, ATs, coremods

`interfaces.json` maps target binary names to interface binary names; the sample
is `{"net/minecraft/world/item/ItemStack":["testproject/FunExtensions"]}`
(`neoformruntime/interfaces.json`), and no build/source file consumes it. NFRT
passes `--interface-injection-data` (`RunNeoFormCommand.java:69-70`); JST injects
`implements` and writes empty stubs (`ApplySourceTransformAction.java:121-129`);
NFRT adds the stubs jar to the recompile sourcepath
(`RunNeoFormCommand.java:135-147`). JST documents binary names, generics and stubs
(https://github.com/neoforged/JavaSourceTransformer#interface-injection); real
interfaces must be on the parser classpath and, at runtime, added by Mixin/coremod
(MDG: interface injection is development-time only,
https://docs.neoforged.net/toolchain/docs/plugins/mdg/). ATs use NeoForge's `ats/`
data source (`NeoForgeConfig.java:23,61-63`; `RunNeoFormCommand.java:185-200`;
`ApplySourceTransformAction.java:96-119`) or InstallerTools
`PROCESS_MINECRAFT_JAR` on the binary path (`ApplyDevTransformsAction.java:28-49`).
Runtime ATs/coremods are FML concerns; the design doc lists
`META-INF/accesstransformer.json`/`coremods.json` and splits coremods into
injection/runtime
(https://github.com/neoforged/NeoForge/blob/d16a8d6d5d21821d105f4269b3bd71e674b75a5d/docs/NewFML.md);
NeoForm/NFRT has no coremod step.

## 4. NeoForm source patches

The 140 files under `neoform/src/patches` are DiffPatch unified diffs, one per
file, with `a/`/`b/` paths and no timestamps:

```text
--- a/net/minecraft/world/level/block/BaseEntityBlock.java
+++ b/net/minecraft/world/level/block/BaseEntityBlock.java
@@ -30,6 +_,6 @@
-        return expected == actual ? ticker : null;
+        return expected == actual ? (BlockEntityTicker<A>)ticker : null;
```

The `+_` auto header computes the modified-side start at apply time, so unrelated
line shifts need no header rewrites; context is normally three lines
(`BaseEntityBlock.java.patch:1-9`; multi-hunk `BuildContexts.java.patch:1-20`).
`CreatePatches` diffs only `.java` files from the decompiled zip against
`workspace/src/main/java` (`autoHeader(true)`, `a/`/`b/`, `\n`), writes
`src/patches` and deletes orphans (`CreatePatches.java:48-123`;
`NeoFormProjectPlugin.java:157-163`). `CreatePatchWorkspace` keys patches by
stripping `.patch` and applies with `PatchMode.OFFSET`
(`CreatePatchWorkspace.java:96-153`); NFRT uses DiffPatch `--mode OFFSET` and a
rejects zip (`PatchActionFactory.java:29-43`). Patches are mergeable because they
are path-addressed, one file per patch, offset-tolerant and independent across
files; conflicts remain possible within a file/hunk. NeoForge uses `ACCESS`
normally and `FUZZY`/`minFuzz(0.9f)` when updating
(`NeoForge/buildSrc/.../ApplyPatches.java`).

## 5. Launch: find or generate the patched game

The design doc is absent from NeoForge `main` (404); historical text:
https://github.com/neoforged/NeoForge/blob/d16a8d6d5d21821d105f4269b3bd71e674b75a5d/docs/NewFML.md.
`FMLServiceProvider.onLoad` verifies the environment/libraries and finds the
deobfuscated and patched Minecraft; if missing it triggers generation, loaded by
`FMLLaunchProvider.launch`. `FMLServiceProvider.initializer` sets mods/config
paths. `FMLLaunchProvider.launch` launches the patched game, generating it from
the vanilla jar and patch/deobf files if needed; the goal is pre-generation during
installation. ModLauncher triggers `FMLLaunchProvider`; discovery loads
`META-INF/coremods.json` and `META-INF/accesstransformer.json`. Current NeoForge
also ships `patches.lzma` in userdev; run mains are
`net.neoforged.fml.startup.Client`/`Server` (`CreateUserDevConfig.java`), FML is
12.0.12 in 26.3 (`NeoForge/26.3.x/gradle.properties`), and MDG exposes
decompiler-less mode as `disableRecompilation = true`
(https://docs.neoforged.net/toolchain/docs/plugins/mdg/). Weaver has no
FML/ModLauncher: `launch.ps1` starts `net.minecraft.client.main.Main` or
`net.minecraft.server.Main` with the agent (`launch.ps1:137-143,168-170`) and
optionally substitutes a patched jar (`launch.ps1:6-7,77-94,152-162`).

## 6. Weaver current state

| Mechanism | NeoForge implementation | Weaver current state | Implication |
| --- | --- | --- | --- |
| Official 26.3 inputs | NFRT downloads/merges (`NeoFormEngine.java:344-351`; `CreateConfig.java:65-72`) | `tools/fetch-game` downloads and SHA-1-verifies client/server (`tools/fetch-game/README.md:1-13`; `fetch-mc.sh:520-528`; `docs/toolchain.md:59-66`) | Base jars are local. |
| Deobfuscation | Official names; `preProcessJar` merges | No remap (`docs/toolchain.md:357-376,626`) | Patch official names directly. |
| Binary patch producer | `patches.lzma` + external binpatcher | `tools/patcher/patch-game.ps1` + `Main`/`JarPatcher`/`ClassPatcher`; `verify.ps1` two-run hash check | Producer exists; launch proof remains. |
| Patched-jar launch | FML finds/generates at launch | Launcher flags replace only the game jar entry (`launch.ps1:6-7,77-94,152-162`; `docs/architecture.md:207-257`) | Plumbing ready; unpatched default. |
| Patch detection | FML owns the artifact | `PatchDetection` reflects `PatchedGame` and reads `patch.json`; `LoaderAgent.logPatch` (working-tree files) | Additive, non-fatal. |
| ATs | Build-time JST/InstallerTools; runtime FML | Runtime `AccessTransformerTransformer` (`docs/architecture.md:214-231`); no build-time game AT | ATs widen access; do not add members. |
| Interface injection | JST plus stubs | None | Build-time only; runtime needs Mixin/coremod. |
| Source patches | 140 NeoForm patches; NeoForge `patches/` | None | Phase 2. |
| Recompile/target | Vineflower 1.12.0; Java 25 (`NeoForm` 26.3 `settings.gradle`) | Weaver modules JDK 26 / release 26 (`build.gradle.kts:103,109`) | Source phase needs Java 25. |
| Loader/API contract | FML/ModLauncher, `neoforge.mods.toml`, NeoForge API | Weaver agent, `weaver.mod.json`; no NeoForge compatibility (`docs/compatibility.md:344-350`) | Patch alone cannot load NeoForge mods. |

## 7. Minimum viable binary patch pipeline

```text
official client.jar / server-26.3.jar (SHA-1 verified)
  -> deterministic patcher (full-jar copy + ASM; no remap)
  -> client-patched.jar / server-patched.jar (full replacements)
  -> launcher -PatchedClientJar / -PatchedServerJar
  -> mod compile classpath with patched jar first
```

1. Verify the official jars; never download or substitute in the patcher. The
   delivered patcher copies the full jar and applies a fixed ASM edit set (patch
   level 1: marker interfaces and attachment methods in `docs/patcher.md`).
2. Write both patched jars with original entries preserved, sorted entries, epoch
   timestamps, fixed compression and an atomic move. The delivered `patch.json`
   has `patchSet`, `patchLevel`, `sourceSha256` and `operationCount`; the
   `docs/patcher.md` contract also requires `schemaVersion`, `gameVersion`,
   `side`, source file name and the interface map, so add those or relax it. Fail
   closed on missing/incompatible targets;
   the delivered `JarPatcher` and `verify.ps1` implement these checks.
3. Launch through the existing flags; the launcher fails on a missing path but
   does not validate `patch.json` yet. The patcher preserves the original
   `META-INF/MOJANGCS.SF/RSA` signatures, so signed-jar verification must be
   disabled or the manifest digests stripped (`tools/patcher/README.md`). Put the
   patched jar first on the mod/`api-game` compile classpath.
4. Cache by official SHA-256 + patcher revision + patch level; run twice and
   compare hashes; compile a probe mod against patched and confirm the same source
   fails against the official jar.

Copy concepts, not code: versioned header, distribution masks,
`CREATE`/`MODIFY`/`REMOVE`, checksum-before-apply, fail-closed, fixed metadata,
full replacement, patch manifest. NeoForge/NeoForm/NFRT/InstallerTools and Weaver are
LGPL-2.1-only; even so, do not copy their source or reuse
`patches.lzma` as Weaver's format, and do not carry over the `rename` anchor.
Distribute the patcher and Weaver-authored patch data only; never redistribute
patched jars, decompiled sources, or `work/` outputs
(`docs/toolchain.md:393-399`; `docs/patcher.md` legal section).

## 8. Path to source patches

Binary first because the launch/detection plumbing exists, it avoids
decompiler/recompiler variance, and it proves the patched surface quickly. Source
mode needs: InstallerTools 4.0.17 `PROCESS_MINECRAFT_JAR`; Vineflower 1.12.0 +
`net.neoforged:vineflower-plugins:0.1.6`; a pinned DiffPatch (NeoForm plugin
2.0.0.34, NFRT 2.0.0.36, NeoForge 26.3 2.1.0.43); `.java.patch` editing and a
Java 25 recompile target (`--release 25`, JDK 25 or ECJ). The official 26.3 jars
in `work/` are class-file
major 69 (Java 25); NeoForm and NeoForge set `javaVersion=25`, while Weaver's
modules use JDK 26 (`build.gradle.kts:103,109`). Verify the recompiled game, then
derive the binary bundle from the compiled output.

## 9. Three walls for NeoForge mod compatibility

1. **Patched surface.** NeoForge mods link against NeoForge-added members; a
   different patch set gives `NoSuchMethodError`/`NoSuchFieldError`/
   `ClassNotFoundException`.
2. **NeoForge API shim.** `net.neoforged.neoforge.*`, its event bus, registries,
   capabilities, network and data components have no Weaver equivalent
   (`docs/compatibility.md:344-350`).
3. **FML loading contract.** `neoforge.mods.toml`, ModLauncher, `IModLocator`,
   language providers, dependency resolution, coremods, runtime ATs and side/dist
   handling are absent from Weaver's `weaver.mod.json` loader. Even a perfect
   patched jar would not make FML discover or load NeoForge mods.

A patch layer addresses wall 1 for Weaver-authored surface only; it is necessary but not sufficient for NeoForge mod compatibility.

## 10. Not present or not verified

- `binpatch-v3` is shallow; earlier history and deleted patch data were not inspectable.
- No binpatch implementation, `patches.lzma`, `.binpatch`, or userdev
  `config.json` exists in either checkout; format details come from NeoForge's
  `CreateUserDevConfig` and InstallerTools `main`, not the pinned 4.0.17 artifact.
- `docs/NewFML.md` is absent from NeoForge `main`; the quote is historical.
  NeoGradle was not cloned; its claims are from
  https://docs.neoforged.net/toolchain/docs/plugins/ng/.
- `tools/patcher` was implemented after this analysis; its generated `out/`
  jars and the patched client/server runs are recorded in `docs/patcher.md`
  and `examples/patched-proof`. This document's NeoForm findings are unchanged.
- No Gradle/compiler/game run was performed; the only runtime check was reading
  class-file major 69 from the already-fetched 26.3 jars.
