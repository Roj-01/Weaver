# The NeoForge-shaped library (`weaver-nf`)

`net.weaver.nf` lets code written the NeoForge way run on Weaver: the same class
names and method shapes (`DeferredRegister`, `DeferredHolder`, `DeferredItem`,
`ModConfigSpec`, `PartEntity`, `EntityRenderersEvent`, ...), each implemented on
Weaver's own APIs. Porting a mod that used a private copy of these classes
means changing its imports to `net.weaver.nf` and rebuilding.

It ships with Weaver in the agent's `lib` folder as `weaver-nf-<version>.jar`,
so there is nothing to bundle. Compile against it next to `api-game`.

This is Weaver's own code. It follows NeoForge's names and behavior but does
not copy NeoForge's source.

## Why one library

Mods that ported from NeoForge each carried their own copy of these classes
(`<mod>/nf/...`), about 16 copies in the mods tested. A bug in one copy had to be
fixed in each one. The deferred-holder bug was the worst of them. A copy's
`DeferredHolder` was a holder of its own, not the registry's. For registries
without intrusive holders (attributes, mob effects, sounds, data components),
anything keyed by holder missed it: attribute maps, effect maps, `is(holder)`
checks. An attribute registered this way read as absent, for example. Here the
registry adopts the `DeferredHolder` itself when the entry is registered (see
[Holders](#holders)), so the fix is in one place.

## What it covers

| Area | Classes | Backed by |
|---|---|---|
| Registration | `DeferredRegister` (+ `.Items`, `.Blocks`, `.DataComponents`, `.Entities`), `DeferredHolder`, `DeferredItem`, `DeferredBlock` | vanilla registries, Weaver `RegisterEvent` |
| Mods and server | `Mods.isLoaded`, `Servers.get` | `WeaverMods`, server lifecycle events |
| Config | `ModConfigSpec` (builder, `define`, `defineInRange`, `defineEnum`, push/pop), `ModConfig.Type` | Weaver config (`SERVER`/`SYNCED` sync to clients) |
| Network | `Net.playToServer/playToClient/playBidirectional`, `sendToServer/Player/TrackingEntity...`, `Net.Context` | Weaver `NetworkRegistry` |
| Entity data | `PersistentData.of(entity)` (NeoForge's `getPersistentData()`) | a Weaver attachment, saved; on a player's death only the `PlayerPersisted` tag carries over |
| Attributes and spawns | `EntityAttributes.register`, `SpawnPlacementsEvent` | Weaver entity attributes and spawn rules |
| Events | `IncomingDamage`, `Respawned`, `Hooks.canEntityGrief/onProjectileImpact/onAnimalTame` | Weaver game events |
| Spawn data | `IEntityWithComplexSpawn` | one channel per mod, sent when a player starts tracking |
| Blocks | `BlockEntityBlocks.add` (more valid blocks for a block entity type) | `BlockEntityValidBlocks` |
| Packs | `ExtraPacks.addClientPack/addDataPack` (a folder inside the mod jar) | `ModPacks.addExtraPack` |
| Multipart | `Multipart`, `PartEntity`, `PartLink`, `Parts` | Weaver multipart entities |
| Biome modifiers | `"type": "neoforge:add_spawns"` files | Weaver biome modifiers |
| Client | `ClientKeys`, `ClientCommands`, `ClientModels`, `EntityRenderersEvent`, `ComputeFov`, `CameraAngles`, `FogColor`, `FogDistance` | Weaver client registries |
| Sky, clouds, weather | `Environment`, `CustomSkyboxRenderer`, `CustomCloudsRenderer`, `CustomWeatherEffectRenderer` | `EnvironmentRenderers` |

The event-bus emulation that one mod carried (`NeoForge.EVENT_BUS`,
`IEventBus`, and about 70 event classes) is not part of the library yet. Mods
that use it keep their copy, or move to Weaver's `EventBus` and game events.

## Registration

```java
public final class MyMod implements WeaverMod {
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("mymod");
    static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, "mymod");

    static final DeferredItem<Item> RUBY = ITEMS.registerSimpleItem("ruby");
    static final DeferredHolder<Attribute, Attribute> POWER = ATTRIBUTES.register("power",
            () -> new RangedAttribute("attribute.mymod.power", 1.0, 0.0, 100.0));

    @Override
    public void onInitialize(ModContext context) {
        // Nothing else to call: each register fills its registry when Weaver's
        // RegisterEvent for that registry fires. register(bus) is kept as a no-op.
    }
}
```

`DeferredRegister.flushAll()` registers every pending entry right away, for code
that needs them before the registry events.

### Holders

- **Registries without intrusive holders** (attributes, mob effects, sounds,
  data components, and most others): the registry adopts the `DeferredHolder` as
  the entry's own holder. `BuiltInRegistries.ATTRIBUTE.get(key).get() == POWER`
  is true, so the holder works as a map key, in tags, and in codecs.
- **Registries whose values carry their own holder** (items, blocks, entity
  types): the `DeferredHolder` forwards to the registry's holder once the entry
  is registered. `getDelegate()` returns that holder.

Holders can be made at any time. That includes `onInitialize`, which runs while
the built-in registries are still being created.

## Attributes

```java
EntityAttributes.register(event -> event.put(SERPENT.get(), Mob.createMobAttributes()
        .add(Attributes.MAX_HEALTH, 40.0).add(POWER, 5.0).build()));
```

Call it from `onInitialize` or `onRegister`, as NeoForge mods do from their mod
constructor. NeoForge fires its attribute event after the registries are filled,
and the library does the same: a registration made before the game's bootstrap is
done waits for the first common setup, and one made later runs at once. At that
point the mod's entity types are registered and vanilla's attribute holders are
bound, so the supplier can be built. A registration that throws is logged
(`[weaver-nf] ENTITY ATTRIBUTES FAILED`) and the others still run.

## Keys

`ClientKeys.register(mapping)` can be called from `onClientSetup`, before the game
has made its options. The library adds each key once the options exist: it adds
the key's category, appends the key to the controls screen, and restores the
binding the player saved in `options.txt`.

## Entity data

`PersistentData.of(entity)` is saved with the entity. When a player dies or
returns from the End, only the `PlayerPersisted` sub-tag
(`PersistentData.PERSISTED`) is copied to the new player, as in NeoForge.
Keep anything that must survive death inside that tag.

## Config

```java
ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
builder.push("general");
ModConfigSpec.IntValue charge = builder.comment("Charge per use").defineInRange("charge", 7, 0, 64);
builder.pop();
builder.build().open(context, ModConfig.Type.COMMON, "mymod");
```

`open` is the Weaver step that NeoForge does with `registerConfig`: it binds the
spec to a Weaver config file for the mod's side. `SERVER` and `SYNCED` values
are sent to clients. `translation`, `worldRestart`, and `gameRestart` are kept
and do nothing.

## Multipart entities

```java
public class Serpent extends PathfinderMob implements Multipart {
    private final Segment[] parts = {new Segment(this, 1.0F), new Segment(this, 0.5F)};

    public Segment[] getParts() { return parts; }
}

public class Segment extends PartEntity<Serpent> {
    private final EntityDimensions size;

    public Segment(Serpent parent, float width) {
        super(parent);
        size = EntityDimensions.scalable(width, width);
        refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) { return size; }
}
```

The parts are Weaver `PartEntity`s. Weaver tracks them with the parent, makes
them hittable, finds them in `level.getEntities`, and gives client parts their
ids. A part that does not override `hurtServer` passes its damage to the parent.
`PartLink.whole(entity)` gives the creature behind any hitbox, the ender dragon's
included.

## Sky, clouds, and weather

```java
Environment.registerSkyboxRenderer(Identifier.fromNamespaceAndPath("mymod", "ruin"), new RuinSky());
```

The renderer of the current dimension type is asked before vanilla draws the
sky, the clouds, or the rain and snow (with and without improved transparency),
and before vanilla ticks the rain. When it returns `true`, vanilla skips that
step. These are Weaver's `EnvironmentRenderers` with NeoForge's names, so a
mod needs no mixins of its own for them.

## Stability

`weaver-nf` is outside the frozen API (see [API stability](api-stability.md)).
It follows NeoForge's shapes, and may change when they need fixing during the
2.2 betas. The Weaver APIs it is built on (`EnvironmentRenderers`,
`BlockEntityValidBlocks`, `ModPacks.addExtraPack`, `PartEntity`) are part of the
frozen API.

## How it is tested

The server proof (`examples/proof`, `NfProof`) is a mod written against the
library the NeoForge way. On a real server it checks four things:

- **Holders.** The registry adopted the attribute and data component holders.
  They pass through codecs, and the item and block holders are bound.
- **Plain helpers.** `Mods`, `Servers`, `ModConfigSpec`, `BlockEntityBlocks`,
  and `PersistentData` work.
- **Multipart.** The parts are seen by `level.getEntities` and by `Parts`, they
  link back to the parent, they have their own size, a hit on a part damages the
  parent, and the parent reads its attribute through the deferred holder.
- **Biome modifiers.** A `neoforge:add_spawns` file adds its spawner to the
  right category.

The client proof registers the multipart mob's renderer through
`EntityRenderersEvent`.
