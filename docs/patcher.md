# Patched-game artifact pipeline

This document describes the binary patch pipeline that produces the patched
Minecraft artifacts consumed by the launcher and by mod builds. It is a design
contract, not a description of NeoForge internals.

Status: patch level 1 is implemented and verified. The tool is
`tools/patcher/patch-game.ps1`; `tools/patcher/verify.ps1` verifies determinism,
signature stripping, the injected descriptors, and a full `-Xverify:all` class
load. The example in [`examples/patched-proof`](../examples/patched-proof/README.md)
passes on a real client and dedicated server. Step `L-patcher` runs the
verifier in `tools/verify-ci.ps1`.

## Terms

- **Official jar**: the unmodified `client.jar` or dedicated server jar for
  Minecraft 26.3, obtained through the normal `tools/fetch-game` flow.
- **Patched jar**: a full copy of an official jar with a fixed, versioned set of
  binary changes applied.
- **Runtime-only path**: the existing agent + Mixin path, which leaves the
  official jar untouched and transforms classes while they load.

## Pipeline

```text
official client.jar / server jar
    -> deterministic copy + ASM patch
    -> client-patched.jar / server-patched.jar
    -> tools/launcher/launch.ps1 uses the patched jar for the requested side
    -> mods compile against the patched jar (patched jar first on the classpath)
```

The patcher entry point is fixed:

```powershell
tools\patcher\patch-game.ps1 -OutDir <dir>
```

Its required outputs are:

```text
<dir>\client-patched.jar
<dir>\server-patched.jar
```

The launcher entry point is fixed:

```text
tools\launcher\launch.ps1 -PatchedClientJar <path>
tools\launcher\launch.ps1 -PatchedServerJar <path>
```

Those are the only patched-jar flags in this phase. A patched jar is a complete
replacement for the official jar on its side, not an overlay and not an
additional classpath entry with partial classes. It keeps every original entry
and adds or modifies only the classes named by the patch level.

A mod build that wants compile-time access to patched members puts the patched
jar before the original classpath:

```text
client-patched.jar
; <game classpath.txt, original client.jar first, then libraries>
; <weaver agent lib jars, including api and api-game>
; <api-game build jar>
```

This order is required because the original `ItemStack`, `Entity`,
`BlockEntity`, `Level`, and `LevelChunk` also appear in the later classpath.
The patched copies must win resolution.

## Artifact contents

Each patched jar contains:

- every non-signature entry from the official jar;
- `net/weaver/patched/WeaverAttachmentHolder.class`;
- `net/weaver/patched/PatchedGame.class`;
- `net/weaver/patched/WeaverAttachmentStore.class` and its identity key;
- the transformed target classes with the interfaces and their methods;
- `META-INF/weaver/patch.json`.

### Signature handling

A modified signed jar cannot keep the original signature material: the JVM
raises `SecurityException: SHA-384 digest error` when a patched class is
loaded. The patcher mirrors NeoForm's `StripManifestDigestContentFilter`:

- removes direct `META-INF/*.SF`, `*.RSA`, `*.DSA`, `*.EC`, and
  `META-INF/SIG-*` entries;
- removes `Signature-Version`, `Magic`, `Digest-Algorithms`, and every
  `*-Digest` attribute from the manifest main section and per-entry sections,
  and drops empty sections.

`verify.ps1` asserts that no signature entry or digest attribute survives and
loads all official classes through a `URLClassLoader` with `-Xverify:all`.

### Verification recorded on 2026-11-05

```text
PATCHER TEST ok=true
PATCHER PROBE ok=true classes=5 loader=url
PATCHER CLASSLOAD jar=client-patched.jar ok=11387 failures=0
PATCHER CLASSLOAD jar=server-patched.jar ok=7766 failures=0
PATCHER VERIFY signatures=stripped
PATCHER VERIFY determinism=ok
PATCHER VERIFY ok=true
```

Output hashes for the two independent runs were identical:
client `a8b48cb2147bea647487e7c538ff5932bac052c5dd97678a842d599e9df0e339`,
server `d22abce34d7150c634a6dcd4e2654786b23debb4b11a85bc935073cb94584b3e`.
The patched proof then reported `side=server patchLevel=1 value=ok` and
`side=client patchLevel=1 value=ok` and `PATCHED PROOF RUN ok=true
server=ok client=ok`, with the negative control proving the same source does
not compile against the official jar.

