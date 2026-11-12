# Weaver Minimap v2 Plan

Status: implementation plan, `feature/minimap-v2`
Baseline checkpoint: `13a2032` (branch `feature/minimap`)
Engine: Minecraft 26.3, Java 26, Gradle 9.7.1
Scope: client-only `examples/minimap`, plus general client hooks in
`api` / `api-game` / `core`.

Baseline evidence (before this branch):

- `examples/minimap`: `clean test weaverBuild` **BUILD SUCCESSFUL**.
- Root `:api:test :core:test :api-game:test --rerun-tasks --no-daemon`:
  **674 tests, 0 failures, 0 errors, 0 skipped**.
- `tools/verify.ps1`: **9/9**, `WEAVER VERIFY ok=true steps=9`.
- `tools/verify-arabic.ps1`: **WEAVER ARABIC PROBE ok=true**.
- Existing client proof markers: `MINIMAP HUD rendered`, `TEXTURE ok`,
  `SCREEN rendered`, `SETTINGS rendered`, `WAYPOINT ok`.

## 1. Product definition

The mod becomes a modern, dark-glass map application with two surfaces:

1. A HUD minimap that reads as a polished game widget: circular or rounded,
   player-up or north-up, smooth zoom, compass ring, waypoint/entity markers,
   off-screen waypoint arrows, and an optional information bar.
2. A fullscreen map that reads as a desktop map application: map canvas,
   searchable waypoint sidebar, top toolbar, right-side layers panel, bottom
   status bar, context menu, cave controls, and draggable waypoints.

The settings screen is a real tabbed application with live preview, search,
import/export, reset, sliders, toggles, dropdowns, color and icon pickers.

## 2. Non-negotiable rules

- Never work on `main`, never push.
- Do not delete the checkpoint or the existing mod; extend it.
- The mod stays client-only and adds **no Mixin of its own**. It uses loader
  hooks only.
- No map logic in `LoaderAgent`, `ClientRegistry`, `CoreClientBackend`,
  `HudLayerMixin`, or any other loader class.
- Loader changes must be general, documented in `docs/client.md`, covered by
  tests, and must not break `tools/verify.ps1` or the Arabic probe.
- No reflection hacks or loader patches for map-specific behavior.
- No placeholder markers in tracked files.
- Every visual phase ends with a real in-game screenshot that is opened and
  inspected. "The code runs" is not visual acceptance.
- Kill only test clients/servers started by this task. Never touch the user's
  CurseForge process (PID was 8496 at the start; do not assume it stays fixed,
  identify by command line).

## 3. Work split and file ownership

Two agents may never edit the same file. The integrator owns the final merge.

### Agent A - loader client hooks

Owns:

- `api/src/main/java/net/weaver/api/client/**` (new general client types only)
- `api-game/src/main/java/net/weaver/api/game/client/**`
- `api-game/src/main/resources/weaver.client.mixins.json`
- `core/src/main/java/net/weaver/core/client/**`
- `core/src/main/resources/weaver.core.mixins.json` (new)
- `core/src/main/java/net/weaver/core/LoaderAgent.java` (only the core mixin
  config arming line)
- `api-game/src/test/java/net/weaver/api/game/client/**`
- `core/src/test/java/net/weaver/core/client/**`
- `docs/client.md`, `docs/hooks.md` (client hook sections)

Deliverables:

1. **Tooltip injection** (`Screen#getTooltipFromItem` RETURN).
   Core mixin calls `CoreClientHooks.appendTooltip(Minecraft, ItemStack,
   List<Component>)`. The hook must tolerate an immutable/null list, never
   retain game objects, and log once on failure. Keep the existing
   `ClientRegistry.tooltipLines` facade working. Add a client proof marker.
