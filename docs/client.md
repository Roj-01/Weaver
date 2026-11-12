# Client API

Weaver 2.2 adds a client extension API for key mappings, screens, HUD layers,
item tooltips, client ticks, client resource reloads, client chunk events, and
world rendering. The render expansion adds first-person hand hooks, level render
stages, screen phases, HUD stages, block tint sources, mutable fog/sky/camera
state, field-of-view modifiers, entity renderer providers, entity render
layers, and model layer definitions. The game-free API lives in
`net.weaver.api.client`; the reflection-backed implementation lives in
`net.weaver.core.client`; the game-typed render, model, entity, color, and
texture helpers live in `net.weaver.api.game.client`. The API is additive: it
does not change `ModContext`, `WeaverMod`, `EventBus`, the registry API, or the
build.

The [event bus](events.md) reference describes `ClientTickEvent` and
`ResourceReloadEvent`, which this API reuses. The [hook points](hooks.md) page
describes the loader lifecycle, the injected HUD, and the built-in client
feature mixins. The
[command API](commands.md) page describes the server-side command and
permission model.

## Status

- Client-only. The public types are in `api`; the working backend is in `core`.
- Additive in 2.2. No existing 2.1 API was changed.
- `api` never references `net.minecraft.*`; game objects cross the boundary as
  `Object`.
- `core` bridges to the game entirely through reflection.
- No server state is created. A dedicated server can load the API classes and
  call `isAvailable()`; the first operation that needs a backend refuses to
  load the core client backend when `net.minecraft.client.Minecraft` is absent.
- Existing events are reused. `ClientTickEvent.Pre` and `ClientTickEvent.Post`
  drive tick listeners, `ResourceReloadEvent` drives resource reload
  listeners, and screen changes remain observable through the existing
  `ScreenOpenEvent`/`ScreenCloseEvent` hooks. The 2.2 client API adds no new
  client event types; the command event is `CommandTreeSyncEvent`,
  described in [commands.md](commands.md).
- The client command tree is recorded by the built-in
  `ClientCommandTreeMixin`. It injects at the tail of
  `ClientPacketListener#handleCommands(ClientboundCommandsPacket)`, reads
  `ClientPacketListener#getCommands()`, and calls
  `CommandRegistry.recordTreeSync(dispatcher, names, "client")`. The mixin is
  listed in the `client` array of `weaver.client.mixins.json` and every failure
  is reported once as `[weaver] CLIENT COMMAND TREE FAILED`.
- `examples/client-proof` checks the snapshot after joining its integrated
  server and prints
  `CLIENT PROOF COMMAND TREE ok=true source=client names=<n>`. The marker is
  part of the client result and its missing list.
- HUD rendering is injected by the built-in client feature configuration:
  `HudLayerMixin` forwards `Hud#extractRenderState` to
  `ClientRegistry.renderHud(HudRenderContext)` with the frame's partial tick.
  A mod only registers a `HudLayer`; it does not need its own Mixin. The same
  tail dispatches `HudRenderStage.POST_HUD`, and `HudStageMixin` and
  `GuiStageMixin` add the remaining HUD stages.
- Item tooltips are injected by the built-in `TooltipMixin`. It appends
  provider lines to `Screen#getTooltipFromItem` and calls the game-free
  `ClientTooltipHook` bridge, which the core backend binds to
  `CoreClientHooks.appendTooltip` when the backend is installed. A mod only
  registers a `TooltipProvider`.
- Client chunk events are injected by the built-in `ClientChunkMixin`. It
  dispatches dedicated load, unload, and server-confirmed block-update events;
  no polling is involved. Listeners register through
  `ClientRegistry.registerChunkListener`.
- World rendering is injected by the built-in `WorldRenderMixin` at
  `LevelRenderer#addMainThreadGizmos`. Listeners register through
  `WorldRenderRegistry.register` in `api-game` and draw through the 26.3 gizmo
  system. This gizmo registry remains the drawing path; the separate level
  stage registry reports render structure and does not draw.
- First-person hands are injected by `HandRenderMixin` and `HandStateMixin`.
  `HandRenderRegistry` supports per-hand cancellation, ordered pre/post
  callbacks, and render-state mutation.
- Screen phases are injected by `ScreenRenderMixin` at the general `Screen`
  target. `ScreenRenderRegistry` adds pre, cancellable background, and post
  phases for every screen that uses the base implementation.
- HUD stages are injected by `HudStageMixin`, `GuiStageMixin`, and the existing
  `HudLayerMixin`. `HudRenderRegistry` adds `PRE_HUD`, `CROSSHAIR`, `HOTBAR`,
  `POST_HUD`, and `POST_GUI`.
- Block tint sources are queued through `BlockColorRegistry` and applied by
  `BlockColorMixin` at `BlockColors#createDefault`. Item tints are data-driven
  in 26.3 and are explicitly not provided.
- `FogRenderMixin`, `SkyRenderMixin`, and `CameraRenderMixin` expose the
  mutable `FogData`, `SkyRenderState`, and `CameraRenderState` values.
  `FovModifierRegistry` composes ordered multiplicative FOV factors.
- `EnvironmentRenderers` replaces a dimension type's sky, clouds, and rain and
  snow (classic and improved transparency) and its rain tick: the registered
  renderer is asked first, and vanilla skips the step when it returns `true`
  (`EnvironmentRenderMixin`, `LevelDrawMixin`, `RainTickMixin`). The
  `AFTER_CLOUDS` and `AFTER_WEATHER` draw stages still fire.
- `EntityRendererMixin` merges registered entity renderer providers into a new
  map at `EntityRenderers#createEntityRenderers`; `EntityLayerMixin` adds
  registered render layers at the `LivingEntityRenderer` constructor return.
- `ModelLayerMixin` merges registered `LayerDefinition`s into
  `LayerDefinitions#createRoots`; registration must happen before the model set
  is baked.
- Runtime textures are supported through the game-typed
  `net.weaver.api.game.client.texture.DynamicTextureHandle` helper in
  `api-game`, including sampling options, partial uploads, and optional
  mipmaps.

## Lifecycle and threading

Register client features during `onClientSetup`, which runs on the client thread
from the `Minecraft` constructor before the client creates its `Options`. Do not
register from `onInitialize` unless the process is a client and you have
confirmed the client thread; the backend checks the thread on every registration
call.

The client API has two threading contracts:

- `ClientRegistry.install(ClientBackend)`, `uninstall()`, `reset()`,
  `isAvailable()`, and `registrationCount()` may be called from any thread.
  `backend()` itself may be called from any thread, but the result must only be
  used for client operations on the client thread.
- Registration, screen, HUD, tooltip, and chunk-listener operations must run on
  the client/render thread. The core backend calls
  `Minecraft.getInstance().isSameThread()` and throws `ClientThreadException`
  when the check fails. This covers key mappings, screen factories, HUD layers,
  tooltip providers, chunk listeners, tick listeners, resource reload
  listeners, `openScreen`, `renderHud`, `tooltipLines`, and the chunk event
  dispatch methods.
- Build `KeyMappingSpec` values on the client thread. The record is immutable,
  but the registration contract and the backend's work are client-thread
  operations. Build the spec in `onClientSetup` or in a client tick listener,
  then register it there.
- Key polling happens on `ClientTickEvent.Pre`. The core backend polls every
  active key mapping before it invokes the facade's pre-tick listeners.
- HUD layer rendering happens on the render thread through the built-in
  `HudLayerMixin`. Tooltip appending happens on the render thread through
  `TooltipMixin`. Chunk events happen on the client thread through
  `ClientChunkMixin`.
- World render listeners run on the render thread through `WorldRenderMixin`,
  once per rendered frame. Register them during `onClientSetup` or from the
  client thread; do not call `WorldRenderRegistry.render` from mod code.
- The game-typed render registries dispatch on the render thread from their
  built-in mixins: `HandRenderRegistry`, `LevelRenderRegistry`,
  `ScreenRenderRegistry`, `HudRenderRegistry`, `BlockColorRegistry`,
  `FogRenderRegistry`, `SkyRenderRegistry`, `CameraRenderRegistry`,
  `FovModifierRegistry`, `EntityRendererRegistry`, `EntityLayerRegistry`, and
  `ModelLayerRegistry`. Their registration methods are thread-safe and may be
  called from any thread. Do not call a `dispatch` or `merge` integration entry
  point from mod code.
- Register block colors and model layers during `onClientSetup`: they are
  consumed while the client builds its color table and model set. A block color
  registered after `BlockColors#createDefault`, or a model layer registered
  after `LayerDefinitions#createRoots`, is not visible until the relevant
  construction runs again (a client restart for colors, a resource reload for
  models). Entity renderer and entity layer registrations are consumed during
  the next resource reload.
- Resource reload listeners are called on the client thread. The core backend
  only forwards `ResourceReloadEvent` events whose side is `CLIENT`; the
  resulting `ResourceReloadContext.client()` is `true`.

