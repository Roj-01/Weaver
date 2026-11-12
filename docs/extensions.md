# Block, item, and entity extensions

Vanilla often reads a fixed value, a tag, or a hard-coded block or item where a
mod wants to decide by position and context. Examples: ladders come from the
`climbable` tag, slime and honey are named in the piston code, gold ingots are
named in the piglin code. The interfaces in `net.weaver.api.game.extension` let
a mod's own `Block`, `Item`, or `Entity` subclass decide these.

A subclass implements the interface and overrides only the methods it needs.
Every default is vanilla's own behavior, so implementing an interface changes
nothing by itself.

```java
public class RopeBlock extends Block implements BlockExtension {
    @Override
    public boolean isClimbable(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        return true;
    }
}

public class WrenchItem extends Item implements ItemExtension {
    @Override
    public boolean usesBlockWhileSneaking(ItemStack stack, Level level, BlockPos pos, Player player) {
        return true; // sneak-click still reaches the machine
    }
}
```

Weaver asks the block, item, or entity involved: the block an entity stands in
or on, the soil under a plant, the stack in a hand or on the ground. The
methods run on the thread of the vanilla logic they replace.

Some methods return a `TriState`: `TRUE` or `FALSE` decide, and `DEFAULT` keeps
vanilla's answer.

## BlockExtension

| Method | Where vanilla asks | Default |
|---|---|---|
| `isClimbable(state, level, pos, entity)` | a living entity inside the block climbs it | in `minecraft:climbable` |
| `getFriction(state, level, pos, entity)` | living entities, dropped items, and experience orbs moving on it | the block's friction |
| `canSustainPlant(soil, level, pos, plant)` | a plant built on `VegetationBlock` (flowers, grass, saplings, crops) stays or is placed on it | `DEFAULT`: the plant's rule |
| `getExplosionResistance(state, level, pos, explosion)` | every explosion | the block's resistance |
| `isFireSource(state, level, pos, side)` | fire on top burns forever | in the dimension's infiniburn tag |
| `isStickyBlock(state)` | a piston moving it pulls its neighbors, like slime | false |
| `canStickTo(state, other)` | a sticky block and its neighbor stick together | true |
| `connectsToRedstone(state, level, pos, direction)` | redstone dust next to it connects | `DEFAULT`: vanilla's rule |
| `makesOpenTrapdoorAboveClimbable(state, level, pos, trapdoor)` | an open trapdoor right above it is a ladder, like above a vanilla ladder | false |
| `getFlammability(state)` | how likely fire burns it away (planks 20) | 0: vanilla's value |
| `getFireSpreadSpeed(state)` | how likely fire spreads onto it (planks 5) | 0: vanilla's value |
| `onBurnedByFire(state, level, pos)` | after fire burned it away, where vanilla primes TNT | nothing |
| `getPathType(state, level, pos)` | how walking mobs see it when finding a path (`DAMAGING` is avoided) | null: vanilla's type |
| `getEnchantPowerBonus(state, level, pos)` | the power it gives a nearby enchanting table, like a bookshelf (1), with the same placement rules | 0: vanilla's rule |
| `addDestroyEffects(state, level, pos)` | it breaks, before vanilla's break particles; true replaces them (client) | false |
| `addHitEffects(state, level, pos, side)` | a player mines it, before the crack particles; true replaces them (client) | false |
| `addRunningEffects(state, level, pos, entity)` | an entity sprints over it, before the running particles; true replaces them | false |
| `addLandingEffects(state, level, pos, entity, count)` | an entity lands on it after a fall, before the landing particles; true replaces them (server) | false |
| `getBeaconColor(state, level, pos, beaconPos)` | for a block that is also a `BeaconBeamBlock`: the ARGB color it gives the beam | null: its dye color |
| `canHarvestBlock(state, level, pos, player)` | a player mines it at full speed and gets its drops, decided by position | `DEFAULT`: the state and the tool |
| `shouldDisplayFluidOverlay(state, level, pos, fluid)` | a fluid next to it draws its overlay on the face toward it, as next to glass (client) | false |
| `getSoundType(state, level, pos, entity)` | its sounds by position: stepping, landing, placing, breaking, mining (entities with step sounds of their own, most animals, do not ask) | the state's sound type |
| `hidesNeighborFace(state, neighbor, side)` | it hides the face of a neighbor that touches it, for blocks drawn over their neighbors (client) | false |

