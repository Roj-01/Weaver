# Lookups: item, fluid, and energy handlers

Lookups let one mod ask "what can I do with this block, entity, or item
stack?" without knowing the mod that made it. A pipe asks the block next to
it for an `ItemHandler`; a charger asks a held item for an `EnergyHandler`.
The answering mod registers a provider. Everything is in `api-game`,
package `net.weaver.api.game.lookup`.

Lookups are separate from [capabilities](capabilities.md): a capability is
a stored value attached to a holder; a lookup is a question answered by
code. A provider is free to build its answer from capability values (see the
battery example below).

## Standard lookups

`Lookups` holds the lookups mods should share:

| Constant | Type | Context |
|---|---|---|
| `BLOCK_ITEMS` | `BlockLookup<ItemHandler, Direction>` | side accessed, or `null` |
| `ENTITY_ITEMS` | `EntityLookup<ItemHandler, Direction>` | side, or `null` |
| `ITEM_ITEMS` | `ItemLookup<ItemHandler, Void>` | always `null` |
| `BLOCK_FLUIDS`, `ENTITY_FLUIDS`, `ITEM_FLUIDS` | `FluidHandler` | as above |
| `BLOCK_ENERGY`, `ENTITY_ENERGY`, `ITEM_ENERGY` | `EnergyHandler` | as above |

Their ids are `weaver:items`, `weaver:fluids`, and `weaver:energy`.

## Finding

```java
ItemHandler target = Lookups.BLOCK_ITEMS.find(level, pos.relative(facing), facing.getOpposite());
if (target != null) {
    ItemStack rest = target.insertAnywhere(stack, false);
}
```

- `BlockLookup.find(level, pos, context)` returns `null` for an unloaded
  position without loading the chunk. An overload takes the state and block
  entity when the caller already has them.
- `EntityLookup.find(entity, context)` and `ItemLookup.find(stack, context)`
  work the same way; an empty stack gives `null`.
- Nothing is cached. Find again when you need the handler; do not keep it
  across ticks, since the block, entity, or stack can change.

## Providing

```java
// For a block (whatever its block entity)
Lookups.BLOCK_ENERGY.registerForBlocks(
        (level, pos, state, blockEntity, side) -> generatorEnergy(level, pos), GENERATOR);

// For a block entity type; return null for sides with no access
Lookups.BLOCK_ITEMS.registerForBlockEntities(
        (machine, side) -> side == Direction.UP ? machine.input() : machine.output(), MACHINE_TYPE);

// For an entity type
Lookups.ENTITY_FLUIDS.registerForEntities((tanker, side) -> tanker.tank(), TANKER_TYPE);

// For an item
Lookups.ITEM_ENERGY.registerForItems((stack, context) -> new BatteryEnergy(stack), BATTERY);
```

Register during mod setup. Providers are asked in order, and the first
non-null answer wins:

1. block lookups: providers for the block, then for the block entity's
   type, then fallbacks;
2. entity lookups: providers for the entity type, then fallbacks;
3. item lookups: providers for the item, then fallbacks.

A provider returns `null` to pass, for example for a side it does not
expose. `registerFallback` adds a provider asked for everything after the
specific ones.

## Vanilla containers

`BLOCK_ITEMS` and `ENTITY_ITEMS` answer for vanilla without any
registration, through fallbacks:

- a block entity that is a `Container` (chests, barrels, hoppers, furnaces,
  dispensers, shulker boxes, ...); a double chest is one 54-slot handler;
- a block that is a `WorldlyContainerHolder` (the composter);
- an entity that is a `Container` (chest and hopper minecarts, chest boats).

`ContainerItemHandler` does the wrapping and can wrap any `Container`. With
a side and a `WorldlyContainer`, only that side's slots are visible and the
container's per-side rules apply, as for hoppers: a furnace's top is its
input slot and its bottom its result and fuel slots. A provider registered
for the block or block entity type replaces the fallback.

Vanilla cauldrons are block fluid handlers (also a fallback): an empty
cauldron takes a full bucket (1000 mB) of water or lava, and a full water or
lava cauldron gives one back, the way a player with a bucket does. A water
cauldron one or two thirds full reports 333 or 667 mB but gives nothing, since
vanilla moves its water a bottle at a time. There are no vanilla energy
handlers.

### Buckets and other item fluid containers

An item handler works on the stack it is given and cannot turn a bucket into
a water bucket, so buckets are served by `FluidContainers` instead. Each call
works on one item of the stack and returns what that item becomes; the caller
puts it in place (shrink the stack by one and give the result):

```java
FluidContainers.Result result = FluidContainers.fill(stack, FluidAmount.of(Fluids.WATER, 1000), false);
if (result.moved()) {
    stack.shrink(1);
    player.getInventory().placeItemBackInInventory(result.container());
}
```