2. **World render hook** (`WorldRenderListener` / `WorldRenderContext` in
   api-game, game-typed). Context exposes a graphics/drawing sink, the client
   level, camera, partial tick, and a world-to-screen projector. The sink draws
   world-space lines, points, and billboard text (backed by the 26.3 gizmo
   system). Hook runs once per rendered frame on the render thread. No map
   logic. Add a proof marker and a test/proof that emits one line.
3. **Client chunk events** (`ClientChunkListener` in api, game-free, plus the
   api-game bridge). Events: chunk load, chunk unload, block update. Context
   carries dimension id, chunk X/Z, and block X/Y/Z for updates. Load/unload
   must be dedicated events, not inferred from polling. Add tests for listener
   isolation and duplicate registration rules.
4. **Dynamic texture enhancements** in `DynamicTextureHandle`:
   - `TextureOptions` (linear filtering, clamp address mode, optional mipmaps).
   - partial ARGB upload `uploadRegion(x, y, width, height, int[] argb)`.
   - full upload remains.
   - size/format validation and clear failure messages.
   - `close()` idempotent and safe after client shutdown.
   - Implement mip levels only if the renderpearl command encoder supports it
     cleanly; otherwise document the limit and provide linear filtering plus
     partial upload. Do not patch the game.
5. **KeyMappingSpec modifiers**: apply the modifier mask in the core polling
   path (skip the action when the required modifiers are not held; consume the
   click either way). Preserve the API. Document the exact semantics and
   limitations. Add pure tests for the mask matcher.
6. Update `docs/client.md` with a short, accurate section per hook, including
   integration targets, threading, null/immutability rules, and proof markers.
7. Run `:api:test :core:test :api-game:test` and `tools/verify.ps1`.

### Agent B - map data and cave engine

Owns:

- `examples/minimap/src/main/java/net/weaver/minimap/map/**`
- `examples/minimap/src/test/java/net/weaver/minimap/map/**`

Deliverables:

1. Keep `MapProjection`, `RegionKey`, `RegionData`, `RegionStorage` source and
   binary compatible; extend, do not break existing tests.
2. Surface scanning:
   - deterministic per-block colors with slope shading, biome tint, water/lava.
   - budgeted scan queue (existing behavior preserved).
   - biome id/name per pixel or per chunk for the status bar (either a compact
     per-region biome palette or a query API; document the choice).
   - region file format v2 with backward-compatible reading of v1.
   - async region load: `regionIfLoaded(key)` plus `requestRegion(key)`; the
     blocking `region(key)` stays for compatibility but the UI uses the async
     path. Loads must not run on the render thread.
   - LRU with a configurable budget and dirty tracking.
   - mip/downsample data for zoomed-out rendering (a 64x64 average per region
     is sufficient if GPU mips are unavailable).
3. Cave engine:
   - `CaveSlice` model (region key, Y, ARGB colors, present mask).
   - slice scanner: sample one block per column at Y, air transparent, fluids
     colored, solid blocks shaded with a cave palette; Nether/End palettes.
   - auto cave Y detection with a budgeted, deterministic scorer that prefers
     walkable cave floors near the player and below the surface.
   - per-`(region, Y)` memory cache with LRU and a separate storage root;
     optional gzip persistence with an atomic write path.
   - `MapManager` API: cave mode state is read from `MinimapConfig`; expose
     `caveSlice(RegionKey, int y)`, `requestCaveSlice(...)`, `autoCaveY()`,
     `caveDepth()`, `caveReady()`; never block the render thread.
4. Performance: no per-frame allocations in scan/render helpers, explicit
   budgets, counters for scanned chunks/slices, and a `MapStats` snapshot.
5. Tests: v1/v2 storage round-trip, corrupt/truncated files, async load state,
   cave slice determinism, auto-Y scorer, LRU eviction, mip downsample.
6. Run `..\..\gradlew.bat test weaverBuild` in `examples/minimap`.

### Agent C - visual system, icons, widgets

Owns:

