# Source-patch proof

This example proves the Weaver source-patch contract end to end:

- `tools\sourcepatcher\source-patch.ps1 -OutDir <dir>` produces
  `<dir>\client-source-patched.jar` and `<dir>\server-source-patched.jar`;
- a mod calls the source-added `ItemStack.weaver$sourcePatchProbe()` member
  directly, with no reflection;
- the same source deliberately does not compile against the official
  `work\game-26.3\client.jar`;
- the launcher starts the client and server from the source-patched jars with
  `-PatchedClientJar` and `-PatchedServerJar`.

The source-patched member is:

```java
public String weaver$sourcePatchProbe() {
    return "source:" + getItem() + "x" + getCount();
}
```

The direct call is the compile-time proof. The runtime check constructs
`new ItemStack(Items.STONE)`, calls the member, and requires the result to be
non-null, start with `source:`, contain `x1`, and contain the stone item
representation (`Items.STONE.toString()`, normally `minecraft:stone`).

Status: verified on 2026-11-05. After a full `build.ps1`, the client proof
logged `SOURCE PATCH PROOF ok=true side=client value=source:minecraft:stonex1`
and `run-all.ps1` logged `SOURCE PATCH PROOF RUN ok=true server=ok client=ok`.
This is recorded evidence, not a permanent guarantee; rerun the proof after
changing the source patches. The markers below are the contract it checks.

## Prerequisites

- JDK 26, either through `JAVA_HOME` or at
  `C:\Program Files\Java\jdk-26.0.1`.
- The agent, API, and typed game API artifacts:

  ```powershell
  .\gradlew.bat :core:agentJar :api:jar :api-game:jar
  ```

- `tools\sourcepatcher\source-patch.ps1` from the source-patch workstream. The
  build fails with a clear message when it is absent; it never fakes patched
  jars.
- `tools\launcher\launch.ps1` with `-PatchedClientJar` and
  `-PatchedServerJar`. The run scripts fail with a clear message when the
  parameters are not present yet.
- The game under `WEAVER_GAME_DIR` (default `work\game-26.3`), the real
  dedicated server under `WEAVER_SERVER_DIR` (default `work\server-26.3`), and
  the fixture world at `examples\proof\fixture-world`.

## Build

```powershell
.\build.ps1
```

The build:

1. resolves the JDK, `core\build\agent\lib`, `api-*.jar`, and the newest
   `api-game-*.jar`;
2. deletes `out\`, then runs:

   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File tools\sourcepatcher\source-patch.ps1 -OutDir examples\source-patch-proof\out\source-patched
   ```

3. requires both patched jars to exist;
4. compiles `src\main\java` with this exact classpath order:

   ```text
   out\source-patched\client-source-patched.jar
   ; <work\game-26.3\classpath.txt>        (official client.jar first, then libraries)
   ; <core\build\agent\lib\*.jar>          (api, api-game, ASM, Mixin, and runtime libraries)
   ; <newest api-game\build\libs\api-game-*.jar>
   ```

   The source-patched jar must be first: it shadows the official `ItemStack`
   that appears later in `classpath.txt`.

5. compiles the same sources against the official classpath as a negative
   control, with no source-patched jar:

   ```text
   <work\game-26.3\classpath.txt>          (official client.jar first)
   ; <core\build\agent\lib\*.jar>
   ; <newest api-game\build\libs\api-game-*.jar>
   ```

   Javac must fail with `cannot find symbol` for
   `weaver$sourcePatchProbe()`. If the source compiles without the
   source-patched jar, the build prints
   `SOURCE PATCH PROOF NEGATIVE ok=false` and fails;
6. writes `out\source-patch-proof.jar` and copies it to
   `out\mods\source-patch-proof.jar`.

Expected build markers:

```text
SOURCE PATCH PROOF NEGATIVE ok=true
SOURCE PATCH PROOF NEGATIVE LOG log=...\out\negative-javac.log
SOURCE PATCH PROOF BUILD ok=true jar=...\out\source-patch-proof.jar mods=...\out\mods
```

The negative javac output is written to `out\negative-javac.log` for evidence.

## Run

Start the dedicated server proof:

```powershell
.\run-server.ps1
```

Start the client proof:

```powershell
.\run-client.ps1
```

Or run both after the build:

```powershell
.\run-all.ps1
```

`run-server.ps1` calls:

```powershell
tools\launcher\launch.ps1 -Side server ... -PatchedServerJar out\source-patched\server-source-patched.jar -WaitFor "SOURCE PATCH PROOF ok=true side=server"
```

`run-client.ps1` copies `examples\proof\fixture-world` to
`work\game-26.3\saves\WeaverSourcePatchProofFixture` and calls:

```powershell
tools\launcher\launch.ps1 -Side client ... -IntegratedServer -QuickPlayWorld WeaverSourcePatchProofFixture -PatchedClientJar out\source-patched\client-source-patched.jar -WaitFor "SOURCE PATCH PROOF ok=true side=client"
```

Set `WEAVER_CLIENT_WORLD` to use a different existing world. The fixture copy is
removed and recreated before every attempt; the source fixture is never
modified.

The client runner makes up to three attempts. Attempts two and three set
`JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1` because this host can hit a
documented C2 JIT or graphics-driver crash while loading the world. The retry
is host hardening, not part of the source-patch contract. A successful attempt
prints `SOURCE PATCH PROOF CLIENT ATTEMPT number=<n>` followed by
`SOURCE PATCH PROOF CLIENT ok=true`.

## Expected evidence

The loader is expected to report the source-only artifact as unpatched. That is
correct: the source patch adds a member but does not create the binary patcher's
`net.weaver.patched.PatchedGame` marker class. The launcher flags are generic.

```text
[weaver] GAME PATCH level=0 artifact=unpatched action=runtime-only
```

Runtime markers, one per side:

```text
SOURCE PATCH PROOF ok=true side=server value=source:minecraft:stonex1
SOURCE PATCH PROOF ok=true side=client value=source:minecraft:stonex1
```

Script markers:

```text
SOURCE PATCH PROOF SERVER ok=true
SOURCE PATCH PROOF CLIENT ok=true
SOURCE PATCH PROOF RUN ok=true server=ok client=ok
```

A failed runtime check prints a single-line marker and the run script exits
non-zero:

```text
SOURCE PATCH PROOF ok=false side=client reason=...
SOURCE PATCH PROOF CLIENT ok=false
```

The client run rejects any failure marker, including one from the integrated
server, even if the client-side marker passed.

## What each check does

Server, on `ServerStartedEvent`:

```java
ItemStack stack = new ItemStack(Items.STONE);
String probe = stack.weaver$sourcePatchProbe();
```

Client, once per client when `Minecraft.getInstance().player` is non-null,
through a client tick listener registered from `onClientSetup`:

```java
ItemStack stack = new ItemStack(Items.STONE);
String probe = stack.weaver$sourcePatchProbe();
```

Both sides pass only when `probe` is non-null, starts with `source:`, contains
`x1`, and contains the stone item representation. The successful marker prints
the actual value.

## Notes

- `out/` is ignored. Neither the source-patched jars nor the game jars are
  committed or redistributed.
- The source-patched jar is still Minecraft code. Generate it locally from a
  legally obtained copy; do not ship it.
- The launcher, not this example, decides how a source-patched jar replaces the
  original jar at runtime. The flags above are the contract.
- The build fails closed when `tools\sourcepatcher\source-patch.ps1` or either
  expected output jar is missing; it never substitutes a fake jar.