- `contents(stack)`, `drain(stack, maxAmount, simulate)`, and
  `fill(stack, offer, simulate)` handle every `BucketItem`, vanilla and modded,
  except buckets holding a mob: emptying a fish bucket into a tank would delete
  the fish.
  A modded fluid's `getBucket()` is its filled bucket.
- Other items fall back to their `ITEM_FLUIDS` handler, working on a copy of
  one item.
- A bucket moves exactly 1000 mB, never part of it.

## Handlers

`ItemHandler`: `slots()`, `stack(slot)` (read only), `insert(slot, stack,
simulate)` returning what did not fit, `extract(slot, amount, simulate)`
returning what was taken, `limit(slot)`, `canPlace(slot, stack)`, and
`insertAnywhere(stack, simulate)`, which tops up matching slots before empty
ones. The handler copies what it takes; callers keep ownership of the stack
they pass.

`FluidHandler`: `tanks()`, `fluid(tank)`, `capacity(tank)`, `accepts(tank,
fluid)`, `fill(offer, simulate)` returning the amount accepted,
`drain(request, simulate)` for a specific fluid and `drain(amount,
simulate)` for any. Amounts are `FluidAmount(fluid, amount)` in millibuckets
(`FluidAmount.BUCKET` is 1000).

`EnergyHandler`: `stored()`, `capacity()`, `insert(amount, simulate)` and
`extract(amount, simulate)` returning what moved, `canInsert()`,
`canExtract()`. Units are whatever the mods agree on.

With `simulate` every call reports what would happen and changes nothing.

Ready implementations: `SimpleEnergyHandler` (capacity, per-call limits),
`SimpleFluidTank` (one tank, optional filter), and `ContainerItemHandler`.
The simple ones call `onChanged()` after every real change; override it to
mark a block entity changed or save the value.

## Example: a battery item

The handler is built per call from a capability value on the stack, and
writes back on change, so the energy is saved with the stack and copies with
it:

```java
Capability<Long> ENERGY = AttachmentType.builder("examplemod:energy", Long.class)
        .codec(DataCodecs.varLong(), 12).build();

Lookups.ITEM_ENERGY.registerForItems((stack, context) -> {
    Long stored = AttachmentRegistry.global().get(stack, ENERGY);
    return new SimpleEnergyHandler(10_000, 100, 100, stored == null ? 0 : stored) {
        @Override
        protected void onChanged() {
            AttachmentRegistry.global().set(stack, ENERGY, stored());
        }
    };
}, BATTERY);
```

## Your own lookups

```java
BlockLookup<HeatSource, Direction> HEAT = BlockLookup.get(
        Identifier.fromNamespaceAndPath("examplemod", "heat"), HeatSource.class, Direction.class);
```

`BlockLookup.get`, `EntityLookup.get`, and `ItemLookup.get` return the same
lookup for the same id and types, so two mods that agree on an id and an
interface share a lookup without depending on each other. The same id with
different types throws `IllegalArgumentException`. Use `Void.class` as the
context type when there is no context.

## Threading and sides

Lookups and handlers are not synchronized. Use them on the thread that owns
the block, entity, or stack (the server thread for server levels).
Registration is safe at any time, but providers registered late miss earlier
finds. Handlers do not sync anything to clients; a block entity that shows
its contents on the client syncs them the vanilla way.

## Tests and proof

`SimpleEnergyHandlerTest` covers the energy handler. The server proof
(`GameEventsLookupProof`) checks a chest (insert, simulate, remainder,
extract), furnace sides, a chest minecart and a pig, providers for a block,
a block entity type with a side that passes, an entity type, a water-only
tank, an item battery whose energy survives a stack copy, and shared and
conflicting ids.

## Limitations

- Only `BlockLookupCache` caches (see [Cached lookups](#cached-lookups)); other
  lookups find again each time.
- Cauldrons move whole buckets only, not bottles.
- No transactions across several handlers: simulate first, then perform.
- Fluid amounts carry no data components.

## Cached lookups

Code that asks the same neighbour every tick (pipes, conveyors, kinetic
networks) should hold a `BlockLookupCache` instead of calling `find` each
time:

```java
BlockLookupCache<ItemHandler, Direction> next = BlockLookupCache.create(
        Lookups.BLOCK_ITEMS, level, pos.east(), Direction.WEST, this::onNeighbourChanged);
ItemHandler handler = next.get(); // answered from the cache until something changes
```

The cache is invalidated automatically when the block or block entity at the
position is replaced or removed, or its chunk unloads; the optional listener
runs then (rebuild your network there). A provider whose answer changes
without the block changing (a machine switching its output side) calls
`BlockLookupCache.invalidate(level, pos)`. Caches hold the level weakly and
cost nothing when no mod uses them. Use them from the level's own thread.
