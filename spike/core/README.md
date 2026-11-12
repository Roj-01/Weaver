# Core spike

This spike exercises the new loader core without Minecraft:

- `weaver.mod.json` v2 discovery;
- system classloader append;
- Mixin arming and injection;
- access transformer widening a private field;
- the six lifecycle phases;
- client and dedicated-server paths.

## Run

```powershell
.\gradlew.bat :core:agentJar
cd spike\core
.\build.ps1
```

Expected last line:

```
CORE SPIKE RESULT client=ok server=ok
```

## Evidence

The run prints:

```
MIXIN APPLIED ok=true ... target=net.example.game.GameTarget sites=1
MIXIN TRANSFORM ok=true target=net.example.game.GameTarget ...
[weaver] MOD proof PROOF AT ok=true secret=7
PROOF PHASE initialize
PROOF PHASE register
PROOF PHASE common
PROOF PHASE client
PROOF PHASE server
PROOF PHASE complete
PROOF MIXIN hit=1
SPIKE RESULT ok=true mode=client ...
SPIKE RESULT ok=true mode=server ...
```

`ProofBlock` is compiled against a stub where `GameTarget.secret` is public and
runs against the real class where it is private. The access transformer widens
the field before the class is defined, so `readSecret()` works.

The mod is loaded by the new `LoaderAgent`; the Mixin engine and the lifecycle
transforms are the filtered versions in this repository.