The patched jar must not remove, rename, or reorder existing members. The
runtime-only path still attaches to the same game code, so unrelated Mixin
targets and access transformers must keep working when the patched jar is used.

## Patch level and the manifest

`net.weaver.api.WeaverApi.PATCH_LEVEL` is the string constant `"1"`. It is a
contract version, not a build number. Any change to injected types, method
descriptors, or manifest semantics requires a new patch level; rebuilding the
same patch from a different official jar does not.

`META-INF/weaver/patch.json` records what was applied. The patch-level 1 shape
is:

```json
{
  "operationCount": 42,
  "patchLevel": 1,
  "patchSet": "weaver-26.3-v1",
  "sourceSha256": "<hex>"
}
```

`operationCount` is diagnostic; the current patch set records 42. `sourceSha256`
is the SHA-256 of the official input jar. The side is implied by which jar and
launcher flag were used; the game version is implied by the official input and
the patch-set id. The same shape is written to the client and server jars. The
manifest must not contain timestamps, absolute paths, user
names, or the output jar's own hash; those either break determinism or are
circular. The field order above is stable.

The launcher checks that a supplied patched jar exists and substitutes it for
the game jar entry. It does not parse the manifest. The loader's patch probe
resolves `net.weaver.patched.PatchedGame` reflectively, with no compile-time
link. Patch level 1 declares `weaver$patchLevel()` as an instance method, so
the probe uses the `patchLevel` and `patchSet` fields from `patch.json` and
logs `GAME PATCH level=... artifact=patched patch-set=...`. An absent marker
class is reported as `artifact=unpatched action=runtime-only`; detection never
aborts boot.

## Interface injection and attachment holder contract

Patch level 1 injects these two interfaces from `net.weaver.patched`:

```java
public interface WeaverAttachmentHolder {
    Object weaver$getAttachment(String key);
    Object weaver$setAttachment(String key, Object value);
    Object weaver$removeAttachment(String key);
    boolean weaver$hasAttachment(String key);
}

public interface PatchedGame {
    int weaver$patchLevel();
}
```

The targets are fixed:

| Class | `WeaverAttachmentHolder` | `PatchedGame` |
| --- | --- | --- |
| `net.minecraft.world.item.ItemStack` | yes | yes |
| `net.minecraft.world.entity.Entity` | yes | yes |
| `net.minecraft.world.level.block.entity.BlockEntity` | yes | yes |
| `net.minecraft.world.level.Level` | yes | yes |
| `net.minecraft.world.level.chunk.LevelChunk` | yes | yes |

Patch level 1 adds `PatchedGame` to all five targets. The contract only
requires it on `ItemStack` and `Level`; the extra targets are a compatible
superset and the proof does not depend on them. `weaver$patchLevel()` returns
`1` on every patched target that implements `PatchedGame`.

The attachment methods must satisfy the following observable contract:

- storage is per holder instance, keyed by identity, not shared between equal
  or unrelated holders;
- `set` returns the previous value for the key, or `null` when the key was
  absent;
- `get` returns the current value, or `null` when the key is absent;
- `remove` returns the value that was removed, or `null` when the key was
  absent;
- `has` is true exactly when an explicit value is present;
- values are arbitrary `Object` instances and are not copied or serialized;
- the interfaces must be real JVM interfaces: `instanceof`, casts, and
  `invokeinterface` must work at runtime, not only at compile time.

Patch level 1 does not add persistence, NBT or data-component encoding, cross-
side synchronization, or network transport for attachments. The methods operate
on the live object only. Mods must use non-null string keys; a null key is
rejected. Null values are not exercised by the proof and should not be relied
on.

The injected methods delegate to `net.weaver.patched.WeaverAttachmentStore`,
which is copied into each patched jar. The store uses a weakly keyed identity
map so holders are not rooted after the game releases them; each holder's inner
map is synchronized so reads and writes from different threads are safe. The
injected method bodies are append-only straight-line delegations and do not
change existing class behavior. The proof only exercises the methods on a local
`ItemStack` and a server `Level`; it does not test persistence or concurrency.

## Determinism rules

The pipeline must be reproducible. Given the same official jar and the same
patch level, repeated runs must produce byte-identical patched jars.

- Entry order is fixed, normally sorted by path.
- Entry timestamps and extra metadata are fixed values, not the current time.
- Compression settings and the zip writer version are fixed by the tool.
- No absolute paths, host names, user names, environment dumps, or run IDs are
  written into classes, resources, or `patch.json`.