The backend isolates listener failures. One failing HUD layer, tooltip
provider, chunk listener, tick listener, reload listener, screen factory, or key
action is logged and does not stop the other callbacks or the tick/render loop.
The log prefixes are `CLIENT HUD LAYER FAILED`, `CLIENT TOOLTIP PROVIDER FAILED`,
`CLIENT CHUNK LISTENER FAILED`, `CLIENT TICK LISTENER FAILED`,
`CLIENT RELOAD LISTENER FAILED`, `CLIENT KEY ACTION FAILED`,
`CLIENT KEY MODIFIER FAILED`, and `CLIENT SCREEN OPEN FAILED`. The world render
registry logs `WORLD RENDER LISTENER FAILED` once per listener id. The expansion
registries log `HAND RENDER LISTENER FAILED`, `HAND RENDER STATE LISTENER
FAILED`, `LEVEL RENDER LISTENER FAILED`, `SCREEN RENDER LISTENER FAILED`, `HUD
RENDER LISTENER FAILED`, `FOG RENDER LISTENER FAILED`, `SKY RENDER LISTENER
FAILED`, `CAMERA RENDER LISTENER FAILED`, `FOV MODIFIER FAILED`, `ENTITY
RENDERER FAILED`, `ENTITY LAYER FAILED`, and `MODEL LAYER HOOK FAILED` with a
stable listener or registration id. Hook setup failures use a matching
`... HOOK FAILED` line and are reported once per process.

## Key mappings

`KeyMappingSpec` is an immutable record:

```java
public record KeyMappingSpec(
        String id,
        String modId,
        String translationKey,
        String categoryId,
        int keyCode,
        boolean mouse,
        int modifiers,
        int order,
        String screenFactoryId)
```

The fields mean:

| Field | Meaning |
|---|---|
| `id` | Unique mapping id within the mod; matches `[a-z0-9_.-]+`. |
| `modId` | Owning mod id; matches `[a-z0-9_.-]+`. |
| `translationKey` | Non-blank mapping name key. Defaults to `"key." + modId + "." + id`. |
| `categoryId` | Non-blank category id. Defaults to `"misc"`. |
| `keyCode` | Non-negative key or mouse-button code. For keyboard mappings this is a GLFW key code such as `GLFW.GLFW_KEY_F6` (`295`). |
| `mouse` | `true` to use `InputConstants.Type.MOUSE`, `false` for `KEYBOARD`. |
| `modifiers` | Modifier mask applied by the poll path. See the modifier semantics below. |
| `order` | Display order; lower values are shown first. |
| `screenFactoryId` | Optional screen factory id opened by the one-argument registration helper. |

Create a spec with the builder or the `key` convenience factory:

```java
KeyMappingSpec spec = KeyMappingSpec.builder("examplemod", "open_screen",
                GLFW.GLFW_KEY_F6)
        .translationKey("key.examplemod.open_screen")
        .category("examplemod")
        .mouse(false)
        .order(100)
        .screenFactory("examplemod:screen")
        .build();

KeyMappingSpec defaults = KeyMappingSpec.key("examplemod", "ping", GLFW.GLFW_KEY_F7);
```

`builder(modId, id, keyCode)` applies the default translation key and category.
The builder also exposes `translationKey(String)`, `category(String)`,
`mouse(boolean)`, `modifiers(int)`, `order(int)`, `screenFactory(String)`, and
`build()`. `key(modId, id, keyCode)` is exactly
`builder(modId, id, keyCode).build()`.

Registration rules:

- `ClientRegistry.registerKeyMapping(KeyMappingSpec, Runnable)` registers the
  mapping and the action to run when it activates. The two-argument overload is
  the general form.
- `ClientRegistry.registerKeyMapping(KeyMappingSpec)` is the convenience form.
  It requires a non-null `screenFactoryId` and is equivalent to registering an
  action that calls `ClientRegistry.openScreen(spec.screenFactoryId())`. It
  throws `IllegalArgumentException` when the spec has no screen factory id.
- The backend identifies a mapping as `spec.id() + ":" + spec.modId()`. A
  duplicate id throws `ClientException` with
  `duplicate key mapping id: <id>:<modId>`.
- The action runs on the client thread when `KeyMapping.consumeClick()` reports
  a click. Exceptions from the action are isolated and logged.
- Unregistering the returned `ClientRegistration` removes the mapping from
  polling immediately, removes it from `Options.keyMappings` on a best-effort
  basis, removes it from the game's static mapping maps on a best-effort basis,
  and calls `KeyMapping.resetMapping()`. A failure in any removal step is
  logged and does not throw.

The core adapter creates the real key mapping with
`new KeyMapping(translationKey, InputConstants.Type, keyCode, Category, order)`.
The `KeyMapping` constructor itself adds the mapping to the game's static
`ALL` and `MAP` collections, which is the functional path for polling. The
adapter also appends the mapping to the `Options.keyMappings` array through
reflection. The append is retried from the key poll, so a mapping registered in
`onClientSetup` (before `Options` exists) still reaches the controls screen once
the client starts ticking. The append is best-effort and can fail on hardened
JVMs with module access restrictions. A failure is logged as
`[weaver] CLIENT ADAPTER options.keyMappings FAILED ...` and does not prevent the
mapping from working through the constructor's static registration.

Category handling:

- `categoryId` `"misc"` uses the game's built-in `KeyMapping.Category.MISC`.
- Any other category id is registered with
  `KeyMapping.Category.register(Identifier.fromNamespaceAndPath(modId, categoryId))`.
  The game derives the category label key from that identifier; in 26.3 this is
  the `key.category.<namespace>.<path>` form. The `KeyMappingSpec` javadoc
  describes the legacy `key.categories.<categoryId>` shape; the identifier
  registration is what the 26.3 adapter actually performs.
- Categories are cached per mod/category pair for the process.

`modifiers` is applied by the core poll path. The five-argument `KeyMapping`
constructor's final integer is still the display `order`, so the mask is
checked by Weaver after `KeyMapping.consumeClick()` reports a click:

- The click is always consumed, even when the action is skipped.
- The action runs only when every bit in `spec.modifiers()` is present in the
  current keyboard modifier state.
- Extra held modifiers do not block the action. Caps Lock and Num Lock are not
  read by the built-in reader.
- The bit values are the 26.3 `InputConstants.MOD_*` values, exposed as
  `KeyModifiers.SHIFT`, `CONTROL`, `ALT`, `SUPER`, `NUM_LOCK`, and `CAPS_LOCK`.
- A state-read failure is logged once by the adapter and treated as "no
  modifier held", so the action is skipped.
- Modifier matching applies to keyboard and mouse mappings alike.

`KeyModifiers.matches(required, current)` is the pure matcher used by the
backend and can be used by mods that build their own mask.

## Mod list and settings screens

The title screen gets a **Mods** button (the Realms button gives up half its
width), and the pause menu gets one above "Save and Quit". It opens
`ModListScreen`, which lists Minecraft, Weaver, and every loaded mod with a
search box. Selecting a mod shows its version, id, side, and the optional
`description`, `authors`, `license`, and homepage of its `weaver.mod.json`:

```json
{
  "id": "examplemod",
  "description": "Adds machines and pipes.",
  "authors": ["Ana", {"name": "Bo"}],
  "license": "MIT",
  "contact": {"homepage": "https://example.org"}
}
```

These fields are also on `WeaverMods.Mod` (`description()`, `authors()`,
`license()`, `homepage()`).

**Settings.** The **Settings** button (or a double click) opens the mod's
settings screen. Each row is named by the translation
`config.<modid>.<key>` (tooltip `config.<modid>.<key>.tooltip`); without one,
the last part of the key is made readable (`dualWield` becomes "Dual Wield").
Only edited rows are applied on save. A mod with a Weaver configuration ([config](config.md)) gets
one generated with no code (`ConfigScreen`):

- One row per value: an on/off button for booleans, a cycling button for enums,
  and a text box for numbers, strings, and lists (comma-separated).
- A text box turns red while its text is invalid (out of range, not a number,
  too long), and its tooltip gives the reason and the value's description.
- **Save** applies and writes every value only when all are valid. **Reset**
  puts every row back to its default. **Cancel** leaves the file as it was.
- While a server's synced values are in effect, the rows are read-only.

A mod with its own screen registers it, and that wins over the generated one:

```java
ConfigScreens.register("examplemod", parent -> new ExampleSettingsScreen(parent));
```

`ModListScreen.open()` opens the list from code. A failing settings screen
factory is logged once and the generated screen is used instead.

## Client commands

`ClientCommands` runs commands on the client, without the server: they work on
any server, vanilla servers too, and show in the chat suggestions next to the
server's commands.

```java
ClientCommands.register(LiteralArgumentBuilder.<ClientSuggestionProvider>literal("waypoint")
        .then(LiteralArgumentBuilder.<ClientSuggestionProvider>literal("add").executes(context -> {
            Waypoints.addHere();
            ClientCommands.feedback(Component.literal("Waypoint added"));
            return 1;
        })));
```

A command typed in chat whose first word is a client command runs here and is
not sent. Errors show in red in the chat. A client command shadows a server
command with the same name. The client proof registers one, checks it is in
the suggestion tree, and runs it from chat.

## Raw mouse buttons

`ClientMouseButtonEvent` reports every mouse button press and release
(`button()`, `action()`, `modifiers()`):

- `PRE` comes before vanilla handles it (screens, attacking, using). Canceling
  it swallows the click.
- `POST` comes after.

## Screen factories

A `ScreenFactory` creates a screen on demand:

```java
public interface ScreenFactory
{
    String id();

    Object createScreen(Object minecraft, Object parentScreen);
}
```

- `id()` must be non-null and non-blank. Registering a duplicate id throws
  `ClientException` with `duplicate screen factory id: <id>`.
- `createScreen` is called on the client/render thread. `minecraft` is the
  client instance and `parentScreen` is the current screen, or `null` when there
  is no parent.
- Returning `null` cancels the open request.
- An exception from the factory is isolated and logged; it does not throw to
  the caller.
