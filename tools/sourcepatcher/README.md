# Weaver source patcher

This directory implements the NeoForm-style **source** patch layer for Weaver.
It selects official game classes, decompiles them, applies unified-diff source
patches, recompiles the patched sources with `javac`, and rebuilds the official
client and server jars with only those classes replaced. It does not use
Gradle, it does not launch the game, and it never modifies the official jars in
place.

The binary patcher in `tools/patcher` remains the production transform layer.
This pipeline proves the source-patch path: selected classes only, not a
whole-game recompile.

## Layout

| Path | Purpose |
| --- | --- |
| `targets.txt` | One class name per line, in internal (`/`) or dotted form. Default: `net/minecraft/world/item/ItemStack`. `#` comments are allowed. Names are normalized to internal (`/`) form for metadata and reports. |
| `patches/*.patch` | Unified diffs applied to the decompiled source tree, sorted by file name. |
| `src/net/weaver/sourcepatcher/ReplaceJar.java` | Deterministic jar rebuild and signature stripping. |
| `src/net/weaver/sourcepatcher/VerifyClasses.java` | GPU-free full class-load verification. |
| `build.ps1` | Compiles the helpers and resolves the pinned Vineflower jar. |
| `source-patch.ps1` | End-to-end client and server source patch pipeline. |
| `verify.ps1` | Runs the pipeline twice and checks determinism, bytecode, signatures, metadata, and class loads. |
| `out/` | Generated toolchain, work directories, jars, and reports. Ignored by git. |

## Pinned toolchain

- JDK 26 (`JAVA_HOME` or `C:\Program Files\Java\jdk-26.0.1`). The helpers are
  compiled with `javac --release 25` so the output class-file version matches
  the proven Minecraft 26.3 toolchain.
- Vineflower 1.11.1:
  - URL: `https://repo1.maven.org/maven2/org/vineflower/vineflower/1.11.1/vineflower-1.11.1.jar`
  - SHA-256: `a615d07ddbbcd489369674f40e42df639c32be95410890b38f173d5c1e2ea39c`
  - `build.ps1` resolves it from `-VineflowerJar`, then
    `out/vineflower/vineflower-1.11.1.jar` or `out/vineflower/vineflower.jar`,
    and otherwise downloads it to `out/vineflower/vineflower-1.11.1.jar`.
    Every path is checked against the pinned SHA-256. A mismatched explicit
    `-VineflowerJar` fails immediately; a mismatched cached copy is ignored and
    a verified copy is downloaded.
- `git` on `PATH` for `git apply`.

## CLI contract

```text
tools\sourcepatcher\source-patch.ps1 -OutDir <dir>
  -> <dir>\client-source-patched.jar
  -> <dir>\server-source-patched.jar
  -> <dir>\report-client.json
  -> <dir>\report-server.json
```

Parameters:

- `-ClientJar` — default `work\game-26.3\client.jar`.
- `-ServerJar` — default `work\server-26.3\versions\26.3\server-26.3.jar`.
- `-OutDir` — default `tools\sourcepatcher\out`.
- `-VineflowerJar` — optional explicit pinned Vineflower jar.
- `-TargetsFile` — default `targets.txt`.
- `-PatchesDir` — default `patches`.

Final markers:

```text
SOURCE PATCHER PATCH ok=true side=<client|server> jar=<path> classes=<n> patches=<n> verified=<n>
SOURCE PATCHER GAME ok=true client=<path> server=<path>
```

`build.ps1` prints:

```text
SOURCE PATCHER BUILD ok=true classes=<path> vineflower=<path> jdk=<path>
```

## Pipeline

For each side (client, then server):

1. Extract each target class and every `Target$*.class` from the official jar
   into `work/<side>/classes`, preserving package paths.
2. Decompile with Vineflower:
   `-dgs=1 -e=<official jar;libraries> <classes> <src>`.
3. Normalize every decompiled `.java` and every copied patch to LF.
4. Create a throwaway git repository around `src`, `git add -A`, and apply each
   patch with `git apply --whitespace=nowarn` in sorted order. Any non-zero
   `git apply` fails the run.
5. Compile every `.java` under `src` with:
   `javac --release 25 -proc:none -encoding UTF-8 -cp "<official jar;libraries>" -d compiled`.
6. Rebuild the jar with `ReplaceJar`.
7. Assert `net.minecraft.world.item.ItemStack#weaver$sourcePatchProbe()Ljava/lang/String;`
   with `javap -p -s`, then run `VerifyClasses` with `-Xverify:all`.
8. Write `report-<side>.json`.