- Manifest field order is stable.
- The same patch level uses the same ASM version and the same patch code.
- Only the declared target classes and support classes are rewritten.
- The output is written to a temporary directory or temporary file and moved
  into place only after every check passes.

Determinism is not a cosmetic property. It lets the launcher and build cache
identify an artifact by its hash, makes patch failures reproducible, and keeps
`patch.json` reviewable.

## Fail-closed rules

The pipeline never produces a partially patched or silently degraded artifact.

- An absent, unreadable, or unexpected official jar fails the run; it is never
  replaced with a download or a different version.
- The input SHA-256 is computed and recorded in `patch.json` and the report. A
  caller that pins an expected input hash must compare it and fail on mismatch.
- A patch level other than the one implemented by the patcher fails the run.
- A missing target class fails the run; the patcher does not skip a class and
  continue.
- A target class that already has an incompatible interface or member fails the
  run; the patcher does not overwrite arbitrary code.
- A post-transform verification failure fails the run. Verification checks at
  minimum that both interfaces are present on every required class, that every
  method descriptor and delegation target matches, that `weaver$patchLevel()`
  returns 1, and that `patch.json` matches the requested patch level and
  patch-set id.
- Verification failure deletes the temporary output and exits non-zero. No
  output jar is left for the launcher to pick up.
- The official jars are never modified in place.
- Running with a patched jar is explicit. The launcher does not guess that a
  patched jar exists and does not use one for the other side.

The proof build applies the same discipline: it checks that the patcher ran,
checks both output jars for the injected interface classes, and fails before
compiling if anything is missing.

## Launcher behavior and unpatched fallback

The runtime-only path remains the default.

- Without `-PatchedClientJar` or `-PatchedServerJar`, the launcher uses the
  official jar exactly as it does today.
- With a patched-jar flag, the launcher uses that jar as the game jar for the
  requested side. The original jar is not also loaded first.
- In a client run the patched client classes also serve the integrated server;
  a dedicated server run uses the patched server jar only.
- If a supplied patched jar path does not exist, the launcher stops with an
  actionable error. If the jar exists but its marker class is unreadable, the
  loader's patch probe reports `probe=unavailable` and the artifact is not
  treated as patched. Supplying a patched jar is an explicit request; the
  launcher never silently substitutes the unpatched jar in its place.
- If no patched jar is supplied, a mod that directly references
  `net.weaver.patched.*` fails to load with the normal missing-class diagnostic.
  The loader must not synthesize the interfaces, inject them reflectively, or
  pretend that the mod loaded. The example's mod is only staged into the mods
  directory for patched runs.
- Additive patched members do not replace the runtime hook system. Hooks,
  events, Mixin, and access transformers keep their existing behavior on an
  unpatched runtime; a mod that needs both capabilities must still be tested on
  both paths.
- The loader's probe (`net.weaver.core.PatchDetection`) is reflective, so
  `core` and `api` do not link against `net.weaver.patched.*`; only mods that
  opt into the patched compile classpath do.

This fallback is graceful at the platform level, not at the individual mod
level: unpatched users can keep running unpatched mods, while patched-only mods
fail closed rather than degrading.

## Legal and distribution notes

Ship the tool and the patch specifications, not Minecraft.

- Do not commit, publish, mirror, or attach `client.jar`, `server-*.jar`,
  `client-patched.jar`, `server-patched.jar`, extracted classes, decompiled
  sources, or any other artifact derived from the official jars.
- `out/` and `work/` stay ignored. The proof intentionally writes its patched
  artifacts under `out/`.
- Users generate the patched jars locally from a legally obtained copy of the
  game. Generating a patched copy locally does not grant redistribution rights.
- The patched jars contain Mojang/Microsoft code and remain subject to the
  Minecraft EULA and the user's license for the game.
- The patcher and the Weaver-owned support classes are Weaver code. Keep their
  source and patch level under the repository license; keep the generated
  output out of release assets.

## Source phase and future phases

Patch level 1 is deliberately narrow: copy the official jar, inject interfaces
and methods with ASM, and verify the result. The source phase extends the same
pipeline without changing the launcher flags above.