- `examples/minimap/src/main/java/net/weaver/minimap/ui/theme/**`
- `examples/minimap/src/main/java/net/weaver/minimap/ui/widget/**`
- `examples/minimap/src/main/java/net/weaver/minimap/render/IconAtlas.java`
- `examples/minimap/src/main/java/net/weaver/minimap/render/UiDraw.java`
- tests under `examples/minimap/src/test/java/net/weaver/minimap/ui/**`

Deliverables:

1. Theme model: `Theme` (accent, background, surface, elevated surface, border,
   text, muted text, danger, success, grid, player, waypoint palette, shadow),
   `ThemeId` (DARK, LIGHT, MIDNIGHT, EMERALD, AMBER, CUSTOM), `ThemeManager`
   with custom accent override and persistence hooks.
2. Drawing toolkit `UiDraw`: 8px grid helpers, rounded rectangles with
   anti-aliased corners, soft shadows, 1px hairline borders, gradients,
   vertical/horizontal dividers, text with ellipsis and alignment, badges,
   progress bars, toggle switches, slider tracks, scrollbars, and hit tests.
   Must use only `GuiGraphicsExtractor` primitives (`fill`, `fillGradient`,
   `text`, `blit`, `enableScissor`), no game Mixins.
3. Reusable widgets: `UiButton`, `UiIconButton`, `UiToggle`, `UiSlider`,
   `UiDropdown`, `UiTabs`, `UiScrollView`, `UiColorPicker`, `UiIconPicker`,
   `UiKeybindButton`, `UiTextField` (wraps `EditBox`), `UiTooltip`,
   `UiContextMenu`, and layout helpers. Widgets implement the 26.3
   `Renderable` / `GuiEventListener` / `NarratableEntry` contracts and use
   `AbstractWidget` when practical. They must be keyboard accessible, show
   focus rings, clamp to parent bounds, and never draw outside a scissor.
4. Icon atlas: generated at runtime into one `DynamicTextureHandle` (or a
   small set), 24x24 logical icons with 4x supersampling. Required icons:
   close, settings, plus, target/center, layers, search, chevron-left/right,
   eye, eye-off, trash, pencil, filter, sort, compass, zoom-in, zoom-out,
   grid, cave, player, skull, home, portal, village, resource, star, pin,
   waypoint, death. Provide `IconAtlas.draw(graphics, icon, x, y, size, argb)`.
5. Animations: `Easing` (linear, ease-out-cubic, ease-in-out-cubic,
   spring-out), `Animation` (start/duration/eased value), hover/press/tab/
   dropdown/context-menu helpers. All animation is time-based and allocation
   free after construction.
6. Tests: color contrast helpers, layout math, ellipsis, slider math, tab
   selection, dropdown hit tests, animation clamping.
7. Run `..\..\gradlew.bat test weaverBuild` in `examples/minimap`.

### Agent D - waypoints, config, keybinds, public mod API

Owns:

- `examples/minimap/src/main/java/net/weaver/minimap/waypoint/**`
- `examples/minimap/src/main/java/net/weaver/minimap/config/**`
- `examples/minimap/src/main/java/net/weaver/minimap/input/**`
- `examples/minimap/src/main/java/net/weaver/minimap/api/**`
- corresponding tests under `src/test/java/net/weaver/minimap/{waypoint,config,input,api}`

Deliverables:

1. Waypoint model v2: `id`, `name`, `dimension`, `x/y/z`, `color`, `icon`,
   `group`, `visible`, `temporary`, `death`, `createdAt`, `updatedAt`.
   Backward-compatible v1 JSON read/write; v2 writes preserve unknown future
   fields when possible (store an extension map), and never lose data.
2. `WaypointManager`: add/update/remove/move, visibility, groups, search,
   sort by name/distance/color, import/export (JSON string and file), dedupe,
   death waypoint lifecycle, per-world/per-dimension persistence, and a
   future-sync-friendly `WaypointSyncRecord` (unused in v2, documented).