- `ClientRegistry.openScreen(String)` opens the screen for a registered factory.
  A `null` or blank id throws `IllegalArgumentException` at the facade. An
  unknown id logs `[weaver] CLIENT SCREEN UNKNOWN id=<id>` and has no effect.
- Registration, `createScreen`, and `openScreen` all require the client/render
  thread.

The game objects are typed as `Object` so the API module stays game-free. A
client mod casts them to `Minecraft`, `Screen`, and so on. Do not retain the
arguments past the call unless the game object is known to be long-lived.

## HUD layers

A HUD layer is rendered by the HUD hook entry point:

```java
public interface HudLayer
{
    String id();

    int order();

    void render(HudRenderContext context);
}
```

`HudRenderContext` is an immutable snapshot:

```java
public record HudRenderContext(
        Object graphics,
        Object minecraft,
        int width,
        int height,
        float partialTick)
```

- `graphics()` is the game GUI/graphics object. In 26.3 the core hook passes
  `GuiGraphicsExtractor`.
- `minecraft()` is normally the client instance in a live game.
- `width()` and `height()` are the logical HUD size; the constructor rejects
  negative values.
- `partialTick()` is the current frame's partial tick. The built-in
  `HudLayerMixin` passes `DeltaTracker#getGameTimeDeltaPartialTick(false)`.
  `CoreClientHooks.renderHud(Object)` keeps a `0.0F` overload for manually
  wired hooks; use `CoreClientHooks.renderHud(Object, float)` when a partial
  tick is available.

Layers render in ascending `order()`. Equal orders keep registration order
(the backend sorts by `order`, then by the registration sequence). A layer with
a larger order draws on top of a layer with a smaller order.

Rules:

- Register through `ClientRegistry.registerHudLayer(HudLayer)` on the
  client/render thread.
- Duplicate layer ids throw `ClientException` with
  `duplicate hud layer id: <id>`.
- `render` runs on the render thread. Do not mutate game state or the world, do
  not start or stop world tick work, and keep the call short.
- Draw through `context.graphics()` rather than reaching into global state.
- A layer exception is logged as `CLIENT HUD LAYER FAILED id=<id>` and the next
  layer still runs.
- Unregistering removes the layer from future passes.

The HUD entry point is `ClientRegistry.renderHud(HudRenderContext)`. On the
client, `LoaderAgent` arms `api-game`'s `weaver.client.mixins.json`, which
contains `HudLayerMixin`. The mixin injects at the tail of
`Hud#extractRenderState(GuiGraphicsExtractor, DeltaTracker)`, builds a context
with the graphics object, client instance, logical size, and partial tick, and
calls the facade. No mod Mixin is required.

`net.weaver.core.client.CoreClientHooks.renderHud(Object)` and
`renderHud(Object, float)` remain available for manually wired hooks and
proofs. They forward only when a backend is installed; they never install one.
Do not also install a mod HUD mixin that calls them: both paths reach the same
backend and would render every layer twice.

## Runtime textures

`api-game` provides
`net.weaver.api.game.client.texture.DynamicTextureHandle` for client features
that generate pixels at runtime:

```java
DynamicTextureHandle texture = DynamicTextureHandle.create(
        "examplemod", "overlay", 256, 256, TextureOptions.linearClamp());
texture.setPixels(argbPixels);
texture.upload();
texture.uploadRegion(32, 32, 64, 64, regionPixels);
graphics.blit(texture.id(), x, y, x + size, y + size, 0.0F, 1.0F, 0.0F, 1.0F);
texture.close();
```

The helper owns a `NativeImage` and a `DynamicTexture`, writes ARGB pixels, and
registers the texture with the client `TextureManager`. Create, update, upload,
and close on the client/render thread. `close()` releases the texture and is
idempotent, including after the client has shut down.

`TextureOptions` controls sampling:

| Field | Default | Effect |
|---|---|---|
| `linearFiltering` | `false` | Linear min/mag filtering instead of nearest. |
| `clampToEdge` | `false` | Clamp-to-edge addressing instead of repeat. |
| `mipmaps` | `false` | Allocate and maintain a full mip chain. |

`TextureOptions.DEFAULT`, `linearClamp()`, and `linearClampMipmapped()` are the
provided presets.

`uploadRegion(x, y, width, height, int[] argb)` validates the region against the
texture size and the source array, copies the region into the CPU image, writes
it with `CommandEncoder.writeToTexture(texture, image, mipLevel, layer, x, y)`,
and regenerates the mip chain when mipmaps are enabled. `argb` is the region
itself in row-major order (`width * height` entries), not the full texture
image. Validation failures are
`IllegalArgumentException`; a GPU write failure is wrapped in
`IllegalStateException` with the texture id. Partial uploads are intended for
occasional updates, not per-frame full-surface streaming.

Mipmaps are implemented: the handle creates the GPU texture with a full mip
count, generates levels by 2x2 box filtering after `upload()`, and regenerates
them after `uploadRegion`. This costs one CPU downsample pass over the texture
per partial upload. A texture created with mipmaps disabled always reports
`mipLevels() == 1`.

The unit proof for the partial-region math prints `TEXTURE PARTIAL ok` from
`TextureRegionTest`; it does not touch the GPU. The integrator's client proof
should call `uploadRegion` on a live handle and observe the rendered result.

## Tooltip providers

A tooltip provider contributes plain-text lines to an item tooltip:

```java
public interface TooltipProvider
{
    String id();

    void append(TooltipContext context, List<String> lines);
}
```

`TooltipContext` is an immutable snapshot:

```java
public record TooltipContext(
        Object minecraft,
        Object screen,
        Object itemStack,
        List<Object> existingLines)
```

- `minecraft()`, `screen()`, and `itemStack()` may be `null` when the hook has
  no corresponding object.
- `existingLines()` is an unmodifiable defensive copy of the game lines already
  collected. It is never `null`; constructing a context with a `null` list or a
  `null` element throws `NullPointerException`.

Rules:

- Register through `ClientRegistry.registerTooltipProvider(TooltipProvider)` on
  the client/render thread.
- Duplicate provider ids throw `ClientException` with
  `duplicate tooltip provider id: <id>`.
- Providers run in registration order with a mutable `List<String>`. Append
  plain strings without formatting codes. Do not mutate existing entries, do
  not retain the list, and do not retain game objects after the call.
- A provider exception is logged as `CLIENT TOOLTIP PROVIDER FAILED id=<id>`
  and the next provider still runs. `null` lines added by a provider are
  filtered out by the backend.
- `ClientRegistry.tooltipLines(TooltipContext)` returns an unmodifiable copy of
  the collected lines and requires the render thread.
- The built-in `TooltipMixin` injects at the return of
  `Screen#getTooltipFromItem(Minecraft, ItemStack)` and calls the game-free
  `ClientTooltipHook.appendTooltip`. The core backend installs
  `CoreClientHooks.appendTooltip(Object, Object, List<Object>)` as that hook's
  appender when the backend is installed. The core hook builds a context, collects
  provider lines, converts each line to a game `Component` through
  `Component.literal`, and appends it to the supplied game list.
- A `null` return list is ignored. An immutable list is tolerated: the first
  append failure is reported once as `CLIENT TOOLTIP HOOK FAILED` and the
  remaining lines are skipped. The hook never retains the list or any game
  object.
- A failed line conversion is skipped. `CoreClientHooks.appendTooltip` is a
  no-op when no backend is installed or `lines` is `null`.
- `ClientTooltipHook` is the loader bridge used by the built-in mixin. Mods
  should not install or call it directly; register a `TooltipProvider` instead.
- Set `-Dweaver.proof.tooltip=true` to print `TOOLTIP ok` once, the first time
  the mixin appends at least one provider line. The marker is not printed when
  no provider contributes a line.

`Screen.getTooltipFromItem` returns a mutable list in 26.3, so the normal path
appends in place. The immutable case is defensive for mods or future game
versions that return an unmodifiable list.

## Client chunk events

`ClientChunkListener` observes dedicated client chunk and block events:

```java
public interface ClientChunkListener
{
    void onChunkLoad(ClientChunkContext context);

    void onChunkUnload(ClientChunkContext context);

    void onBlockUpdate(ClientBlockContext context);
}
```

`ClientChunkContext` is `(String dimension, int chunkX, int chunkZ)`.
`ClientBlockContext` is `(String dimension, int x, int y, int z)`. The dimension
is the game identifier in `namespace:path` form, for example
`minecraft:overworld`. Both records reject a `null` or blank dimension.

Registration and dispatch:

- `ClientRegistry.registerChunkListener(ClientChunkListener)` registers a
  listener on the client thread and returns a `ClientRegistration` whose id is
  `client-chunk:<sequence>`.
- The same listener instance may be registered only once. A second registration
  throws `ClientException` with `duplicate chunk listener: <class>`. Different
  instances are independent even when they share a class.
- Unregistering removes the listener from future dispatch immediately.
- One failing listener is logged as `CLIENT CHUNK LISTENER FAILED id=<id>` and
  does not stop later listeners.

Integration targets verified against Minecraft 26.3:

| Event | Target | Injection |
|---|---|---|
| Chunk load | `net.minecraft.client.multiplayer.ClientLevel#onChunkLoaded(Lnet/minecraft/world/level/ChunkPos;)V` | `TAIL` |
| Chunk unload | `net.minecraft.client.multiplayer.ClientLevel#unload(Lnet/minecraft/world/level/chunk/LevelChunk;)V` | `HEAD` |
| Block update | `net.minecraft.client.multiplayer.ClientLevel#setServerVerifiedBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)V` | `HEAD` |