1. **Source patches (NeoForm-style, selected classes).** Implemented in
   `tools/sourcepatcher`: decompile selected classes (default `ItemStack` and
   its nested classes) with Vineflower 1.11.1, apply unified-diff patches,
   recompile with `javac --release 25`, and rebuild the jar. See
   [Source patcher](source-patcher.md). Whole-game decompilation/recompilation
   remains future work; the selected-class phase intentionally avoids the
   synthetic/bridge risk of recompiling unrelated classes.
2. **Library patches.** Apply the same manifest, verification, and determinism
   rules to selected libraries on the game classpath. This is needed when a
   library class, not a game class, must expose an interface or a hook point.
   Classpath precedence and conflict detection become part of the launcher
   contract.
3. **Event call injection.** Insert direct calls at selected game call sites so
   hot-path events do not depend on a runtime transform. This phase needs
   per-version call-site specifications, verification that the inserted call
   sites survive obfuscation-free builds, and a policy for ordering against
   existing Mixin hooks.
4. **Gradle integration.** Teach the Gradle plugin to request, cache, and
   consume patched artifacts by official-jar hash and patch level, then use the
   same artifact for `runClient`, `runServer`, and the mod compile classpath.
   Cache keys must include the patch level and game version; a stale patched
   jar must never silently satisfy a different game build.

The remaining items are not part of the current launcher or patcher contract.`r`nThey are listed so the patch level and manifest can grow without changing the`r`nmeaning of the existing fields.

## Prior art and comparison

Public references:

- [NeoForm](https://github.com/neoforged/NeoForm) - patches and configuration
  for recompilable Minecraft sources.
- [NeoFormRuntime](https://github.com/neoforged/NeoFormRuntime) - CLI that
  builds Minecraft artifacts for compiling mods, including interface injection
  data and recompiled artifacts.
- [NeoGradle](https://github.com/neoforged/NeoGradle) - Gradle plugins that
  consume NeoForm artifacts, with a documented binary patch mode and interface
  injections for the userdev plugin.

Those projects are prior art for the general approach. Weaver's pipeline is
independent: it is a small, deterministic, local artifact for the Weaver
launcher, not a reimplementation of the NeoForge toolchain. The detailed
source-level comparison is in
[patcher reference](patcher-reference.md); this document stays focused on the
Weaver contract.

| Concern | Patched artifact (this pipeline) | Runtime-only path (agent + Mixin) |
| --- | --- | --- |
| Compile-time linkage to injected members | Yes. Mods import `net.weaver.patched.*` and call the methods directly; the vanilla compile is a negative control. | No. Mods use the Weaver API, Mixin, or reflection. |
| Distribution | Ship the patcher and patch specs; each user generates the jars locally. Never redistribute the game. | Ship the agent only; no game copy is produced. |
| Work before startup | One local patch generation, then the JVM loads a normal jar. | No jar generation; transforms run while classes load. |
| Hook coverage | Patch level 1 adds types and methods only. Changing existing behavior needs source patches or bytecode edits, which are not in this phase. | Can inject, redirect, and wrap existing methods through Mixin. |
| Debuggability | Stack traces point at patched classes; compare the manifest and source jar when debugging. | Stack traces can point at transformed classes or Mixin-generated frames. |
| Version drift | Regenerate per game version; the patcher records the input hash and the loader logs the detected patch level and patch set. | Hook specs and Mixin targets must match the running game; failures can appear at load time. |
| Startup cost | No per-launch patch generation; the runtime hook transformers still run. | No jar generation; per-class transformation at startup. |
| Failure behavior | Fail closed at patch time; a missing jar path fails the launch, and an unreadable marker is reported as `probe=unavailable`. | Failures may surface during class loading or when a hook fires. |
| Legal exposure | The generated artifact is still Minecraft code; distribution is not allowed. | No additional game artifact, but game files are still not redistributable. |

The two paths are complementary. The runtime-only path remains the fallback
and the place for broad hook injection; the patched artifact is the path that
makes injected members real compile-time API for mods.

## Proof

The end-to-end example is
[`examples/patched-proof`](../examples/patched-proof/README.md). It compiles
against `client-patched.jar` first, fails to compile against the original
`client.jar`, and then verifies at runtime:

```text
PATCHED PROOF ok=true side=client patchLevel=1 value=ok
PATCHED PROOF ok=true side=server patchLevel=1 value=ok
```

Run `build.ps1`, `run-server.ps1`, `run-client.ps1`, or `run-all.ps1`. The
recorded 2026-11-05 run passed both sides; see the verification section above.