3. Config v2: all settings required by the plan (HUD shape/corner/size/zoom/
   opacity/rotation/compass/readout/time; fullscreen layer toggles; cave mode;
   entities; waypoints; theme; keybind hints; performance preset; compatibility).
   Keep old keys readable. Validation/clamping and corrupt backup stay.
   Add `resetSection`, `resetAll`, `exportJson`, `importJson`, and a
   `snapshot()` record for live preview.
4. Keybinds: define all 11 mappings and conflict detection helper. The mod
   registers them through the existing loader API; D owns the spec table and
   action IDs, not the registration in `MinimapClient` (integrator wires it).
5. Public `net.weaver.minimap.api.MinimapApi` with provider registries:
   `MapLayerProvider`, `MapColorProvider`, `WaypointProvider`,
   `EntityIconProvider`, `HudOverlayProvider`, `MapActionProvider`.
   Registration is idempotent by id, thread-confined to the client, isolated
   on failure, and documented. No server sync.
6. Tests: v1/v2 waypoint migration, malformed data, import/export round-trip,
   group/search/sort, config migration/reset/export, keybind conflict,
   provider duplicate/failure isolation.
7. Run `..\..\gradlew.bat test weaverBuild` in `examples/minimap`.

### Agent E - QA and proof

Owns:

- `examples/minimap/src/test/java/net/weaver/minimap/qa/**`
- `tools/minimap-proof/**`
- verification reports under `build/minimap-proof/` (ignored)

Deliverables after integration:

1. End-to-end unit suites for cave/settings/waypoint/theme interactions.
2. A headless proof script that runs the client with proof properties and
   captures screenshots and markers.
3. Performance measurement harness (HUD idle, fullscreen, 100/500 waypoints,
   entities, cave, zoom-out) with recorded numbers.
4. Compatibility checklist and a final verification report.

### Integrator (this session)

Owns:

- `MinimapClient`, `MinimapMod`, `render/MinimapHudLayer`,
  `render/RegionTextureCache`, `ui/MinimapScreen`, `ui/MinimapSettingsScreen`,
  `ui/*` dialogs, docs, README, `play.ps1`, screenshot proof wiring.
- Final integration, cross-agent review, build, client run, screenshots.

## 4. Loader API contracts used by the mod

These are the only loader-facing surfaces the mod may use. If A changes a
signature, it must update this section and `docs/client.md`.

```text
ClientRegistry.registerHudLayer(HudLayer)
ClientRegistry.registerScreenFactory(ScreenFactory)
ClientRegistry.registerKeyMapping(KeyMappingSpec, Runnable)
ClientRegistry.registerKeyMapping(KeyMappingSpec)
ClientRegistry.registerTickListener(ClientTickListener)
ClientRegistry.registerTooltipProvider(TooltipProvider)
ClientRegistry.openScreen(String)

// new, game-free (api)
ClientRegistry.registerChunkListener(ClientChunkListener)
interface ClientChunkListener {
    void onChunkLoad(ClientChunkContext context);
    void onChunkUnload(ClientChunkContext context);
    void onBlockUpdate(ClientBlockContext context);
}

// new, game-typed (api-game)
WorldRenderRegistry.register(WorldRenderListener)
interface WorldRenderListener { void render(WorldRenderContext context); }
// context: graphics sink, ClientLevel, Camera, partialTick, projector

// enhanced, game-typed (api-game)
DynamicTextureHandle.create(modId, name, width, height, TextureOptions)
DynamicTextureHandle.uploadRegion(x, y, width, height, int[] argb)
```

The mod must degrade gracefully if an optional hook is absent (for example a
loader built without `api-game`): log once and continue.

## 5. Visual system specification

### Design tokens

- Base grid: 8 px. Spacing: 4/8/12/16/24/32.
- Radius: 8 (small), 12 (medium), 16 (large), 20 (panel), 999 (pill).
- Typography: vanilla font, 1.0x body, 0.85x captions, 1.15x section titles,
  1.5x page titles. Shadow only on maps, not on panels.