The block target is the shared path for single-block and section block-update
packets. Local predictions that the server has not confirmed are not reported.
The block injection is at `HEAD`, before the new state is applied, and the
context carries the position only. Chunk load fires after the chunk replaces its
packet data; chunk unload fires before the level clears the chunk's block
entities and lighting.

The game-typed bridge is
`net.weaver.api.game.client.chunk.ClientChunkHooks`. It converts game values to
the game-free contexts and calls `ClientRegistry.chunkLoaded`,
`chunkUnloaded`, or `blockUpdated`. Those facade dispatch methods are no-ops
until a backend is installed; they never install one. Set
`-Dweaver.proof.chunkEvents=true` to print one `CHUNK EVENTS ok type=load`,
`type=unload`, and `type=block` marker per process.

## World render hook

`api-game` provides a game-typed world render registry:

```java
WorldRenderRegistry.register(WorldRenderListener listener);

public interface WorldRenderListener
{
    String id();

    default int order()
    {
        return 0;
    }

    void render(WorldRenderContext context);
}
```

`WorldRenderContext` exposes the client level, camera, partial tick, camera
position, a framebuffer size, a `WorldToScreen` projector, and a
`WorldRenderSink`. The projector is bound to
`GameRenderer#projectPointToScreen(Vec3)` and returns clip-space coordinates;
`projectToScreen(Vec3)` converts them to framebuffer pixels and reports whether
the depth is inside the clip range. `WorldRenderSink` draws world-space lines,
points, and camera-facing billboard text. The built-in
`GizmoWorldRenderSink` backs those calls with `Gizmos.line`, `Gizmos.point`, and
`Gizmos.billboardText`.

Rules:

- Register during `onClientSetup` or from the client thread. Duplicate ids throw
  `ClientException` with `duplicate world render listener id: <id>`.
- Listeners run in ascending `order()`, with registration order as the
  tie-break, once per rendered frame on the render thread.
- Use `context.sink()` for drawing. Do not retain the context, sink, level,
  camera, or projector past the callback.
- One failing listener is logged once as
  `WORLD RENDER LISTENER FAILED id=<id>` and does not stop later listeners.
- Unregistering removes the listener from the next frame.
- `WorldRenderRegistry.reset()` is for tests and loader shutdown, not mod code.

The hook target verified against Minecraft 26.3 is
`net.minecraft.client.renderer.LevelRenderer#addMainThreadGizmos(Ljava/util/List;)V`,
injected at `HEAD`. The mixin installs a temporary `SimpleGizmoCollector`, runs
the listeners, drains the collector into the game's main-thread gizmo list, and
therefore renders in the same frame. Set `-Dweaver.proof.worldRender=true` to
print `WORLD RENDER ok listeners=<n>` once after the first frame with at least
one registered listener.

## First-person hand rendering

`api-game` provides `net.weaver.api.game.client.hand`:

```java
HandRenderRegistry.register(HandRenderListener listener);

public interface HandRenderListener extends OrderedClientListener
{
    default void onExtractState(HandRenderStateContext context) {}

    default HandRenderResult onRender(HandRenderContext context)
    {
        return HandRenderResult.CONTINUE;
    }
}
```

`HandRenderContext` exposes the `InteractionHand`, the held `ItemStack`, the
live `PoseStack`, the `SubmitNodeCollector`, the `PlayerRenderState`, the
`FirstPersonHandsAndItemsRenderState`, the partial tick, the `Minecraft`
instance, and the callback phase. Whole-pass callbacks have a `null` hand and a
`null` item stack; the per-hand callback supplies both.
`HandRenderStateContext` exposes the `LocalPlayer`, the mutable
`FirstPersonHandsAndItemsRenderState`, the partial tick, and the client.

Phases:

| Phase | Target | Cancellable |
|---|---|---|
| `PRE` (whole pass) | `FirstPersonHandsAndItemsRenderer#submitHandsWithItems` `HEAD` | Yes, skips both hands |
| `PRE` (one hand) | private `submitArmWithItem` `HEAD` | Yes, skips that hand only |
| `POST` (whole pass) | `FirstPersonHandsAndItemsRenderer#submitHandsWithItems` `RETURN` | No |
| State mutation | `FirstPersonHandsAndItems#extractRenderState` `RETURN` | Not applicable |

Rules:

- Listeners run in ascending `order()` with registration order as the tie-break.
- Returning `HandRenderResult.CANCEL` from a `PRE` callback skips the targeted
  hand or the whole pass. The first cancelling listener stops the pre dispatch.
  A `CANCEL` from `POST` is ignored.
- A whole-pass cancellation dispatches the `POST` phase before the pass returns,
  so listeners see a balanced pre/post pair.
- The state callback may write the public fields of
  `FirstPersonHandsAndItemsRenderState` to change the same frame's hand render.
- A listener exception is isolated and reported once per listener id.
- Unregistering removes the listener from the next frame. `reset()` is for
  tests and loader shutdown.

## Level render stages

`LevelRenderRegistry` reports the structural stages of one level frame:

| Stage | Hook | `PoseStack` / `SubmitNodeCollector` |
|---|---|---|
| `START` | `LevelRenderer#render` `HEAD` | Not available |
| `SKY` | private `LevelRenderer#addSkyPass` `HEAD` | Not available |
| `ENTITIES` | private `LevelRenderer#submitEntities` `HEAD` | Available |
| `BLOCK_ENTITIES` | private `LevelRenderer#submitBlockEntities` `HEAD` | Available |
| `END` | `LevelRenderer#render` `RETURN` | Not available |

`LevelRenderContext` exposes the `ClientLevel`, `CameraRenderState`,
`LevelRenderState`, `PoseStack`, `SubmitNodeCollector`, partial tick, stage, and
`Minecraft` instance. The pose stack and collector are `null` outside the two
entity stages, and the camera state may be `null` when the caller has not
populated the nested state.