## ItemExtension

| Method | Where vanilla asks | Default |
|---|---|---|
| `onItemEntityTick(stack, entity)` | every tick of a dropped stack; true replaces the whole tick | false |
| `getItemEntityLifespan(stack, level)` | ticks before a dropped stack despawns | 6000 |
| `isPiglinCurrency(stack)` | piglins take it to barter | a gold ingot |
| `usesBlockWhileSneaking(stack, level, pos, player)` | a sneaking player clicking a block still uses the block (server and client) | false |
| `canBeDropped(stack, player)` | the drop key drops the selected stack (server and client) | true |
| `causesReequipAnimation(from, to)` | the first-person hand lowers and rises when the held stack changes (client) | true |
| `allowsWalkingOnPowderSnow(stack, wearer)` | worn on the feet, it keeps the wearer on powder snow, like leather boots | false |
| `canPerformAction(stack, action)` | it can do what vanilla lets only one item do (see below) | false |
| `onEntitySwing(stack, entity, hand)` | an entity starts swinging with it; true cancels the swing (both sides) | false |
| `onLeftClickEntity(stack, player, target)` | a player attacks with it, before damage; true cancels the attack (both sides) | false |
| `shouldResetBlockBreaking(from, to)` | mining starts over when the held stack changes; asked only when vanilla would restart (client) | true |
| `getCraftingRemainder(stack)` | what stays in the crafting grid in its place (a hammer that lost durability); return a new stack | the item's fixed remainder |
| `createItemEntity(level, entity, stack)` | a plain dropped stack is about to be added; return the entity to add instead, copying motion, pickup delay, and owner from it (server) | null: the plain entity |
| `onStopUsing(stack, entity, remainingTicks)` | an entity stops using it for any reason: released, finished, or interrupted | nothing |
| `getHighlightTip(stack, name)` | the name shown above the hotbar when it is selected (client) | the hover name |

`causesReequipAnimation` is asked only when vanilla would play the animation, so
it can only keep the hand still: an item whose energy bar changes every tick
returns false.

### Item actions

`ItemAction` names something vanilla lets only one item do. `ItemAction.SHEAR`
covers every place that checks for shears:

- shearing sheep, mooshrooms, snow golems, copper golems, sulfur cubes, and
  bogged;
- taking honeycomb from beehives and carving pumpkins;
- breaking tripwire without setting it off, and cutting leads;
- shearing from a dispenser, for an item without a dispenser behavior of its
  own.

Leaves, cobwebs, and other blocks that drop themselves only with shears decide
that in their loot tables, which match the shears item. Give those tables a
`match_tool` entry for your item.

`ItemAction.of("examplemod:drill")` makes a mod's own action, the same object
for every caller, so mods can check each other's tools.

### Client item behavior

`net.weaver.api.game.client.model.ClientItemExtension` holds what only the client
asks an item:

| Method | Where vanilla asks | Default |
|---|---|---|
| `getArmPose(stack, entity, hand)` | the arm pose of a player or humanoid mob holding it | null: vanilla's pose |
| `getHumanoidArmorModel(stack, slot, original)` | the model it is drawn with when worn, instead of vanilla's armor model; posed from the wearer and textured from its equipment asset; show only the parts for the slot | null: vanilla's model |

A mod can add a pose of its own: add a constant to `HumanoidModel.ArmPose`
([extensible enums](enums.md), constructor `(ZZ)V`: two-handed, affects the off
hand), then register how it moves the arm with `ArmPoses.register(pose,
(model, state, arm) -> ...)`. The transformer runs after vanilla set the arm up.

Items drawn in a special way (in hand, in the inventory) use a special model
renderer of their own, registered through `ModelLoaderRegistry` (see
[client](client.md)); worn textures come from the item's equipment asset
(`assets/<namespace>/equipment/<id>.json`).

## EntityExtension

