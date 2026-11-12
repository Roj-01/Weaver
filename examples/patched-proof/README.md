# Patched-game proof

This example proves the Weaver patched-game contract end to end:

- `tools\patcher\patch-game.ps1 -OutDir <dir>` produces
  `<dir>\client-patched.jar` and `<dir>\server-patched.jar`;
- a mod compiles against the patched client jar and uses the injected
  `net.weaver.patched.WeaverAttachmentHolder` and
  `net.weaver.patched.PatchedGame` members directly, with no reflection;
- `ItemStack` accepts, reads, and removes an attachment, and reports
  `weaver$patchLevel() == 1`;
- a real dedicated `ServerLevel` accepts the same attachment operations;
- the same source deliberately does not compile against the unpatched
  `work\game-26.3\client.jar`;
- the launcher starts the client and server from the patched jars.

The source imports `net.weaver.patched.*` on purpose. That import is the
compile-time part of the proof; `build.ps1` turns the expected vanilla compile
failure into a positive marker.

Status: verified on 2026-11-05 on the real client and dedicated server.

```text
PATCHED PROOF NEGATIVE ok=true
PATCHED PROOF BUILD ok=true
PATCHED PROOF ok=true side=server patchLevel=1 value=ok
PATCHED PROOF ok=true side=client patchLevel=1 value=ok
PATCHED PROOF RUN ok=true server=ok client=ok
```

The client runner retries up to three times, switching attempts two and three
to `-XX:TieredStopAtLevel=1` when the host hits the documented C2/GPU crash
during world load. The retry is host hardening; the artifact itself is proven
independently by `tools/patcher/verify.ps1`, which loads every class with
`-Xverify:all` and reports zero failures.

## Prerequisites

- JDK 26, either through `JAVA_HOME` or at
  `C:\Program Files\Java\jdk-26.0.1`.
- The agent, API, and typed game API artifacts:

  ```powershell
  .\gradlew.bat :core:agentJar :api:jar :api-game:jar
  ```

- `tools\patcher\patch-game.ps1` from the patcher workstream. The build fails
  with a clear message when it is absent; it never fakes patched jars.
- `tools\launcher\launch.ps1` with the `-PatchedClientJar` and
  `-PatchedServerJar` parameters. The run scripts fail with a clear message
  when the parameters are not present yet.
- The game under `WEAVER_GAME_DIR` (default `work\game-26.3`) and the real
  dedicated server under `WEAVER_SERVER_DIR` (default `work\server-26.3`).

## Build

```powershell
.\build.ps1
```

The build:

1. resolves the JDK, `core\build\agent\lib`, `api-*.jar`, and the newest
   `api-game-*.jar`;
2. deletes `out\`, then runs:

   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File tools\patcher\patch-game.ps1 -OutDir examples\patched-proof\out\patched
   ```

3. checks that both patched jars exist and contain
   `net/weaver/patched/WeaverAttachmentHolder.class` and
   `net/weaver/patched/PatchedGame.class`;
4. compiles `src\main\java` with the patched client jar first:

   ```text
   out\patched\client-patched.jar
   ; <work\game-26.3\classpath.txt>        (original client.jar first, then libraries)
   ; <core\build\agent\lib\*.jar>          (api, api-game, ASM, Mixin, and runtime libraries)
   ; <newest api-game\build\libs\api-game-*.jar>
   ```

   The patched jar must be first: it shadows the original `ItemStack`,
   `Entity`, `BlockEntity`, `Level`, and `LevelChunk` classes that appear later
   in `classpath.txt`.

5. compiles the same sources against the unpatched classpath as a negative
   control. That classpath is the same list without the patched jar and starts
   with the original `work\game-26.3\client.jar`:

   ```text
   <work\game-26.3\classpath.txt>
   ; <core\build\agent\lib\*.jar>
   ; <newest api-game\build\libs\api-game-*.jar>
   ```

   Since the source imports `net.weaver.patched.*`, javac must fail;
6. writes `out\patched-proof.jar` and copies it to
   `out\mods\patched-proof.jar`.