- Motion: 120 ms hover, 180 ms tabs, 220 ms panel/dropdown, 260 ms zoom,
  300 ms screen open. Ease-out-cubic default.

### Themes

| Theme | Background | Surface | Accent | Notes |
|---|---|---|---|---|
| Dark | #0B0F14 | #121821 | #5CC8FF | default, cool graphite |
| Light | #F4F6F9 | #FFFFFF | #1D7AF3 | soft grey, dark text |
| Midnight | #080B1A | #10162B | #7C8CFF | indigo glass |
| Emerald | #07130F | #0F1F19 | #37D39B | green glass |
| Amber | #140F07 | #221A0F | #F5A524 | warm dark glass |
| Custom | derived | derived | user | accent drives tints |

Waypoint palette: 12 accessible colors. Player marker uses accent. Death uses
#FF5C6C. Cave uses a cool blue-grey palette. Nether uses warm reds. End uses
desaturated violets.

### HUD anatomy

- Outer soft shadow (3 layers, alpha 0.10/0.08/0.05).
- 1 px border at 18% white (or theme border).
- Circular mask (default) or 12 px rounded square.
- Compass ring with N/E/S/W ticks and a subtle cardinal highlight.
- Player arrow at center: 7x9 vector arrow with a dark outline, smooth yaw
  interpolation (shortest angle), 1.5 px accent dot.
- Waypoint markers: 14 px icon disc with a 2 px ring, label to the right with
  a dark translucent plate; off-screen arrows clamped 10 px inside the edge.
- Entity markers: 4 px category dots (players, hostile, passive, item, other)
  with limits (default 64 HUD, 256 fullscreen).
- Readout strip: coordinates, biome, dimension, zoom, optional time. Two lines
  max, never clipped; 0.85x font.
- Hover: tooltip panel with name, coordinates, distance, group.
- Hidden when: HUD hidden (F1), chat screen open, debug screen open, or
  configured conditions. No double HUD hook.

### Fullscreen anatomy

- Full-screen glass backdrop with a top-left brand mark and a subtle radial
  accent glow.
- Top toolbar 56 px: close, title, search, add waypoint, center player,
  layers, settings.
- Left sidebar 300-340 px: search field, filter chips (dimension/group),
  sort dropdown, waypoint list (cards with icon, name, distance, coordinates,
  eye/delete actions), empty state.
- Map canvas fills the rest; 16 px inset; rounded 20 px; border; inner shadow.
- Right layers panel 260 px, toggled from the toolbar: layer toggles, cave
  controls (mode, Y slider, transparency, surface overlay), entity limits.
- Bottom status bar 36 px: cursor coordinates, biome, dimension, zoom percent,
  waypoint count, FPS/target hint.
- Context menu at cursor: add waypoint, copy coordinates, set death waypoint,
  hide/show layer.
- Pan/drag, wheel zoom to cursor, double-click zoom, arrow keys, Home.
- Waypoint editor modal: name, color, icon, group, visible, temporary,
  delete, save/cancel; drag-to-move with a live ghost.
- Import/export dialog with a text area and validation errors.

### Settings anatomy

- Left tab rail 220 px: General, Appearance, Minimap, Fullscreen Map,
  Waypoints, Cave Mode, Entities, Performance, Compatibility, Advanced.
- Header with title, search, reset section, reset all, import/export.
- Content scroll view with section cards, 8 px grid, description + control
  rows, tooltips on hover.
- Right live preview 300 px: a real HUD render (same renderer as the HUD) plus
  color swatches, opacity preview, and a mini map preview.
- Footer: apply status ("Saved automatically") and a close button.
- All controls are the shared widgets; no vanilla grey buttons.

## 6. Map and cave rendering

### Surface

- 256x256 block regions, per-pixel ARGB + height + biome palette.
- Render fullscreen with one texture per region, linear filtering and mip/
  downsample selection by zoom. At most 2 uploads per frame.
