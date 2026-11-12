# Weaver network proof

This directory contains a **standalone two-process TCP proof** of the Weaver
network transport and API. It is deliberately not a Minecraft game-bridge proof.

The proof starts one real `NetworkServer` process on an ephemeral loopback port
and runs two separate client JVMs against it:

- `ProofClient` completes a real handshake, reaches `PLAY`, sends a serverbound
  PING, and verifies the clientbound PONG, its payload, its handler thread, and
  the handshake metadata.
- `ProofMismatch` connects with protocol version 2 against a server at protocol
  version 1 and verifies the exact rejection reason.

There is no in-process or fake transport: every check crosses a real TCP socket
on `127.0.0.1`.

## What this does not prove

The Minecraft 26.3 bridge code exists in `api-game`, but this directory's
standalone proof does not load a game. The core transport proof covers the
Weaver TCP transport, handshake, payload dispatch, event bus, and safety limits.
The optional bridge contract proof covers the real `weaver:bridge` envelope
codec and `BridgeSession` state machine over an in-memory transport. Neither
proof shows that a Minecraft client and dedicated server exchange packets.

The in-game round trip is a separate plan with expected markers in
[GAME-BRIDGE-PROOF.md](GAME-BRIDGE-PROOF.md). No in-game result is recorded here.


## Build

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File examples\network-proof\build.ps1
```

The build script finds JDK 26 through `JAVA_HOME` or
`C:\Program Files\Java\jdk-26.0.1`, reuses
`api/build/libs/api-*.jar` and `core/build/libs/core-*.jar` (running
`.\gradlew.bat :api:jar :core:jar --no-daemon` once if either is absent),
compiles the proof sources, and creates
`examples/network-proof/out/network-proof.jar`.

When `api-game/build/libs/api-game-*.jar` is available (or can be built with
`.\gradlew.bat :api-game:jar --no-daemon`), the script also compiles
`src/bridge/java` and adds `BridgeContractProof` to the proof jar. Expected
markers:

```text
NETWORK PROOF BUILD ok=true jar=...\out\network-proof.jar
NETWORK BRIDGE PROOF BUILD ok=true jar=...\out\network-proof.jar
```

When the artifact is unavailable, the script prints
`NETWORK BRIDGE PROOF BUILD SKIP ok=true reason=api-game-unavailable` and still
builds the core proof.


## Run

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File examples\network-proof\run.ps1
```

The run script starts `ProofServer` as a child process, reads its ephemeral port
from the log, runs `ProofClient` and `ProofMismatch` as separate child processes,
validates their markers and exit codes, and stops the server in a `finally`
block. Logs are written under `examples/network-proof/out/logs/`.

Expected core markers:

```text
NETWORK SERVER READY port=<n>
NETWORK SERVER CHECK ok=true serverbound payload=hello thread=network-proof-server-game
NETWORK SERVER RESULT ok=true checks=1
NETWORK PROOF CHECK ok=true <name>            (six lines)
NETWORK PROOF RESULT ok=true checks=6
NETWORK MISMATCH CHECK ok=true reason=handshake rejected: protocol mismatch: client=2, server=1
NETWORK MISMATCH RESULT ok=true
NETWORK PROOF RUN ok=true server=ok client=ok mismatch=ok
```

When the bridge contract proof was built, `run.ps1` first runs it and adds:

```text
NETWORK BRIDGE PROOF CHECK ok=true <name>     (nine lines)
NETWORK BRIDGE PROOF RESULT ok=true checks=9
```

Otherwise it prints
`NETWORK BRIDGE PROOF SKIP ok=true reason=bridge-contract-not-built`.

`NETWORK SERVER CHECK ...` is written by `ProofServer` to
`out/logs/server.log`; `run.ps1` does not echo it. `run.ps1` echoes the final
console line `NETWORK PROOF RUN ok=true server=ok client=ok mismatch=ok` after
the server, client, and mismatch stages pass.

## Unit tests

The API, core, and bridge tests live next to the regular module tests:

```powershell
.\gradlew.bat :api:test --no-daemon
.\gradlew.bat :core:test --no-daemon
.\gradlew.bat :api-game:test --no-daemon
```

They use JUnit 5, real loopback sockets with ephemeral ports, bounded waits, and
cleanup in `finally` blocks. The bridge tests use the real envelope codec and
the real session state machine; the game-payload test uses the 26.3 class path.

## Limitations

- The core proof uses the plain TCP transport only; it does not exercise a game
  network stack, Minecraft packet codecs, or the 26.3 hook targets.
- The bridge contract proof uses an in-memory transport and the game-free
  session; it does not exercise Minecraft packet framing or Mixin application.
- The protocol mismatch case is the only handshake rejection exercised by the
  standalone proof; mod/version/policy rejections are covered by unit tests.
- The proof intentionally keeps the server accepting for a bounded linger after
  the PING check so the mismatch client can connect, then shuts down.
- No in-game round-trip result is recorded. See
  [GAME-BRIDGE-PROOF.md](GAME-BRIDGE-PROOF.md) for the plan and markers.
