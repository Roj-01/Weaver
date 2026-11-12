# Weaver binary patcher

This directory is Weaver's first real game-patch pipeline. It takes an official
Minecraft 26.3 jar, injects the `net.weaver.patched` API, adds the attachment and
patch-level contract to five core game classes, and writes a deterministic
patched jar that mods compile against. It is the Weaver equivalent of the
NeoForge layer that derives a patched artifact before launch; it does not
modify the official jar and it does not use Gradle.

## Layout

| Path | Purpose |
| --- | --- |
| `src/net/weaver/patcher/Main.java` | CLI entry point |
| `src/net/weaver/patcher/JarPatcher.java` | deterministic jar read/patch/verify/write pipeline |
| `src/net/weaver/patcher/ClassPatcher.java` | ASM class transform for the patch set |
| `src/net/weaver/patcher/PatchSet.java` | `weaver-26.3-v1` targets and descriptors |
| `src/net/weaver/patcher/InjectedClasses.java` | injected-class loading and contract validation |
| `inject/net/weaver/patched/` | classes copied unchanged into the game jar |
| `test/` | fixture self-test and classloader probe |
| `build.ps1` | compiles injected classes, the tool, and tests |
| `patch-game.ps1` | patches both official jars |
| `verify.ps1` | build + self-test + two patch runs + `javap` + classloader probe |

Generated files live in `tools/patcher/out` (ignored by git). Only the patcher
writes there; `work/game-26.3/client.jar` and the server jar are opened read-only.

## CLI contract

```text
net.weaver.patcher.Main --input <jar> --output <jar> --inject <dir> --report <json> --patch-level 1
```

* `--input` — official jar to read. It is never modified.
* `--output` — patched jar. Written to a temporary file in the destination
  directory and moved into place only after post-verification succeeds.
* `--inject` — directory containing compiled `net/weaver/patched` classes.
  `build.ps1` produces this at `out/inject`.
* `--report` — JSON report path.
* `--patch-level` — must be `1` for this patcher.

The tool classpath is written to `out/patcher-classpath.txt`:

```powershell
$cp = (Get-Content tools\patcher\out\patcher-classpath.txt -Raw).Trim()
java -cp $cp net.weaver.patcher.Main --input work\game-26.3\client.jar `
    --output tools\patcher\out\client-patched.jar `
    --inject tools\patcher\out\inject `
    --report tools\patcher\out\report-client.json `
    --patch-level 1
```

## Integration contract

```powershell
tools\patcher\patch-game.ps1 -OutDir <dir>
```

produces:

```text
<dir>\client-patched.jar
<dir>\server-patched.jar
<dir>\report-client.json
<dir>\report-server.json
```

`patch-game.ps1` builds the tool when outputs are missing or stale. Its final
line is:

```text
PATCHER GAME ok=true client=<abs path> server=<abs path>
```

Default paths are `work/game-26.3/client.jar`,
`work/server-26.3/versions/26.3/server-26.3.jar`, and `tools/patcher/out`.

## Patch set `weaver-26.3-v1` (level 1)

Targets:

```text
net/minecraft/world/item/ItemStack
net/minecraft/world/entity/Entity
net/minecraft/world/level/block/entity/BlockEntity
net/minecraft/world/level/Level
net/minecraft/world/level/chunk/LevelChunk
```

Every target receives:

* interface `net/weaver/patched/WeaverAttachmentHolder`
* interface `net/weaver/patched/PatchedGame`
* these public methods, delegating to
  `net/weaver/patched/WeaverAttachmentStore`:

| Method | Descriptor | Store call |
| --- | --- | --- |
| `weaver$getAttachment` | `(Ljava/lang/String;)Ljava/lang/Object;` | `get(Object,String)` |
| `weaver$setAttachment` | `(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;` | `set(Object,String,Object)` |
| `weaver$removeAttachment` | `(Ljava/lang/String;)Ljava/lang/Object;` | `remove(Object,String)` |
| `weaver$hasAttachment` | `(Ljava/lang/String;)Z` | `has(Object,String)` |
| `weaver$patchLevel` | `()I` | returns constant `1` |

The added method bodies are straight-line bytecode only:

```text
ALOAD 0
ALOAD 1
[ALOAD 2]
INVOKESTATIC net/weaver/patched/WeaverAttachmentStore.<get|set|remove|has>(...)
ARETURN or IRETURN
```

`weaver$patchLevel()` is `ICONST_1; IRETURN`. No fields, constructors, access
flags, existing methods, or existing StackMapTable frames are changed.

`PatchedGame` is added to all five targets. The task required at least
`ItemStack` and `Level`; all five are safe because none already has a member with
these names or descriptors. `Entity` and `BlockEntity` carry a generic class
`Signature` attribute; the patcher appends the two non-generic interfaces to
that signature as well. Without that update `javap` and `javac` would ignore the
new interface for those two classes.