- HUD is CPU-composited into one 160-224 px texture for the circular mask and
  rotation; recompose only when the player moves/turns beyond a threshold,
  zoom animates, or map data changes.
- Shading: slope, water depth, biome tint, optional height contours in the
  fullscreen height layer.

### Cave

- A cave slice at Y shows solid blocks, transparent air, blue-grey cave
  palette, warm Nether palette, violet End palette. Air openings are the
  negative space; floors are brightened by neighbor checks.
- Auto mode scores candidate Y values by walkable air-floor count near the
  player, penalizes lava/water and void, and refuses to pick the surface.
- Manual mode uses a Y slider and a depth readout.
- Surface overlay blends the surface map at configurable alpha.
- Separate slice cache and optional `.wslice` gzip storage; atomic writes.
- Transitions cross-fade 180 ms; no FPS spike; slices load async.

## 7. Waypoints, entities, keybinds

- All 11 keybinds, conflict detection, tooltips in the UI.
- Death waypoint auto-created once per death on the client; temporary
  waypoints expire by configurable age.
- Off-screen arrows on HUD.
- Beams in the world via the loader world render hook (`gizmos.line`),
  vertical 64-96 px columns with a fading top; only for visible waypoints in
  the current dimension and within configurable distance.
- Entity markers: player, hostile, passive, item, other; icons; max counts;
  distance sort; optional labels.
- Chunk grid and slime chunks (slime chunks only when the client can read the
  seed from an integrated server; otherwise the toggle is disabled with a
  tooltip explaining why).
- World border and spawn toggles.

## 8. Performance budgets

| Scenario | Target |
|---|---|
| HUD idle | <= 1% frame time, <= 2 texture uploads/frame |
| Fullscreen | <= 3 ms extra on a mid-range GPU |
| 100 waypoints | no measurable HUD regression |
| 500 entities | fullscreen <= 3 ms, HUD <= 0.5 ms |
| Cave mode | <= 1 ms render overhead, async scans |
| Zoom out | mip/downsample path, no per-frame allocations |

Every budget is measured, not asserted. Record numbers in the final report.

## 9. Verification gates

After each phase:

```powershell
cd D:\Weaver-v2\examples\minimap
..\..\gradlew.bat clean test weaverBuild

cd D:\Weaver-v2
.\gradlew.bat :api:test :core:test :api-game:test --rerun-tasks --no-daemon
powershell -NoProfile -ExecutionPolicy Bypass -File tools\verify.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\verify-arabic.ps1
```

Client proof:

```powershell
cd D:\Weaver-v2\examples\minimap
.\play.ps1
```

Markers required: `MINIMAP HUD rendered`, `TEXTURE ok`, `SCREEN rendered`,
`SETTINGS rendered`, `WAYPOINT ok`. Add `CAVE ok`, `FULLSCREEN ok`,
`WORLD OVERLAY ok`, `TOOLTIP ok`, `CHUNK EVENTS ok`, `TEXTURE PARTIAL ok`.

Screenshots: HUD, fullscreen, settings, cave mode, waypoint editor. Opening and
inspecting each image is mandatory.

## 10. Deliverables

- Working mod in `examples/minimap`; jars in `build/libs` and
  `build/weaver/mods`.
- `play.ps1` that stages, launches, and leaves the game open.
- Screenshots under `examples/minimap/build/screenshots` and a curated copy
  in `docs/images/minimap-v2/`.
- `examples/minimap/README.md` and `docs/minimap-v2.md` with features, keys,
  settings, paths, limits, how to run, and loader additions.
- Final report in the conversation: done, changed, tests, images, deferred,
  key files.

## 11. Explicit non-goals for this pass

- Server sync or waypoint sharing (design only, no protocol).
- Custom map shaders or a new render pipeline.
- Replacing the vanilla HUD or adding a second HUD mixin.
- Map rotation beyond smooth player-up yaw.
- Runtime mod dependency on Sodium/Iris/Xaero.
