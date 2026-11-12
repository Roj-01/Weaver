# Source patch pipeline

Contract for the NeoForm-style source phase under `tools/sourcepatcher`. It extracts selected
classes, decompiles them, applies reviewed unified diffs, and recompiles them into a
deterministic, signature-stripped replacement jar. See [`patcher.md`](patcher.md) for the
binary phase.

Status: the selected-class proof passed on a real dedicated server. Decompiling and recompiling
`ItemStack` with its nested classes and replacing it in the client and server jars worked. A
source-patched world/client run still hits documented C2/GPU crashes; the proof retries with `-XX:TieredStopAtLevel=1`.

## Purpose and relationship to the binary phase

The binary phase (`tools/patcher/patch-game.ps1`) adds interfaces and methods with ASM but does
not rewrite existing method bodies. The source phase edits existing methods, signatures, or control
flow. It complements rather than replaces the binary phase; both produce complete replacement jars,
not overlays, and neither modifies the official jar.

## CLI contract and outputs

The source patcher entry point is fixed:

```powershell
tools\sourcepatcher\source-patch.ps1 -OutDir <dir>
```

It patches both sides from `work/game-26.3/client.jar` and
`work/server-26.3/versions/26.3/server-26.3.jar`. Required outputs:

```text
<dir>\client-source-patched.jar
<dir>\server-source-patched.jar
```

