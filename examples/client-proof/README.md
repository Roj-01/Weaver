# Weaver client proof

This proof exercises the Weaver 2.2 client and command APIs on a real Minecraft
26.3 client and dedicated server. It is a proof mod, not a starter template.

## What it proves

1. A `KeyMappingSpec` registered through `ClientRegistry.registerKeyMapping`
   activates on a simulated `F6` click, runs its action, and opens a screen
   through a registered `ScreenFactory`.
2. A HUD layer registered with `ClientRegistry.registerHudLayer` renders a
   marker and draws `Weaver` with `GuiGraphicsExtractor.text`.
3. A `TooltipProvider` appends `Weaver client proof tooltip` to the list
   returned by `Screen.getTooltipFromItem`.
4. A permission-gated `weaverclientproof gate` command executes for an
   explicitly allowed source, is rejected for an explicitly denied source, and
   executes through the `wcp` alias.
5. A dedicated-server run does not initialize client classes:
   `CLIENT PROOF SERVER COMPONENT ok=true client_api=false` is printed, and no
   HUD, screen, key-mapping, or client-setup marker appears.
6. The render expansion is exercised on the client: a `HudRenderListener`
   receives `POST_HUD`, a `ScreenRenderListener` receives a `ProofScreen` `PRE`
   phase, a `HandRenderListener` sees a per-hand `PRE` callback, a
   `FogRenderListener` mutates a fog field once, and `BlockColorRegistry`
   registers a grass tint for `short_grass`. These markers are diagnostic; the
   final `CLIENT PROOF RESULT` does not depend on them.
7. The built-in `ClientCommandTreeMixin` records the server command tree after
   `ClientPacketListener#handleCommands`. The client half waits for
   `CommandRegistry.treeSnapshot().source()` to become `client` and prints
   `CLIENT PROOF COMMAND TREE ok=true source=client names=<n>`. The marker is
   part of `ProofState.clientOk()` and the client result's missing list.
8. The mod registers a `weaverclientproof:ping`/`pong` channel pair. On
   `NetworkConnectedEvent` the client sends `ping`; the server replies with
   `pong:ping`; the client prints
   `CLIENT PROOF NETWORK ok=true value=pong:ping`. This proves real
   client-to-server and server-to-client payload delivery through the bridge.
9. The mod registers a `RUNTIME` capability. Ten server ticks after
   `PlayerJoinEvent` the server sets it on the `ServerPlayer`; the capability
   channel sends it to the verified session; the client resolves the holder by
   UUID and prints `CLIENT PROOF CAPABILITY SYNC ok=true`. This marker is also
   part of `ProofState.clientOk()`.

The command proof runs on the server thread from `onServerTick` after
`CommandRegistry.dispatcher()` has a real tree. It never executes from
`onInitialize`.

## Build and run

From the repository root:

```powershell
cd examples\client-proof
.\build.ps1
.\run-server.ps1
.\run-client.ps1
.\run-all.ps1
```

`run-client.ps1` copies `examples/proof/fixture-world` to
`work/game-26.3/saves/WeaverClientProofFixture` and starts the client with
`-IntegratedServer` and `-QuickPlayWorld`. Set `WEAVER_CLIENT_WORLD` to use a
different world. `run-server.ps1` uses port `25567` so it does not clash with
other proofs. The client waits up to 300 seconds; the server waits up to 180
seconds. `run-all.ps1` builds, runs the server, then the client, and prints
`CLIENT PROOF RESULT server=ok client=ok`.

The build requires JDK 26, a game directory with `classpath.txt`, the agent jar
and agent lib under `core/build/agent`, and an `api-game` jar. It mirrors
`examples/hooks-proof/build.ps1` and writes `out/client-proof.jar` plus
`out/mods/client-proof.jar`.

## Expected markers

Build:

```text
CLIENT PROOF BUILD ok=true jar=...\out\client-proof.jar mods=...\out\mods
```

Dedicated server:

```text
CLIENT PROOF COMMAND REGISTERED ok=true
CLIENT PROOF INIT side=server
CLIENT PROOF COMMAND EXECUTED
CLIENT PROOF COMMAND allowed ok=true
CLIENT PROOF COMMAND denied ok=true
CLIENT PROOF COMMAND EXECUTED
CLIENT PROOF COMMAND alias ok=true
CLIENT PROOF SERVER COMPONENT ok=true client_api=false
CLIENT PROOF SERVER RESULT ok=true side=server
```

The server runner additionally checks that the log has no
`CLIENT PROOF HUD`, `CLIENT PROOF SCREEN`, `CLIENT PROOF KEYBIND`,
`CLIENT PROOF HAND RENDER`, `CLIENT PROOF FOG`, or `CLIENT PROOF BLOCK COLOR`
marker. The command that is denied never prints
`CLIENT PROOF COMMAND EXECUTED`.

Client with an integrated server:

```text
CLIENT PROOF COMMAND REGISTERED ok=true
CLIENT PROOF INIT side=client
CLIENT PROOF CLIENT SETUP ok=true
CLIENT PROOF COMMAND EXECUTED
CLIENT PROOF COMMAND allowed ok=true
CLIENT PROOF COMMAND denied ok=true
CLIENT PROOF COMMAND EXECUTED
CLIENT PROOF COMMAND alias ok=true
CLIENT PROOF COMMAND TREE ok=true source=client names=<n>
CLIENT PROOF NETWORK ok=true value=pong:ping
CLIENT PROOF CAPABILITY SYNC ok=true
CLIENT PROOF KEYBIND REGISTERED ok=true
CLIENT PROOF TOOLTIP ok=true
CLIENT PROOF HUD ok=true
CLIENT PROOF HUD STAGE ok=true stage=post_hud
CLIENT PROOF SCREEN PHASE ok=true phase=pre
CLIENT PROOF HAND RENDER ok=true hand=MAIN_HAND
CLIENT PROOF FOG ok=true
CLIENT PROOF BLOCK COLOR REGISTERED ok=true block=short_grass
CLIENT PROOF KEYBIND ok=true
CLIENT PROOF SCREEN ok=true
CLIENT PROOF RESULT ok=true side=client
```

The order of the client markers can vary. The command proof may finish before
the first HUD frame. The client runner fails if the log contains
`CLIENT PROOF RESULT ok=false`.

## Server safety evidence

The mod is built with side `both`, but `onClientSetup` is only called on a client
process. The mixin config declares only client targets and is skipped by the
Mixin engine on a dedicated server. The actual server log contains:

```text
MIXIN CONFIG SKIP ok=true mod=weaverclientproof config=client-proof.mixins.json reason=optional-target-absent targets=net.minecraft.client.gui.Hud,net.minecraft.client.gui.screens.Screen action=skipped
MIXIN INDEX ok=true targets=0 mixins=0
```

That is the `"required": false` path: the engine checks each declared target
with `classBytes` before registering the config and skips the whole config when
a target is absent. No separate client-only mixin config was needed. The proof
also verifies `client_api=false` by attempting a non-initializing
`Class.forName("net.minecraft.client.Minecraft")` on the dedicated server, and
the server runner asserts that no HUD, screen, or key-mapping marker appears.

## Mixin configuration

`src/main/resources/client-proof.mixins.json`:

```json
{
  "required": false,
  "package": "net.weaver.clientproof.mixin",
  "compatibilityLevel": "JAVA_25",
  "injectors": {
    "defaultRequire": 1
  },
  "mixins": [
    "HudMixin",
    "ScreenMixin"
  ]
}
```

- `HudMixin` injects at `TAIL` of
  `Hud.extractRenderState(GuiGraphicsExtractor, DeltaTracker)` and calls
  `CoreClientHooks.renderHud(graphics)`.
- `ScreenMixin` injects at `RETURN` of the static
  `Screen.getTooltipFromItem(Minecraft, ItemStack)` and calls
  `CoreClientHooks.appendTooltip(...)` with the returned list.
- The config is `required false` because the targets are client-only. On a
  dedicated server the engine reports `optional-target-absent` and arms no
  mixins.
- `defaultRequire` is `1`: a client target that exists but does not receive the
  injection is a failure, not a silent skip.
- `compatibilityLevel` is `JAVA_25`, the level the loader's Mixin bridge uses.

The HUD target is now injected by the built-in `HudLayerMixin` in `api-game`;
this proof intentionally keeps its own HUD mixin to exercise the manual
`CoreClientHooks` compatibility path as well. Real mods should not install a
second HUD mixin because both paths reach the same backend. The tooltip target
remains a mod integration request in this proof. The HUD and tooltip code paths
are otherwise the real `ClientRegistry`/`CoreClientHooks` paths.

## Limitations

- HUD delivery is built into the loader when `api-game` is present; this proof
  also keeps a manual HUD mixin. Tooltip delivery still comes from the proof
  mixin. The loader hook targets are listed in `docs/client.md`.
- Model and block/item color registration are not implemented in 2.2. Runtime
  textures are available through `DynamicTextureHandle`; this proof does not
  claim model or color support.
- The client command tree is synchronized by the built-in
  `ClientCommandTreeMixin`. The proof checks the `client` snapshot after joining
  the integrated server. The command-execution half still runs directly on the
  integrated server dispatcher; it does not send chat commands through the
  network.
- The permission proof uses explicit in-memory decisions on two
  `CommandSourceStack` values. It does not test a proxy or vanilla permission
  forwarding.
- The dedicated server has no client class, so the client half of the proof is
  intentionally absent there. The server marker proves the absence rather than
  a client path.
- A full client run is sensitive to the host graphics driver and offline
  authentication. The proof markers are deterministic once the integrated
  server loads; rerun the client script if the host driver crashes before the
  first marker.

## Files

- `src/main/java/net/weaver/clientproof/ClientProofMod.java` - entrypoint.
- `src/main/java/net/weaver/clientproof/ClientProof.java` - command
  registration, server-tick command proof, server result.
- `src/main/java/net/weaver/clientproof/ClientProofClient.java` - client
  registration, key mapping, simulated click, tooltip check, command-tree
  snapshot check, client result.
- `src/main/java/net/weaver/clientproof/ProofState.java` - marker state.
- `src/main/java/net/weaver/clientproof/ProofScreen.java` - proof screen.
- `src/main/java/net/weaver/clientproof/mixin/*` - manual HUD compatibility and
  tooltip hooks.
- `src/main/resources/weaver.mod.json`,
  `src/main/resources/client-proof.mixins.json` - descriptor and mixin config.
- `build.ps1`, `run-server.ps1`, `run-client.ps1`, `run-all.ps1` - build and
  proof scripts.