The client uses `work\game-26.3\classpath.txt`. The server classpath is
generated from the server jar plus `work\server-26.3\libraries\*.jar`. The
server library file used for class loading is generated at
`<OutDir>\work\server-classpath.txt`.

`verify.ps1` executes the pipeline into `out\verify\run1` and
`out\verify\run2`, compares SHA-256 values, re-checks the probe method and
signature stripping, and independently runs `VerifyClasses` for both sides.
Final marker:

```text
SOURCE PATCHER VERIFY ok=true determinism=ok signatures=stripped member=ok client-sha256=<hash> server-sha256=<hash> client-classes=<n> server-classes=<n>
```

## Patch format

Patches are ordinary unified diffs rooted at the decompiled source directory:

```text
a/net/minecraft/world/item/ItemStack.java
b/net/minecraft/world/item/ItemStack.java
```

`git apply`'s default `-p1` strip is used. The checked-in probe patch is the
proven artifact and keeps its `git diff` `index` line; `git apply` applies to
the working tree and does not verify that index. The patch context is pinned to
the output of Vineflower 1.11.1 with `-dgs=1` against the official 26.3 jars. If
the game jar or the decompiler version changes, the patch context may no longer
match; regenerate the decompiled source, update the patch against that output,
keep it LF-only, and rerun `verify.ps1`. Do not hand-edit decompiled sources
outside a patch.

The default patch set is:

- `itemstack-source-probe.patch` — adds
  `public String weaver$sourcePatchProbe()` to `ItemStack`.

## Scope

Only the classes named in `targets.txt` (and their inner classes) are
decompiled. Every decompiled `.java` is recompiled, but the rest of the jar is
copied verbatim from the official jar. This is deliberately narrower than a
NeoForm whole-game recompile; it exercises the patch/compile/repack contract
without decompiling the entire game.

`ReplaceJar` refuses to write an output when a compiled class has no matching
jar entry. That is the fail-closed guard against decompiler/compiler drift
introducing synthetic classes that the official jar does not contain.

## Deterministic jar output

`ReplaceJar` rebuilds the jar with:

- entries sorted by name;
- every entry timestamp fixed at `0`;
- fixed deflate level `6` for files and `STORED` zero-length directory entries;
- no duplicate entries;
- the canonical metadata entry at `META-INF/weaver/source-patch.json`;
- a temporary file in the destination directory, post-verified and then moved
  into place.

The metadata uses a stable field order: `patchSet`, `decompiler` (`vineflower`),
`decompilerVersion` (`1.11.1`), `decompilerSha256` (the pinned Vineflower hash),
`javaRelease` (`25`), `sourceSha256` (SHA-256 of the input official jar), sorted
`classes` (internal names), sorted `patches` (file names), `patchCount`, and
sorted `targets` (internal names). It contains no timestamps, elapsed values,
absolute paths, or user names. The external `report-<side>.json` records
input/output paths and hashes, the assigned patches, class counts, and elapsed
time; reports are not embedded in the jar.

`verify.ps1` runs the full pipeline twice and requires identical client and
server jar hashes.

## Signature stripping

A modified signed jar cannot retain its signature material: the JVM would
reject every replaced class with a digest error. The rebuild mirrors NeoForm's
manifest filter:

- remove direct `META-INF/*.SF`, `META-INF/*.RSA`, `META-INF/*.DSA`,
  `META-INF/*.EC`, and `META-INF/SIG-*` entries;
- remove `Signature-Version`, `Magic`, `Digest-Algorithms`, and every
  `*-Digest` attribute from the manifest main section and all per-entry
  sections;
- drop per-entry sections that become empty.

All non-signature entries are preserved byte-for-byte in content, including
`META-INF/versions/**`.

## Verification

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\sourcepatcher\build.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\sourcepatcher\source-patch.ps1 -OutDir tools\sourcepatcher\out\manual
powershell -NoProfile -ExecutionPolicy Bypass -File tools\sourcepatcher\verify.ps1
```

`VerifyClasses` loads every class in the rebuilt jar through a
`URLClassLoader` with the official libraries on the classpath, printing:

```text
SOURCE PATCHER CLASSLOAD jar=<name> ok=<n> failures=<n>
```

The pipeline and verifier also print the side-tagged form:

```text
SOURCE PATCHER CLASSLOAD ok=true side=<client|server> classes=<n> failures=0
```

It exits non-zero on any load or verification failure. This is the GPU-free
artifact check; client world launches can hit the host's documented C2/GPU
native crash and are not part of this pipeline.

## Legal note

Do not redistribute Minecraft jars. This tooling, the patch set, and the
verification scripts operate on a jar each user supplies. The derived jars stay
local to the user's installation.