At the `ENTITIES` and `BLOCK_ENTITIES` stages the pose stack and collector
are live: draw real geometry there with `WorldGeometry`, `BlockStructure`, and
`QuadMesh` (see [Machines, contraptions, and custom rendering](#machines-contraptions-and-custom-rendering)).
`WorldRenderRegistry` remains the simple way to draw debug lines, points, and
text.

Listeners run in ascending `order()` with registration order as the tie-break.
A listener exception is isolated and reported once per id. The registry never
mutates the level render state.

### Draw stages

The stages above fire while vanilla sets the frame up. `LevelDrawRegistry`
fires while the frame is drawn, at fixed depths of its GPU work, so what a
listener draws lands between what vanilla drew before and after:

| `LevelDrawStage` | When | Open render pass |
|---|---|---|
| `AFTER_SKY` | end of the sky pass | no |
| `AFTER_OPAQUE_BLOCKS` | after solid and cutout terrain, before entities and block entities | yes |
| `AFTER_SOLID` | after the opaque features too (entities, block entities, opaque particles) | yes |
| `AFTER_TRANSLUCENT_BLOCKS` | after translucent terrain (water, stained glass, ice) | classic: yes; improved: no |
| `AFTER_PARTICLES` | after the translucent particles | classic: yes; improved: yes, the layer's pass |
| `AFTER_TRANSLUCENT_FEATURES` | after the translucent features after terrain (particles, the block outline) | yes |
| `AFTER_CLOUDS` | after the clouds | classic: yes; improved: no |
| `AFTER_WEATHER` | after rain and snow | yes |
| `AFTER_TRANSLUCENT` | after all translucent geometry and the world border | classic: yes; improved: no |
| `AFTER_LEVEL` | the whole level is drawn, before the GUI | no |

```java
LevelDrawRegistry.register("examplemod:shield", LevelDrawStage.AFTER_SOLID, context -> {
    RenderPass pass = context.pass(); // open main pass: set a pipeline, bind, draw; never close it
    // context.target() is the main render target; context.camera() the frame's camera state
});
```

With improved (order-independent) transparency vanilla draws the translucent
part once per transparency layer, in passes of its own. The translucent stages
then fire once per layer, with the layer's name in `context.oitLayer()` (null
with classic transparency). A stage without an open pass gives a null
`context.pass()`: open your own on `context.target()`.

The context also carries the frame's view matrix (`context.modelView()`), valid
in every stage. At `AFTER_LEVEL` vanilla has already reset its own.

These stages stand in for NeoForge's `RenderLevelStageEvent` stages:

| NeoForge stage | Weaver stage |
|---|---|
| `AfterSky` | `AFTER_SKY` |
| `AfterOpaqueBlocks` | `AFTER_OPAQUE_BLOCKS` |
| `AfterOpaqueFeatures` | `AFTER_SOLID` |
| `AfterTranslucentBlocks` | `AFTER_TRANSLUCENT_BLOCKS` |
| `AfterTranslucentParticles` | `AFTER_PARTICLES` |
| `AfterTranslucentFeatures` | `AFTER_TRANSLUCENT_FEATURES` |
| `AfterWeather` | `AFTER_WEATHER` |
| `OitTranslucent` | the translucent stages with `oitLayer` set |
| `AfterLevel` | `AFTER_LEVEL` |

Each NeoForge stage has its own Weaver stage. The client proof checks that each
stage is reached with a render target; it puts smoke particles in view so that
`AFTER_PARTICLES` has particles to follow.

### Instanced meshes

`InstancedMesh` is a mesh uploaded to the GPU once and drawn many times in one
draw call. Each copy (instance) has its own transform, color, and light. This is
how to draw thousands of moving parts (gears, shafts, belts, pistons) without
writing their quads every frame: only 80 bytes per instance go to the GPU per
frame.

```java
InstancedMesh gear = InstancedMesh.builder()
        .box(2, 0, 2, 14, 4, 14, atlas.getSprite(Identifier.parse("examplemod:block/gear")))
        .build();
LevelDrawRegistry.register("examplemod:gears", LevelDrawStage.AFTER_SOLID, context -> {
    InstancedMesh.Instances batch = gear.instances(context);
    for (Machine machine : visibleMachines) {
        batch.add(machine.x, machine.y, machine.z, machine.rotation(), 1.0F, 0xFFFFFFFF, machine.light());
    }
    batch.draw();
});
```

- Build quads with `quad(sprite, face, corners)` or `box(...)`, in model units
  (0 to 16 across a block) with block atlas sprites.
- `add(x, y, z, rotation, scale, argb, light)` places an instance at a world
  position, turned around the mesh's centre. `add(matrix, argb, light)` takes a
  full camera-relative matrix.
- **Materials** (`InstancedMesh.Material`) choose how a mesh is drawn:
  - the texture: the block atlas (`Material.blocks()`) or any texture
    (`Material.texture(id)`, where quad coordinates 0..16 cover it);
  - the blending (`Blend`): `OPAQUE`, `CUTOUT`, `TRANSLUCENT`, or
    `ADDITIVE`;
  - `withEmissive(true)`: full bright, without the lightmap or shading;
  - `withCull(false)`: both sides of every face are drawn;
  - `withShaders(vertex, fragment)`: a mod's own shaders reading the same
    inputs.

  Each material gets its own pipeline, built once and compiled during resource
  loading.
- Draw opaque and cut-out meshes at `AFTER_SOLID`, and translucent and additive
  ones at `AFTER_TRANSLUCENT`. In a stage with an open main pass the mesh draws
  into it. In a stage without one (`AFTER_LEVEL`, or translucent stages under
  improved transparency) it opens its own pass on the main target, with color
  and depth.
- A texture not yet loaded is loaded at the end of the frame (loading uploads,
  which an open pass does not allow), and the mesh shows from the next frame.
- Each batch is drawn once: `instances(context)` starts the next. A mesh reuses
  its instance memory from frame to frame.
- It uses real GPU instancing: Weaver's own shader (`weaver:core/instanced`)
  reads a per-instance vertex buffer, and one `drawIndexed` call draws every
  instance.
- The client proof draws 400 spinning cubes in one call, translucent glass
  behind them (hidden where the cubes are in front), and glowing cubes from a
  plain texture in a pass of their own at `AFTER_LEVEL`. A screenshot checks the
  result.

## Screen render phases

## Screen render phases

`ScreenRenderRegistry` targets the general `net.minecraft.client.gui.screens.Screen`
class, so mod screens inherit the hooks:

| Phase | Hook | Result handling |
|---|---|---|
| `PRE` | `Screen#extractRenderState` `HEAD` | `CANCEL` ignored |
| `BACKGROUND` | `Screen#extractBackground` `HEAD` | `CANCEL` skips the background |
| `POST` | `Screen#extractRenderState` `RETURN` | `CANCEL` ignored |

`ScreenRenderContext` exposes the `Screen`, `GuiGraphicsExtractor`, scaled mouse
coordinates, partial tick, phase, and `Minecraft` instance, plus `width()` and
`height()` convenience accessors.

For `BACKGROUND`, dispatch stops at the first listener that returns
`ScreenRenderResult.CANCEL`. Exceptions are isolated; a failing listener counts
as `CONTINUE`. A subclass that overrides `extractBackground` or
`extractRenderState` without calling the super implementation bypasses the
corresponding hook for its own drawing. Screens that call `super` are covered,
which is the normal mod-screen pattern.

## HUD stages

`HudRenderRegistry` exposes five stages:

| Stage | Hook | Graphics available |
|---|---|---|
| `PRE_HUD` | `Hud#extractRenderState` `HEAD` | Yes |
| `CROSSHAIR` | private `Hud#extractCrosshair` `HEAD` | Yes |
| `HOTBAR` | private `Hud#extractItemHotbar` `HEAD` | Yes |
| `POST_HUD` | `Hud#extractRenderState` `TAIL` | Yes |
| `POST_GUI` | `Gui#extractRenderState` `TAIL` | No; scaled window size only |

`net.weaver.api.game.client.hud.HudRenderContext` exposes the
`GuiGraphicsExtractor`, `Hud`, `Minecraft` instance, logical width and height,
partial tick, and stage. `canDraw()` reports whether `graphics()` is non-null.
At `POST_GUI` the context carries a `null` graphics object because `Gui` does
not expose its extractor after extraction. This game-typed context is distinct
from the game-free `net.weaver.api.client.HudRenderContext` used by legacy
`HudLayer` registrations.

### Hiding parts of the HUD

The typed `net.weaver.api.game.event.client.HudLayerRenderEvent` fires before
each part of the HUD is drawn, each frame, while it has listeners. Cancel it to
leave that part out:

```java
EventBus.GAME.addListener(HudLayerRenderEvent.class, event -> {
    if (event.is(VanillaHudLayers.PLAYER_HEALTH) || event.is(VanillaHudLayers.FOOD_LEVEL)) {
        event.setCanceled(true); // the mod draws its own bars
    }
});
```

`net.weaver.api.game.client.hud.VanillaHudLayers` holds the ids of the game's
own parts (camera overlays, crosshair, hotbar, selected item name, hearts,
armor, food, vehicle health, air, contextual bar, experience level, effects,
boss bars, sleep and demo overlays, scoreboard, overlay message, title, chat,
tab list, subtitles); they are the ids NeoForge uses for the same layers. A
`HudRenderRegistry` listener is asked for under its own listener id, so a mod
can hide another mod's HUD. Hiding the crosshair or the hotbar does not stop
the `CROSSHAIR` and `HOTBAR` stages, which run first, so a replacement can be
drawn there.

`POST_HUD` is backward compatible: `HudLayerMixin` dispatches the new stage and
then calls the existing `ClientRegistry.renderHud(HudRenderContext)` at the same
tail. Legacy `HudLayer` registrations therefore keep their original position
above the vanilla HUD. New `HudRenderListener` listeners at `POST_HUD` run
before the legacy layers in that same tail. Listeners run in ascending
`order()`, and failures are isolated once per id.

## Pipeline modifiers

`net.weaver.api.game.client.render.PipelineModifiers` changes the render
pipelines the game draws with while a modifier is pushed: every pipeline asked
for through `RenderSystem#getCompiledPipelineNullable` (which
`getCompiledPipeline` also goes through) is first given to each pushed modifier,
most recently pushed first, and the game draws with what it returns.

```java
Identifier key = Identifier.fromNamespaceAndPath("mymod", "tinted");
PipelineModifiers.register(key, (pipeline, copyName) -> RenderPipelineCopy.of(pipeline)
        .location(copyName)
        .fragmentShader(Identifier.fromNamespaceAndPath("mymod", "core/tinted"))
        .build());

PipelineModifiers.push(key);
try {
    // draw: everything drawn here uses the tinted copies
} finally {
    PipelineModifiers.pop();
}
```

A modifier is asked once per pipeline; the copy is kept, so it is compiled once,
and a copy a modifier made is never modified again. Returning the pipeline
itself (or `null`) leaves it unchanged. `RenderPipelineCopy` copies every
setting of a pipeline through the pipeline's own constructor and changes only
what is set: name, vertex or fragment shader, shader defines, added bind group
layouts, color targets, depth and stencil, polygon mode, culling.
`invalidate()` forgets the kept copies. `push` and `pop` belong to the render
thread; a modifier that throws is logged once per pipeline
(`PIPELINE MODIFIER FAILED`) and the pipeline is left as it was.

## Block colors

`BlockColorRegistry.register(Block, BlockTintSource...)` (or the `List`
overload) queues tint sources for a block. Registration never touches the client
and is safe before the game creates its color table. `BlockColorMixin` applies
the queue at the return of `BlockColors#createDefault`, after vanilla has
registered its built-in sources.

- Several Weaver registrations for the same block are concatenated in
  registration order, so one Weaver registration does not clobber another. The
  composed list is passed to `BlockColors#register`, which replaces the block's
  existing list. Registering a block that vanilla already tints replaces the
  vanilla source unless the caller includes an equivalent source.
- A failure for one block is isolated and logged; later blocks still apply.
- `register` validates the block and every source. `reset()` is for tests and
  loader shutdown.
- Unregistering removes the entry from future `createDefault` calls. It cannot
  undo a registration that was already applied to a live `BlockColors` instance.
- The registry reports `registrationCount()` and, when
  `-Dweaver.proof.blockColors=true` is set, prints
  `[weaver] BLOCK COLORS ok blocks=<n>` once after a non-empty apply.

**Item tints are not supported.** In Minecraft 26.3 item colors are
data-driven `ItemTintSource` definitions loaded from item models; there is no
`ItemColors` registry to extend at runtime. Weaver does not fake an item tint
registry.

## Fog, sky, camera, and FOV

Modded fluids with a [fluid type](fluids.md) that sets `fog(rgb, start, end)`
get that fog while the camera is inside them. Weaver adds its fog environment
first in vanilla's list, so it wins over the default fog.

Three registries expose mutable state at the end of vanilla extraction:

| Registry | Hook | Mutable value |
|---|---|---|
| `FogRenderRegistry` | `FogRenderer#setupFog` `RETURN` | `FogData` |
| `SkyRenderRegistry` | `SkyRenderer#extractRenderState` `RETURN` | `SkyRenderState` |
| `CameraRenderRegistry` | `Camera#extractRenderState` `RETURN` | `CameraRenderState` |

`FogRenderContext` exposes the `Camera`, `FogData`, `ClientLevel`, render
distance value, partial tick, and client. `SkyRenderContext` exposes the level,
partial tick, camera, mutable `SkyRenderState`, and client.
`CameraRenderContext` exposes the camera, mutable `CameraRenderState`,
`DeltaTracker`, partial tick, and client. Listeners run in ascending `order()`
with registration order as the tie-break. A listener exception is isolated and
reported once per id. The sky hook fires at every return of vanilla extraction,
including the early paths for dimensions with no skybox; on those paths only
the fields vanilla populated are meaningful.

`FovModifierRegistry` uses the private `Camera#calculateFov(float)` return,
verified as `(F)F` in 26.3. Each `FovModifier` returns a multiplicative factor
and the registry composes them as
`result = baseFov * factor1 * factor2 ...`. When the composed value differs
from the vanilla value, it is clamped to `[1.0, 179.0]` so a broken modifier
cannot produce an unusable projection; with no active modifier, or when every
factor is a no-op, the vanilla value is returned unchanged. A non-finite or
non-positive factor is ignored and reported once per modifier id; a listener
exception is isolated.

`Projection#setupPerspective` was verified as `(FFFFF)V` (five floats, not six)
and is a lower-level path shared with culling, so the camera FOV method is the
chosen target. `calculateFov` is the world field-of-view path; the separate HUD
field of view computed by `Camera#calculateHudFov` is not modified.

## Entity renderers and render layers

`EntityRendererRegistry.register(EntityType<T>, EntityRendererProvider<T>)`
registers a provider. `EntityRenderers#createEntityRenderers` returns a Guava
immutable map in 26.3, so `EntityRendererMixin` copies the vanilla entries into
a new `LinkedHashMap`, creates each registered renderer with the supplied
`EntityRendererProvider.Context`, and replaces the return value. The vanilla map
is never mutated.

- Registering an entity type that vanilla already provides overrides that
  renderer in the merged map for the next creation pass.
- A provider exception or a `null` renderer is isolated and logged once per
  type; the vanilla entry is kept.
- The renderer table is rebuilt during the client resource reload. A
  registration made after that point applies on the next reload.

`EntityLayerRegistry.register(Class<? extends LivingEntityRenderer<?, ?, ?>>,
EntityLayerFactory)` registers a layer factory. The mixin targets the return of
`LivingEntityRenderer#<init>` rather than `addLayer`: every concrete living
renderer must call the base constructor, the model field is already assigned,
and renderers that never add a vanilla layer are still covered. The cost is
ordering and initialization: registered layers are added before the concrete
subclass's own layers, so they render below them, and factories must not read
subclass state that has not been initialized yet. Returning `null` skips the
renderer; a factory exception is isolated and logged once per registration id.
Removal stops future attachments only, because vanilla has no layer removal.

## Model layers

`ModelLayerRegistry.register(ModelLayerLocation, LayerDefinition)` queues a
definition. `ModelLayerMixin` copies the immutable `LayerDefinitions#createRoots`
map into a new map and adds the queued entries. Registering a location that
vanilla already defines overrides it for the next `createRoots` call; duplicate
locations inside Weaver are rejected.

Bake-order implication: `createRoots` runs while the client builds its
`EntityModelSet`. Register model layers during client setup, before the first
reload finishes. A layer registered after the model set is baked is not
available until the next client resource reload, and
`EntityModelSet#bakeLayer` for a missing location throws. Removal only affects
future `createRoots` calls; an already-baked model set is not modified.

## Client tick and resource reload listeners

`ClientRegistry.registerTickListener(ClientTickListener)` registers a
start-of-tick listener. The two-argument overload takes `pre`:

```java
ClientRegistration pre = ClientRegistry.registerTickListener(listener, true);
ClientRegistration post = ClientRegistry.registerTickListener(listener, false);
```

`ClientTickContext` is `(long tick, boolean pre, Object minecraft)`. Listeners
run on the client thread, once per selected phase. The core backend polls key
mappings before invoking the pre-tick listeners for the same event. The
loader's direct `onClientTick` callback runs after the event dispatch for the
same hook. Exceptions are isolated and logged as `CLIENT TICK LISTENER FAILED`.

`ClientRegistry.registerResourceReloadListener(ResourceReloadListener)`
registers a listener for client resource reloads. `ResourceReloadContext` is
`(Phase phase, Object source, boolean client)`; `Phase` is `START`, `FINISHED`,
or `FAILED`. The core backend maps `ResourceReloadEvent.Phase` to the same
names and only forwards events whose side is `CLIENT`. The built-in hooks post
`START` and `FINISHED`; `FAILED` is part of the API and context enum but is not
posted by the current hook set. Listeners run on the client thread and their
exceptions are isolated and logged.

## ClientRegistration

Every facade registration method returns a `ClientRegistration`:

```java
public interface ClientRegistration
{
    String id();

    boolean isActive();

    void unregister();
}
```

- `id()` is the backend's stable registration id. Key mappings report
  `<mappingId>:<modId>`. Screen factories, HUD layers, and tooltip providers
  report the id supplied by the factory/layer/provider. Chunk listeners report
  `client-chunk:<sequence>`; tick listeners report `client-tick-pre:<sequence>`
  or `client-tick-post:<sequence>`; resource reload listeners report
  `client-resource-reload:<sequence>`. World render listeners report their
  `WorldRenderListener.id()`. Game-typed ordered listeners (hand, level,
  screen, HUD, fog, sky, camera, and FOV) report their listener `id()`. Block
  color registrations report `block-color:<sequence>`, entity renderers
  `entity-renderer:<sequence>`, entity layers `entity-layer:<sequence>`, and
  model layers `model-layer:<sequence>`.
- `isActive()` is `false` after `unregister()` or after the backend is
  uninstalled or reset. Game-typed registry handles are independent of the
  client backend and stay active until they are unregistered or their registry
  is reset by tests/shutdown.
- `unregister()` is idempotent and thread-safe. It removes the registration
  from future dispatch. Removing an already inactive registration does not
  throw.
- `ClientRegistry.registrationCount()` counts only registrations created
  through the facade and still active; direct backend registrations are not
  tracked. It prunes inactive handles and returns `0` when no backend is
  active.
- `ClientRegistry.uninstall()` uninstalls the active backend, clears
  facade-tracked registrations, and is fail-soft. `reset()` clears the cached
  backend and registrations so the next backend-needing call resolves a fresh
  backend. Both may run from any thread. Backend uninstall failures are printed
  to standard error and swallowed.

## Server safety

The API is safe to load on a dedicated server. `ClientRegistry` does not
initialize client game classes just by being loaded, and `isAvailable()` never
resolves a backend, never loads client classes, and never throws. It returns
`false` until a backend has been installed or previously resolved and reports
itself available.

The first call that needs a backend (`backend()`, any registration method,
`openScreen`, `renderHud`, or `tooltipLines`) performs a non-initializing
`Class.forName("net.minecraft.client.Minecraft", false, loader)` check before
loading `net.weaver.core.client.CoreClientBackend`. When the client class is
absent, it throws `ClientUnavailableException` with the message
`client API is not available on this side: net.minecraft.client.Minecraft is not
present` and the core client backend is never loaded. When the client class is
present but resolution fails, the failure is also reported as
`ClientUnavailableException`, with the original cause.

Dedicated-server test guidance:

- Assert `ClientRegistry.isAvailable()` is `false`.
- Call a backend-needing method, for example `registerScreenFactory`, and
  expect `ClientUnavailableException`.
- Assert the log contains no `CLIENT PROOF HUD`, `CLIENT PROOF SCREEN`, or
  other client-render markers.
- A custom test backend can be installed with `ClientRegistry.install(...)` on a
  test process; uninstall or reset it after the test.

The game-typed render registries in `net.weaver.api.game.client` reference
Minecraft client classes directly. They are client-only types: a mod that may
run on a dedicated server must guard their use with
`ClientRegistry.isAvailable()`, a client-side lifecycle callback such as
`onClientSetup`, or a non-initializing presence check for
`net.minecraft.client.Minecraft`. The built-in mixins are only armed by
`LoaderAgent` on the client, so the registry classes are never touched by the
server unless mod code calls them.

There is no server-to-client forwarding in this API. A client key press, HUD
layer, or tooltip provider exists only in the client process. Server state is
not created or mutated by the facade.

## Guide screens

`GuideScreen` is a ready guide screen in the style of guide mods: pages of
steps, each step a 3D scene of blocks with an explanation.

```java
GuideScreen.builder(Component.literal("Smelter guide"))
        .page(GuideScreen.page(Component.literal("Building it"))
                .buildUp(smelter, Component.literal("Lay the floor, then the furnace, then the hopper.")))
        .page(GuideScreen.page(Component.literal("Using it"))
                .step(smelterRunning, Component.literal("Drop ore into the hopper.")))
        .open();
```

- `buildUp(structure, text)` splits a `BlockStructure` into steps that add one
  layer at a time, playing by themselves (one second per layer by default).
- `step(scene, text)` and `step(scene, text, ticks)` add steps by hand.
- The player drags to turn the scene and scrolls to zoom. The arrow buttons and
  keys move between steps across pages, and **Replay** restarts the page.
- `next()`, `previous()`, `replay()`, and `currentStep()` drive it from code.

The scene is drawn with `GuiBlockScene`, which fits it to the screen. For
other layouts, vanilla's `GridLayout`, `LinearLayout`, and
`HeaderAndFooterLayout` work in mod screens as in vanilla's.

## Machines, contraptions, and custom rendering

These APIs (in `net.weaver.api.game.client.blockentity` and
`net.weaver.api.game.client.render`) cover what large technical mods need to
draw: rotating parts, moving structures, their own shaders, and guide screens.
The client proof exercises each of them in a real client.

**Block entity renderers.** `BlockEntityRendererRegistry.register(type,
context -> renderer)` adds a renderer for a block entity type (or replaces a
vanilla one). A renderer copies what it needs in `extractRenderState` and draws
in `submit` through the `SubmitNodeCollector`. Register in client setup; the
table is rebuilt at each resource reload. A failing provider is logged once and
skipped.

**Geometry and blocks in the world.** `WorldGeometry`:
- `translateToWorld(poses, camera, x, y, z)` puts a world position at the pose origin.
- `geometry(collector, poses, renderType, (pose, consumer) -> ...)` draws any triangles or quads with any render type.
- `block(collector, poses, level, lightPos, state)` draws a block model at any transform, lit as if it stood at `lightPos` (the way pistons draw moving blocks).
- `blockWithLight(...)` draws without a level.

Draw from a block entity or entity renderer's `submit`, from
`LevelRenderRegistry` at the `ENTITIES` or `BLOCK_ENTITIES` stage (pose stack
and collector are set there), or from `PrepareRenderBuffersEvent`.

**Contraptions.** `BlockStructure` is a set of blocks at local positions,
drawn together with `submit(collector, poses, level, lightOrigin)`. Rotate,
move, or scale the pose stack to move the whole structure; `centre()` gives
the pivot.

**Models built in code.** `QuadMesh.builder()` builds quads (or `box(...)`)
with texture coordinates (optionally mapped into an atlas sprite), colors, and
normals once; `submit(collector, poses, renderType, light, tint)` draws it at
the current pose every frame. Animate by changing the pose: rotate a gear,
slide a piston arm. Each quad is written every frame, so prefer few large
meshes for thousands of instances (there is no GPU instancing).

**Shaders, render types, post effects.** `CustomRendering`:
- `renderType(name, RenderSetup)` makes a render type from any pipeline, including one built with `RenderPipeline.builder()` and your own shaders in `assets/<namespace>/shaders/`.
- `precompile(pipeline)` compiles it during resource loading instead of on first use.
- `enablePostEffect(id)` / `disablePostEffect(id)` applies a full-screen post chain (`assets/<namespace>/post_effect/<path>.json`) every frame.

**3D scenes in screens.** `GuiBlockScene.draw(graphics, x0, y0, x1, y1,
structure, yaw, pitch, scale)` draws a `BlockStructure` rotated inside a
screen box, the core of guide screens like Ponder. Lighting is fixed full
brightness. Build the rest of the screen (text, buttons, layouts) with
vanilla widgets.

## Custom model loaders

A block or item model file whose JSON has a `"loader"` field gets its geometry
from the loader registered under that id, instead of from vanilla's cuboid
`elements`. The rest of the file (parent, textures, display transforms,
ambient occlusion) works as usual, and block state variants still rotate the
model.

`ModelLoaderRegistry.register(id, (json, context) -> geometry)` registers a
loader in client setup. It returns any `UnbakedGeometry`, whose `bake` builds
the baked quads from the resolved textures and the variant's transform.

Weaver has one built-in loader, `weaver:quads` (`QuadGeometry`), for shapes
that are not boxes: slopes, wedges, pipes, and panels at any angle.

```json
{
  "loader": "weaver:quads",
  "parent": "minecraft:block/block",
  "textures": { "all": "examplemod:block/slope", "particle": "#all" },
  "quads": [
    { "texture": "#all", "face": "up",
      "vertices": [[0, 0, 16, 0, 16], [16, 0, 16, 16, 16], [16, 16, 0, 16, 0], [0, 16, 0, 0, 0]] },
    { "texture": "#all", "face": "down", "cull": "down",
      "vertices": [[0, 0, 0, 0, 0], [16, 0, 0, 16, 0], [16, 0, 16, 16, 16], [0, 0, 16, 0, 16]] }
  ]
}
```

Each quad has four corners `[x, y, z, u, v]` in model units (0 to 16 across a
block), counter-clockwise seen from the front:

- `face` sets the shading direction.
- `cull` (optional) hides the quad when that side touches a full block.
- `tint` (optional, default -1) is the tint index.

From code, `QuadGeometry.builder().quad(...).build()` makes the same geometry
for a custom loader to return.

**Failures.** A model naming an unknown loader, or one whose loader throws, gets
empty geometry. One `MODEL LOADER FAILED loader=<id>` line is logged per
loader. Resource loading goes on, and the other models are not affected.

**Item and special model types.** `ModelLoaderRegistry.registerItemModelType(id,
codec)` adds a type for item definitions (`"type": "<id>"` in
`assets/<ns>/items/*.json`). `registerSpecialModelType(id, codec)` adds a
special model renderer type. Both must be called before the first resource
reload.

The client proof loads a `weaver:quads` slope and a model from its own test
loader, and checks both baked item models (`model-loader-read`,
`model-loader-baked`, `loader-item-models`).

## Model and color registration

What is supported:

- **Block tint sources** through `BlockColorRegistry` and `BlockColorMixin`.
  Entries are queued and applied at `BlockColors#createDefault`.
- **Model layer definitions** through `ModelLayerRegistry` and
  `ModelLayerMixin` at `LayerDefinitions#createRoots`. The definition is
  available to renderers that call `EntityModelSet#bakeLayer` after the model
  set is built. Register before the first client reload finishes.
- **Runtime textures** through `DynamicTextureHandle` (see
  [Runtime textures](#runtime-textures)).

- **Item tints and item model properties.** In 26.3 item colors are
  data-driven `ItemTintSource` definitions in item models. Mods add their own
  types with `ModelLoaderRegistry.registerItemTintSource(id, codec)`, used as
  `"tints": [{"type": "<id>"}]` (an energy bar color, a stored fluid's color).
  Item model properties work the same way:
  - `registerConditionalProperty` for `minecraft:condition` models;
  - `registerRangeProperty` for `minecraft:range_dispatch` models;
  - `registerSelectProperty` for `minecraft:select` models.

  Register these before the first resource reload.

What is not supported:
- **Baked models with no model file.** A baked block or item model starts from
  a model file in resources (a mod jar's `assets/` is a built-in resource
  pack, see [Mod jars as packs](registry.md#mod-jars-as-packs)). Its geometry
  can come from code through a [custom model loader](#custom-model-loaders),
  and baked results can be replaced with `ModifyBakingResultEvent`. For
  dynamic models drawn every frame, use `QuadMesh` (see
  [Machines, contraptions, and custom rendering](#machines-contraptions-and-custom-rendering)).
- **Arbitrary model re-baking.** `ModelLayerRegistry` adds definitions to the
  root map. It does not force an existing `EntityModelSet` to re-bake and does
  not replace a baked `ModelPart` tree.

## Integration requests

The built-in client hooks are implemented by the `api-game` mixins. The table
lists each target and its current status:

| Target | Status | Call |
|---|---|---|
| `net.minecraft.client.gui.Hud#extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V` | Implemented (`TAIL`) | `ClientRegistry.renderHud(HudRenderContext)` with the partial tick |
| `net.minecraft.client.gui.screens.Screen#getTooltipFromItem(Lnet/minecraft/client/Minecraft;Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;` | Implemented (`RETURN`) | `ClientTooltipHook.appendTooltip`; the core backend binds it to `CoreClientHooks.appendTooltip(Object, Object, List)` |
| `net.minecraft.client.multiplayer.ClientLevel#onChunkLoaded(Lnet/minecraft/world/level/ChunkPos;)V` | Implemented (`TAIL`) | `ClientChunkHooks.chunkLoaded(ClientLevel, ChunkPos)` |
| `net.minecraft.client.multiplayer.ClientLevel#unload(Lnet/minecraft/world/level/chunk/LevelChunk;)V` | Implemented (`HEAD`) | `ClientChunkHooks.chunkUnloaded(ClientLevel, ChunkPos)` |
| `net.minecraft.client.multiplayer.ClientLevel#setServerVerifiedBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)V` | Implemented (`HEAD`) | `ClientChunkHooks.blockUpdated(ClientLevel, BlockPos)` |
| `net.minecraft.client.renderer.LevelRenderer#addMainThreadGizmos(Ljava/util/List;)V` | Implemented (`HEAD`) | `WorldRenderRegistry.render(WorldRenderContext)` |
| `net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer#submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;)V` | Implemented (`HEAD`, `RETURN`) | `HandRenderRegistry.dispatch(HandRenderContext)` (whole-pass pre/post) |
| `net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer#submitArmWithItem(Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V` | Implemented (`HEAD`, cancellable) | `HandRenderRegistry.dispatch(HandRenderContext)` per hand |
| `net.minecraft.client.player.FirstPersonHandsAndItems#extractRenderState(Lnet/minecraft/client/player/LocalPlayer;FLnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;)V` | Implemented (`RETURN`) | `HandRenderRegistry.dispatchState(HandRenderStateContext)` |
| `net.minecraft.client.renderer.LevelRenderer#render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V` | Implemented (`HEAD`, `RETURN`) | `LevelRenderRegistry.dispatch(LevelRenderContext)` for `START`/`END` |
| `net.minecraft.client.renderer.LevelRenderer#addSkyPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;)V` | Implemented (`HEAD`) | `LevelRenderStage.SKY` |
| `net.minecraft.client.renderer.LevelRenderer#submitEntities(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V` | Implemented (`HEAD`) | `LevelRenderStage.ENTITIES` |
| `net.minecraft.client.renderer.LevelRenderer#submitBlockEntities(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V` | Implemented (`HEAD`) | `LevelRenderStage.BLOCK_ENTITIES` |
| `net.minecraft.client.gui.screens.Screen#extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V` | Implemented (`HEAD`, `RETURN`) | `ScreenRenderRegistry.dispatch(ScreenRenderContext)` for `PRE`/`POST` |
| `net.minecraft.client.gui.screens.Screen#extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V` | Implemented (`HEAD`, cancellable) | `ScreenRenderRegistry.dispatch(ScreenRenderContext)` for `BACKGROUND` |
| `net.minecraft.client.gui.Hud#extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V` | Implemented (`HEAD`, `TAIL`) | `HudRenderStage.PRE_HUD` and `POST_HUD` |
| `net.minecraft.client.gui.Hud#extractCrosshair(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V` | Implemented (`HEAD`) | `HudRenderStage.CROSSHAIR` |
| `net.minecraft.client.gui.Hud#extractItemHotbar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V` | Implemented (`HEAD`) | `HudRenderStage.HOTBAR` |
| `net.minecraft.client.gui.Gui#extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V` | Implemented (`TAIL`) | `HudRenderStage.POST_GUI` |
| `net.minecraft.client.color.block.BlockColors#createDefault()Lnet/minecraft/client/color/block/BlockColors;` | Implemented (`RETURN`) | `BlockColorRegistry.apply(BlockColors)` |
| `net.minecraft.client.renderer.fog.FogRenderer#setupFog(Lnet/minecraft/client/Camera;ILnet/minecraft/client/DeltaTracker;FLnet/minecraft/client/multiplayer/ClientLevel;)Lnet/minecraft/client/renderer/fog/FogData;` | Implemented (`RETURN`) | `FogRenderRegistry.dispatch(FogRenderContext)` |
| `net.minecraft.client.renderer.SkyRenderer#extractRenderState(Lnet/minecraft/client/multiplayer/ClientLevel;FLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/state/level/SkyRenderState;)V` | Implemented (`RETURN`) | `SkyRenderRegistry.dispatch(SkyRenderContext)` |
| `net.minecraft.client.Camera#extractRenderState(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lnet/minecraft/client/DeltaTracker;)V` | Implemented (`RETURN`) | `CameraRenderRegistry.dispatch(CameraRenderContext)` |
| `net.minecraft.client.Camera#calculateFov(F)F` | Implemented (`RETURN`, cancellable) | `FovModifierRegistry.apply(FovContext, float)` |
| `net.minecraft.client.renderer.entity.EntityRenderers#createEntityRenderers(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;)Ljava/util/Map;` | Implemented (`RETURN`, map merge) | `EntityRendererRegistry.merge(Map, Context)` |
| `net.minecraft.client.renderer.entity.LivingEntityRenderer#<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Lnet/minecraft/client/model/EntityModel;F)V` | Implemented (`RETURN`) | `EntityLayerRegistry.attach(LivingEntityRenderer, Context, LayerSink)` |
| `net.minecraft.client.model.geom.LayerDefinitions#createRoots()Ljava/util/Map;` | Implemented (`RETURN`, map merge) | `ModelLayerRegistry.merge(Map)` |
| `net.minecraft.client.multiplayer.ClientPacketListener#handleCommands(Lnet/minecraft/network/protocol/game/ClientboundCommandsPacket;)V` | Implemented (`TAIL`, `ClientCommandTreeMixin`) | `CommandRegistry.recordTreeSync(this.getCommands(), names, "client")`; see [commands.md](commands.md) |

The tooltip mixin tolerates a null or immutable game list and does not retain
the list or its elements. Do not call `ClientRegistry.tooltipLines` from an
arbitrary game callback without checking the client thread.

## Arabic and bidirectional text input

Weaver ships a built-in client feature that renders Arabic and other
bidirectional text inside `EditBox` with shaping, bidi reordering, and a
logical-to-visual cursor/selection mapping. It is armed automatically when the
optional `api-game` artifact is present; the system class-space mode is the
supported path.

- The stored value is never changed. The server still receives the original
  logical text; only the rendered text, cursor, and selection are transformed.
- Shaping and bidi use ICU4J, which Minecraft already bundles.
- The feature lives in `api-game` as `ArabicEditBoxMixin` with the
  `weaver.client.mixins.json` config. `LoaderAgent` arms the config on the
  client; no mod install is required.
- Chat, anvil, sign, and search boxes use `EditBox` and are covered. Screens
  that implement their own text widget are not covered.
- Search-highlight formatters are not preserved by the shaped overlay. The
  hint and suggestion strings are left to the vanilla path.
- Long-text horizontal scrolling in RTL layout is approximate; the v1 feature
  focuses on typing, cursor placement, and selection in normal-length fields.

Verify the feature without launching the game:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\verify-arabic.ps1
```

Expected: `WEAVER ARABIC PROBE ok=true`, with
`MIXIN APPLIED ... target=net.minecraft.client.gui.components.EditBox` in the
captured output.

## Limitations

- Screens are vanilla `Screen` classes with vanilla widgets and layouts.
  Weaver adds ready screens (the mod list, generated settings, and
  `GuideScreen`), not a separate widget toolkit.
- `LevelRenderRegistry` and `WorldRenderRegistry` are the simple paths, with
  setup-time stages and gizmo drawing. Real drawing goes through
  `LevelDrawRegistry` (open render passes at eight points of the frame),
  `CustomRendering` (your own pipelines, shaders, render types, and post
  effects), `WorldGeometry`, `BlockStructure`, `QuadMesh`, and
  `InstancedMesh` (GPU instancing).
- `InstancedMesh` draws opaque meshes from the block atlas. Translucent
  instanced meshes, or other textures, need a custom pipeline built the same
  way.
- `Options.keyMappings` is updated by reflection. A hardened JVM with denied
  module access can make the array append fail; the mapping still works through
  the `KeyMapping` constructor's static maps. The failure is logged once per
  operation.
- `KeyMappingSpec.modifiers` is applied by polling the keyboard state after a
  click is consumed. The state reader checks the left/right Shift, Control, Alt,
  and Super scancodes; Caps Lock and Num Lock are not read. A state-read failure
  is treated as "no modifier held" and skips the action.
- Block tint sources, item tint sources, item model properties, model layer
  definitions, and custom model loaders are supported (see
  [Model and color registration](#model-and-color-registration) and
  [Custom model loaders](#custom-model-loaders)). Item textures are replaced
  with resource packs, as in vanilla.
- A screen subclass that overrides `extractRenderState` or
  `extractBackground` without calling `super` bypasses the corresponding screen
  phase hook for its own drawing. This is inherent to targeting the general
  `Screen` class.
- Entity layers are attached at the living renderer constructor return, so they
  are added below the concrete renderer's own vanilla layers, and factories
  must not depend on subclass constructor state. Removal does not detach a
  layer that was already added.
- A model layer registered after the `EntityModelSet` is baked is not available
  until the next client resource reload.
- FOV modifiers apply to the world field of view (`Camera#calculateFov`) and
  not to the separate HUD field of view (`Camera#calculateHudFov`). A composed
  value is clamped to `[1.0, 179.0]`; with no active modifier the vanilla value
  is unchanged.
- Block color registration is applied on `BlockColors#createDefault`; removing
  a registration cannot undo an already-applied live color table.
- Resource reload `FAILED` is supported by the context type but not posted by
  the current built-in hooks.
- Client chunk events report server-confirmed block updates only. Local
  predictions that have not been confirmed are not reported, and a level unload
  does not synthesize an unload event for every remaining chunk.
- The client command tree snapshot is an observation of the server tree, not a
  client-side command registry. It is replaced by the next packet the client
  receives, and an integrated-server process can also overwrite it with a
  server-side snapshot from the same process; compare `source()` when the
  distinction matters.
- `WorldRenderRegistry` listeners draw through the gizmo system. For real
  geometry, depth, and post effects use `LevelDrawRegistry` and
  `CustomRendering`. Every render context's game objects are
  render-thread-only.
- Dynamic texture mipmaps cost a CPU downsample pass after each partial upload.
  Partial uploads are not designed for per-frame full-surface streaming.
- No server leakage by construction: the facade never forwards client state to
  a server, and the backend refuses to load on a process without the client
  class.
- All built-in client integration points, including the render expansion, are
  injected by the base loader when `api-game` is present. No mod Mixin is
  required. The game-typed registries are thread-safe and isolate listener
  failures; a broken listener never throws into the game loop.

## Related

- [Event bus](events.md)
- [Hook points](hooks.md)
- [Command API](commands.md)
- [Registry API](registry.md)
- [Documentation index](README.md)
