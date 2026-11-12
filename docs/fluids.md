# Fluid types

A modded fluid in vanilla 26.3 is only a block and a fluid. Water and lava have
their behavior written into the game: swimming, drag, drowning, currents, fog,
and bucket sounds all check `FluidTags.WATER` or `FluidTags.LAVA`. A third fluid
that is in neither tag does none of this: entities fall through it as if it
were air.

`net.weaver.api.game.fluid.FluidType` describes how entities behave in a
modded fluid. `FluidTypes.register(tag, type)` attaches the type to every fluid
in a fluid tag, so the source and flowing forms share one type.

## Registering a fluid type

```java
static final TagKey<Fluid> GOO = TagKey.create(Registries.FLUID,
        Identifier.fromNamespaceAndPath("examplemod", "goo"));

// During the register phase, after the fluids and the liquid block:
FluidTypes.register(GOO, FluidType.builder()
        .speed(0.01)            // push per tick from movement input (water: 0.02)
        .drag(0.4, 0.4)         // velocity kept per tick, horizontal and vertical (water: 0.8)
        .canDrown(true)         // entities with eyes inside lose air
        .fog(0x4A9A2E, 0.0F, 3.0F) // fog color and distances when the camera is inside
        .emptySound(SoundEvents.BUCKET_EMPTY)
        .build());
```

Put the fluids in the tag with a data file,
`data/examplemod/tags/fluid/goo.json`:

```json
{ "values": ["examplemod:goo", "examplemod:flowing_goo"] }
```

`register` rejects the vanilla `water` and `lava` tags, because vanilla already
handles them. Register types before entities exist (the register phase). The
tag set an entity tracks is fixed when the entity is created.

## What a fluid type changes

| Property | Builder method | Default | Effect |
| --- | --- | --- | --- |
| Current strength | `currentStrength` | 0.014 | how strongly the fluid's flow pushes entities |
| Speed | `speed` | 0.02 | movement added per tick from input while inside |
| Drag | `drag(horizontal, vertical)` | 0.8, 0.8 | share of velocity kept each tick |
| Swimming | `canSwim` | true | living entities float up and travel as in a fluid |
| Drowning | `canDrown` | true | an entity with its eyes inside loses air |
| Fall reset | `resetsFallDistance` | true | entering the fluid clears fall distance |
| Fog | `fog(rgb, start, end)` | none | client fog while the camera is in the fluid |
| Bucket sound | `emptySound` | none | the sound when a bucket of the fluid is emptied |

The hooks behind these are mixins on `Entity`, `LivingEntity`, `BucketItem`,
the client `Camera`, and `FogRenderer`:

- Every entity's fluid tracker includes the modded tags, so `getFluidHeight`,
  `isInFluid`, and `isEyeInFluid` work for them.
- `LivingEntity.travelInFluid` moves an entity with the type's speed and drag.
- `aiStep` treats the fluid like water for jumping and floating.
- `baseTick` loses air when `canDrown` is true and the eyes are inside.

## Queries

- `FluidTypes.of(fluid)` returns the fluid's type, or null for vanilla and
  untyped fluids.
- `FluidTypes.inside(entity)` returns the tag of the typed fluid the entity is
  in.
- `FluidTypes.swimmable(entity)`, `swimHeight(entity)`, and `drowns(entity)`
  give the swim and air state.
- `FluidTypes.all()` returns every registered type.

## Modded block and fluid states

Vanilla gives each block state and fluid state a global id, and builds each
block state's cache (its fluid, shapes, and light), only for its own blocks
during bootstrap. Without more work, states of mod blocks would have no id
(they could not be sent to clients) and no cache. A modded `LiquidBlock` would
then hold no fluid.

Weaver adds every missing state to `Block.BLOCK_STATE_REGISTRY` and
`Fluid.FLUID_STATE_REGISTRY`, and builds the block state caches, when the block
and fluid registries freeze. The log line is
`MOD BLOCK STATES registered=N (ids and caches)`. Block items registered
directly (not through `Items`) are linked to their blocks when the item
registry freezes (`MOD BLOCK ITEMS linked=N`), so `Block#asItem`, pick block,
and drops find them. Mods do not need to do either themselves.

## Proof

The server proof registers a real fluid (`weaverproof:goo`, source and flowing,
with its liquid block and tag). It fills a column with it, drops a pig in, and
checks that the pig is inside, that its fall is slowed by the drag, and that it
loses air (`fluid type ... air=300->290`).

## Limitations

- Fluid rendering (textures and tint in the world) still comes from resources:
  the client's fluid models and `RegisterFluidModelsEvent`.
- Boats, fishing, and other vanilla code that checks the water tag directly do
  not treat a modded fluid as water.
- An entity created before its fluid type was registered does not track that
  fluid.

## Related

- [Registry API](registry.md) - registering the fluids and the block, and the
  shared `c:` tags.
- [Client API](client.md) - fog and fluid models.