There are no new launcher flags: the jars use the existing `tools\launcher\launch.ps1`
`-PatchedClientJar <path>` and `-PatchedServerJar <path>` contract. Targets come only from
`tools/sourcepatcher/targets.txt`; patches only from `tools/sourcepatcher/patches\`.

## Pipeline steps

1. **Select.** Extract entries named by `targets.txt` from the official jar. The default is
   `net/minecraft/world/item/ItemStack` plus `ItemStack$*`, so outer and nested classes travel
   together.
2. **Decompile.** Run Vineflower 1.11.1 with `-dgs=1` (keep generic signatures):

   ```text
   java -jar vineflower-1.11.1.jar -dgs=1 <extracted-classes> <decompiled-sources>
   ```

   The jar is downloaded at build time and must match SHA-256 `a615d07ddbbcd489369674f40e42df639c32be95410890b38f173d5c1e2ea39c`.
3. **Normalize.** Convert sources and patch files to LF; CRLF breaks patch application and
   repeatability.
4. **Patch.** Apply the unified diffs in `tools/sourcepatcher/patches/`. The current patch adds
   `public String weaver$sourcePatchProbe()` to `ItemStack`; a hunk that does not apply is a hard failure, with no fuzzing or skip.
5. **Compile.** Recompile against the official jar and game libraries:

   ```text
   javac --release 25 -proc:none -cp "<official.jar>;<libraries>" -d <classes-out> <sources>
   ```

   `-proc:none` keeps annotation processors out of the output.
6. **Rebuild.** Copy non-signature entries from the official jar, replace the selected classes,
   add `META-INF/weaver/source-patch.json`, and strip signatures using the binary phase's rules.
7. **Verify.** Check the added member with `javap` and load every class through a `URLClassLoader` under `-Xverify:all`.

## Patch format and refresh

Patches are unified diffs, one file per target, with game source paths, `a/` and `b/` prefixes, no
timestamps, and normally three context lines. The current patch adds:

```diff
--- a/net/minecraft/world/item/ItemStack.java
+++ b/net/minecraft/world/item/ItemStack.java
@@ -<old>,<count> +<new>,<count> @@
+    public String weaver$sourcePatchProbe() {
+        return "source:" + getItem() + "x" + getCount();
+    }
```

Patches are path-addressed. To refresh after a game or decompiler update: re-extract with the
unchanged `targets.txt`, re-run the pinned Vineflower version, normalize to LF, reapply each patch's
intent by hand, regenerate the diff preserving `a/`/`b/` with no timestamps or `index` lines, then run
`tools\sourcepatcher\source-patch.ps1 -OutDir <dir>` and require the markers below. A decompiler
version change is a refresh event even if the game jar is unchanged.

## Determinism, signatures, metadata

The jar must be byte-identical for the same official jar, decompiler, JDK, targets, and patches.
Sort entries, fix timestamps and compression, use fixed `javac` inputs, apply patches in fixed order,
and write no timestamps, absolute paths, host names, user names, environment data, or output-jar
hashes into the jar. Write to a temporary file and move it into place only after every check passes.

Signature handling matches the binary phase: remove `META-INF/*.SF`, `*.RSA`, `*.DSA`, `*.EC`,
`META-INF/SIG-*`, and the `Signature-Version`, `Magic`, `Digest-Algorithms`, and `*-Digest` manifest
attributes. A modified signed class otherwise fails with `SecurityException: SHA-384 digest error`.

`META-INF/weaver/source-patch.json` records what was applied, with a stable field order:

```json
{"decompiler":"vineflower","decompilerVersion":"1.11.1",
 "decompilerSha256":"a615d07ddbbcd489369674f40e42df639c32be95410890b38f173d5c1e2ea39c",
 "javaRelease":25,"patchCount":1,"sourceSha256":"<official jar sha-256>",
 "targets":["net/minecraft/world/item/ItemStack"]}
```

`sourceSha256` pins the game jar. The launcher does not parse this file; it is an audit record.

## Verification and markers

Every target must be extracted, decompiled, patched, compiled, and replaced; `javap` must show
`public java.lang.String weaver$sourcePatchProbe();` on `ItemStack`; no signature entry or digest may
survive; two runs must produce identical hashes; every class must load under `-Xverify:all`; and the
manifest must match the input hash and pinned decompiler.

Expected marker prefixes are fixed; trailing fields are diagnostic:

```text
SOURCE PATCHER BUILD ok=true
SOURCE PATCHER PATCH ok=true
SOURCE PATCHER GAME ok=true client=<path> server=<path>
SOURCE PATCHER CLASSLOAD ok=true side=server classes=<n> failures=0
SOURCE PATCHER CLASSLOAD ok=true side=client classes=<n> failures=0
SOURCE PATCHER VERIFY ok=true determinism=ok signatures=stripped member=ok
```

Any missing target, failed hunk, compile error, surviving signature entry, `javap` mismatch, or
class-load failure deletes the temporary output, exits non-zero, and leaves no partial jar.

## Risks and limits

- **Selected classes only.** This is not a whole-game decompile/recompile; all other classes come from the official jar unchanged.
- **Synthetic and bridge members.** Recompiling one class can change `access$...`, bridge methods, switch maps, and lambda methods. Other compiled classes may reference the old shapes, causing `NoSuchMethodError` or `IllegalAccessError`. Nested classes must travel with their outer class.
- **Pinned patches and downloads.** Patches are pinned to the exact game jar and decompiler output; updates or formatting changes can require a manual refresh. Vineflower is fetched during the build; the pinned hash prevents substitution, not unavailability.
- **Not additive-only.** A source patch can remove, rename, reorder, or change existing members. Review every hunk; no descriptor guard protects unrelated behavior.
- **No redistribution.** The output is Minecraft code with signatures stripped. Ship only the tool, targets, and patches.
- **Client instability.** The dedicated-server proof passed; the client/world run still hits the documented C2/GPU crashes and is retried with `-XX:TieredStopAtLevel=1`.

## Binary phase vs source phase

| Concern | Binary (`tools/patcher`) | Source (`tools/sourcepatcher`) |
| --- | --- | --- |
| Mechanism, scope | ASM adds interfaces and methods | Decompile, patch, recompile selected classes |
| Can change | Additive members only | Existing bodies, signatures, control flow |
| Input, review | `PatchSet.java`, injected classes | Unified diffs in `patches/` |
| Compiler, risk | None; descriptor checks | `javac --release 25 -proc:none`; synthetic drift |
| Output, metadata | `*-patched.jar`, `patch.json` | `*-source-patched.jar`, `source-patch.json` |

## Next phases

1. **Whole-game source patching.** Decompile and recompile the full game with deterministic ordering and a reject workflow. NeoForm's `createPatchWorkspace`/`createPatches` and NeoFormRuntime's source and binary modes are the reference points.
2. **Event call injection.** Insert direct calls at selected call sites, with per-version specs and ordering rules against Mixin hooks.
3. **Library patches.** Apply the same patch, manifest, and verification rules to selected libraries.
4. **Gradle integration.** Consume artifacts by official-jar hash and patch set for `runClient`, `runServer`, and the mod compile classpath; cache keys must include the game version and patch set.

## References

- [NeoForm](https://github.com/NeoForged/NeoForm)
- [NeoFormRuntime](https://github.com/NeoForged/NeoFormRuntime)
- [NeoGradle binary patch mode](https://docs.neoforged.net/toolchain/docs/plugins/ng/)
- [`docs/patcher.md`](patcher.md), [`docs/patcher-reference.md`](patcher-reference.md)