| Method | Where vanilla asks | Default |
|---|---|---|
| `canTrampleFarmland(state, level, pos, fallDistance)` | the entity landing on farmland turns it to dirt; asked before `FarmlandTrampleEvent` | true |
| `walksOnPowderSnow()` | the entity walks on powder snow | false (the `powder_snow_walkable_mobs` tag still applies) |
| `onAddedToLevel()` | the level starts tracking it: added, or its chunk loaded (both sides) | nothing |
| `onRemovedFromLevel()` | the level stops tracking it: removed, or its chunk unloaded (both sides) | nothing |
| `shouldRiderSit()` | for a vehicle: humanoid riders are drawn sitting; false draws them standing (client) | true |

## Proofs

The server proof registers `weaverproof:extension_block` and
`weaverproof:extension_item`, plus a pig subclass, and checks each method
where vanilla reads it: a pig climbs the block, friction is asked for the pig
moving on it, a poppy survives on it, an explosion leaves it standing, fire on
it is a fire source, a piston pulls a side block along, dust connects to it,
the item despawns after 20 ticks, piglins take it, a sneaking player still uses
the block with it, the drop key keeps it, the pig does not trample farmland,
the pig and the boots walk on powder snow, the item shears a sheep, carves a
pumpkin, and gets the shears dispenser behavior, an open trapdoor above the
block is a ladder, the item cancels its swing and its attack, and fire reads the
block's flammability and tells it when it burned away, the item leaves a bowl
in the crafting grid, a named stack of it drops as its own entity, mobs see the
block as damaging, and one block next to an enchanting table gives the top
cost, the block replaces the running and landing particles of a pig, a
beacon beam through it takes its color, stopping a use reaches the item, the
block is mined slower and drops nothing at a position where it cannot be
harvested, the level tells the pig when it is added and removed, and stepping
and landing on the block ask it for its sound.
Each check compares with a
vanilla block, item, or pig in the same place. The client proof checks that
two stacks of an item with `causesReequipAnimation` false swap without the
animation, and that changing such an item keeps the mining progress, while
sticks do neither. It also checks that a block replaces its break and crack
particles, that a zombie on a vehicle with `shouldRiderSit` false is drawn
standing, that the client level reports tracking, and that the HUD asks the
item for the name above the hotbar, that water next to a
see-through block that asks for it draws its overlay, that breaking and mining
a block ask it for its sound, that a block hides the face of a neighbor, and
that an item picks an added arm pose for a player and a zombie, with its
transformer moving the arm, and that a zombie wearing the item as a helmet is
drawn with the item's own armor model.

### Things that need no extension in 26.3

Some NeoForge extension methods have data-driven counterparts in 26.3:

- stripping, path making, and tilling are `block_transformer` data files
  (`data/<namespace>/block_transformer/`): a mod adds its blocks there;
- furnace fuel is the `cooking_fuel` item component;
- crops grow faster on blocks in `minecraft:grows_crops` with a `moisture`
  property;
- nether portal frames are `minecraft:nether_portal_frame`, and conduit
  frames `minecraft:conduit_effect_block`;
- bubble columns rise over `minecraft:enables_bubble_column_push_up` and sink
  over `minecraft:enables_bubble_column_drag_down`;
- the enderman stare uses `minecraft:gaze_disguise_equipment`, and gliding the
  `glider` item component;
- beds: every vanilla check (sleeping, occupancy, standing up, respawning) uses
  `AbstractBedBlock`, so a mod bed extends it and implements
  `getBedEnvironmentAttribute`, `destroyOnUse`, and `destroyOnLeave`. The server
  proof sleeps in a mod bed, checks it is occupied and freed, and respawns at it.

New constants for vanilla enums are in [Extensible enums](enums.md).

## Client data generator

The vanilla client data generator checks that every registered item and block
got a model from it. It only makes vanilla's models, so a mod item or block failed
the check ("Missing item model definitions"). Weaver limits the check to the
`minecraft` namespace; mod models stay in the mod's own files. The client proof
runs the data generator with a mod item installed.

## Compared with NeoForge

NeoForge adds more such methods to its patched `Block`, `Item`, and `Entity`
classes. Weaver covers the ones mods use, and 26.3 covers several others with
data and tags (above). For anything else, use the matching event (see
[hook inventory](hook-inventory.md)) or a mixin of your own.

## Related

- [Entities](entities.md)
- [Events](events.md)
- [Lookups](lookups.md)