Expected build markers:

```text
PATCHED PROOF NEGATIVE ok=true
PATCHED PROOF NEGATIVE LOG log=...\out\negative-javac.log
PATCHED PROOF BUILD ok=true jar=...\out\patched-proof.jar mods=...\out\mods
```

`PATCHED PROOF NEGATIVE ok=false` means the source compiled without the patched
jar, or javac failed for a reason unrelated to the patched imports. Either case
invalidates the linkage proof, so the build prints the marker and fails instead
of producing a jar.

## Run

Start the dedicated server proof:

```powershell
.\run-server.ps1
```

Start the client proof:

```powershell
.\run-client.ps1
```

`run-client.ps1` makes up to three attempts. It re-copies the fixture world
before each attempt and switches attempts two and three to
`-XX:TieredStopAtLevel=1` because this host can hit a C2 JIT or graphics-driver
crash while loading the world. A successful attempt prints
`PATCHED PROOF CLIENT ATTEMPT number=<n>` followed by
`PATCHED PROOF CLIENT ok=true`.

Or run both after the build:

```powershell
.\run-all.ps1
```

`run-server.ps1` calls:

```powershell
tools\launcher\launch.ps1 -Side server ... -PatchedServerJar out\patched\server-patched.jar -WaitFor "PATCHED PROOF ok=true side=server"
```

`run-client.ps1` copies `examples/proof/fixture-world` to
`work\game-26.3\saves\WeaverPatchedProofFixture` and calls:

```powershell
tools\launcher\launch.ps1 -Side client ... -IntegratedServer -QuickPlayWorld WeaverPatchedProofFixture -PatchedClientJar out\patched\client-patched.jar -WaitFor "PATCHED PROOF ok=true side=client"
```

Set `WEAVER_CLIENT_WORLD` to use a different existing world. The fixture copy is
removed and recreated on every client run; the source fixture is never
modified.

## Expected evidence

Runtime markers, one per side:

```text
PATCHED PROOF ok=true side=server patchLevel=1 value=ok
PATCHED PROOF ok=true side=client patchLevel=1 value=ok
```

Script markers:

```text
PATCHED PROOF SERVER ok=true
PATCHED PROOF CLIENT ok=true
PATCHED PROOF RUN ok=true server=ok client=ok
```

A failed runtime check prints a single-line marker and the run script exits
non-zero:

```text
PATCHED PROOF ok=false side=client reason=...
PATCHED PROOF CLIENT ok=false
```

The client run also rejects any failure marker, including one from the
integrated server, even if the client-side marker passed.

## What each check does

Client, once per client when `Minecraft.getInstance().player` is non-null:

```java
ItemStack stack = new ItemStack(Items.STONE);
WeaverAttachmentHolder holder = (WeaverAttachmentHolder) stack;
holder.weaver$setAttachment("probe", "ok");
boolean has = holder.weaver$hasAttachment("probe");
String got = (String) holder.weaver$getAttachment("probe");
Object removed = holder.weaver$removeAttachment("probe");
int level = ((PatchedGame) stack).weaver$patchLevel();
```

The check passes only when `has` is true, `got` and `removed` are `"ok"`,
`level` is `1`, and `stack instanceof WeaverAttachmentHolder` is true.

Server, on `ServerStartedEvent`: `MinecraftServer.overworld()` is cast to
`WeaverAttachmentHolder`, the same set/get/remove sequence runs on the
`ServerLevel`, and the level patch level must be `1`. A missing injected
interface therefore fails with the runtime marker, not a silent skip.

## Notes

- `out/` is ignored. Neither the patched jars nor the game jars are committed
  or redistributed.
- The patched jar is still Minecraft code. Generate it locally from a legally
  obtained copy; do not ship it.
- The launcher, not this example, decides how a patched jar replaces the
  original jar at runtime. The flags above are the contract.
- See [docs/patcher.md](../../docs/patcher.md) for the pipeline design,
  determinism rules, fallback behavior, and the comparison with the
  runtime-only path.
