# Minecraft 26.3 bridge proof plan

This is a **plan and marker contract** for the in-game client/server payload
round trip. It is not a recorded result. The game-free contract proof in
`src/bridge/java` and the standalone TCP proof run without Minecraft; neither
replaces this test.

## Prerequisites

1. `api-game/build/libs/api-game-*.jar` exists.
2. `LoaderAgent` has the two integration insertions from
   [docs/network.md](../../docs/network.md#loader-integration):
   `NetworkBridgeInstall.arm(...)` after `engine.boot(...)` and before the
   Weaver mixin transformer, then `NetworkBridgeInstall.activate(...)` after the
   transformer is installed and before game main.
3. The run logs contain:
   - `[weaver] NETWORK BRIDGE mixin config armed resource=weaver.network.mixins.json`
   - `[weaver] NETWORK BRIDGE installed=true armed=true resource=true classes=true`
   - `MIXIN CONFIG ok=true mod=weaver-network config=weaver.network.mixins.json`
4. The dedicated server and client use the same Weaver protocol version,
   Weaver API version, and game version.

The proof must run on a client with an integrated or dedicated server. A
single-player world exercises the integrated server but still has two
`Connection` objects and two bridge sessions; it is acceptable, but a dedicated
server is the stronger case.

## Scenario

Register two channels on both endpoints before connecting:

- serverbound `proof:ping/v1`, handler replies clientbound `proof:pong/v1` with
  the same string plus `-pong`;
- clientbound `proof:pong/v1`, client handler prints a marker and records the
  payload.

Then connect one client and send `hello` on `proof:ping/v1` from a
`NetworkConnectedEvent` listener.

Use these two custom `NetworkFeature` state checks as preconditions:

- `NetworkFeature.isInstalled() == true`
- `NetworkFeature.blocker().isEmpty() == true`

## Expected markers

Core install and codec:

```text
[weaver] NETWORK BRIDGE installed=true armed=true resource=true classes=true
[weaver] NETWORK BRIDGE CODEC verified id=weaver:bridge
```

Server handshake and play:

```text
[weaver] NETWORK BRIDGE session side=SERVER ...
[weaver] NETWORK BRIDGE hello side=SERVER ...
[weaver] NETWORK BRIDGE handshake side=SERVER ... player=<name> ...
[weaver] NETWORK BRIDGE connected side=SERVER ...
```

Client handshake and play:

```text
[weaver] NETWORK BRIDGE session side=CLIENT ...
[weaver] NETWORK BRIDGE hello side=CLIENT ...
[weaver] NETWORK BRIDGE request side=CLIENT ...
[weaver] NETWORK BRIDGE handshake side=CLIENT ...
[weaver] NETWORK BRIDGE connected side=CLIENT ...
```

Payload round trip:

```text
GAME BRIDGE PROOF CHECK ok=true ping
GAME BRIDGE PROOF CHECK ok=true pong
GAME BRIDGE PROOF CHECK ok=true player
GAME BRIDGE PROOF CHECK ok=true server-installed
GAME BRIDGE PROOF CHECK ok=true client-installed
GAME BRIDGE PROOF RESULT ok=true checks=5
```

The marker names are a contract for a future runner; no runner is wired in this
tree and no in-game output has been recorded.

## Failure interpretation

| Symptom | Likely cause |
|---|---|
| `NETWORK BRIDGE skipped reason=not-installed` | `NetworkBridgeInstall.activate(...)` is missing or ran before the Mixin transformer. |
| `NETWORK BRIDGE CODEC missing id=weaver:bridge` | The codec `@ModifyArg` injectors did not apply, or a packet class initialized before the config was armed. Check `MIXIN CONFIG` and `MIXIN APPLIED` markers. |
| `hello side=SERVER` but no `request side=CLIENT` | The client did not reach the `handleLoginFinished` tail, the client config was rejected, or the client bridge is not installed. |
| `handshake rejected: ...` | Version, game version, Weaver version, mod list, or policy mismatch. The reason is exact. |
| `payload phase mismatch` | A payload was sent while the carrier was in a different phase; send `PLAY` channels only after the connected event. |
| `unknown channel: ...` | The receiving registry has no registration for the inbound direction and phase. Both endpoints must register both directions they use. |
| `inbound byte budget exhausted` | The session exceeded 8 MiB of bridge input. This budget does not refill. |
| `inbound queue overflow` | More than 64 envelopes or 1 MiB arrived before the connected event. |

## Limits to verify

- Clientbound bridge body: at most `1_047_552` bytes.
- Serverbound bridge body: the same registered-codec limit; the 26.3
  `32_767` fallback applies only to unknown `DiscardedPayload` ids.
- Envelope channel payload: at most `1_043_456` bytes.
- Handshake body: at most `131_072` bytes.
- Pre-play queue: 64 envelopes or 1 MiB.
- Session inbound budget: 8 MiB.

## Evidence rules

- Do not write `GAME BRIDGE PROOF RESULT ok=true` unless both sides print their
  `connected` marker and both `ping`/`pong` markers are observed.
- Record the client and server logs together; a client-only log cannot prove the
  server received the payload.
- Keep the exact `CODEC verified` line. If it is absent, the codec registration
  is unproven even if the handshake works in one direction.
- A skipped run must be reported as skipped, not passed.