## Injected classes

Compiled separately with `--release 25` (class-file major 69) so ASM can read
them, then copied byte-for-byte into the patched jar:

```text
net/weaver/patched/PatchedGame.class
net/weaver/patched/WeaverAttachmentHolder.class
net/weaver/patched/WeaverAttachmentStore.class
net/weaver/patched/WeaverAttachmentStore$IdentityKey.class
```

`WeaverAttachmentStore` uses only `java.base`: a
`Collections.synchronizedMap(new WeakHashMap<>())` keyed by an identity wrapper,
with synchronized inner maps. The wrapper is a `WeakReference` with a
`ReferenceQueue`; the map value strongly references the wrapper so it cannot be
collected while the holder is alive, while the holder itself stays weakly
reachable. A holder's attachments disappear when the holder becomes unreachable.
`clear(Object)` drops the holder entry; `size()` returns the number of live
holder entries.

The patcher validates the injected bytecode before writing anything: required
files present, class paths match internal names, class-file version no newer
than 69, and every store/interface method has the exact public/static/abstract
shape and descriptor.

## Determinism

* Entries are ordered by name; the output has no duplicate entries.
* Every entry gets timestamp `0` (1980-01-01 in the DOS epoch).
* All non-directory entries are `DEFLATED` at a fixed level `6`; directory
  entries are `STORED` with zero size/CRC.
* The embedded `META-INF/weaver/patch.json` contains no clock or elapsed value:
  patch set id, patch level, source SHA-256, and operation count only. The
  current game patch records 42 operations (35 target edits, 2 class-signature
  updates, 4 injected class entries, and the metadata entry).
* The external report includes elapsed time, but it never feeds back into the
  jar.
* `verify.ps1` patches both jars twice into separate directories and compares
  SHA-256 values. The process-under-test produces identical hashes.

## Fail-closed behavior

The patcher exits non-zero and never moves a partial jar into place when:

* the input jar or a target class is missing;
* the input has duplicate entries;
* a target already implements one of the added interfaces;
* a target already has a field or method with one of the added names
  (descriptor conflict is treated as a conflict too);
* an injected entry collides with an input entry, or a required injected class
  is missing/malformed/wrongly compiled;
* post-verification fails: every original entry must survive with the same
  size/CRC (except the five patched classes), every added interface/method must
  exist with the expected access and descriptors, the delegating bodies must be
  the expected straight-line code, existing metadata/method counts must be
  unchanged, injected bytes and metadata must match, and the class-signature
  update must be present where required.

The destination is written to a temporary file in the output directory. An
existing destination is removed before patching starts, so a failed run cannot
leave a stale artifact for the launcher to pick up; on failure the temporary file
is deleted and the destination remains absent.

## Signatures and legal notes

A modified signed jar cannot keep the original signature material: the JVM would
reject every patched class with a digest error. The patcher therefore mirrors
NeoForm's `StripManifestDigestContentFilter`:

- removes direct `META-INF/*.SF`, `META-INF/*.RSA`, `META-INF/*.DSA`,
  `META-INF/*.EC`, and `META-INF/SIG-*` entries;
- strips `Signature-Version`, `Magic`, `Digest-Algorithms`, and every
  `*-Digest` attribute from the manifest main section and per-entry sections,
  and drops sections that become empty.

`verify.ps1` asserts that no signature entry or digest attribute survives in the
patched client or server jar. The resulting jars load through a standard
`JarFile`/`URLClassLoader` without a signed-jar rejection. All non-signature
entries are preserved; `META-INF/versions/**` is untouched.

Do not redistribute Minecraft jars. Ship this tool, the patch set, and the
Verifier; each user supplies their own official 26.3 jar, and the pipeline
derives the patched artifact locally.

## Build and verification

Requirements: JDK 26 (`JAVA_HOME` or `C:\Program Files\Java\jdk-26.0.1`) and
the ASM 9.11.1 jars already present in `core/build/agent/lib`.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\patcher\build.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\patcher\patch-game.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\patcher\verify.ps1
```

Expected success markers:

```text
PATCHER TEST ok=true
PATCHER BUILD ok=true
PATCHER GAME ok=true
PATCHER PROBE ok=true
PATCHER VERIFY ok=true
```

The self-test builds an unsigned fixture jar containing `fixture.Dummy`, patches
it with the same `JarPatcher`, loads it through `URLClassLoader`, verifies the
added interfaces/methods, exercises the attachment and patch-level methods, and
confirms the original `hello()`/`twice()` bytecode still works. It also checks
determinism, original entries/manifest preservation, fail-closed behavior for a
conflicting member, and fail-closed behavior for a missing target.
