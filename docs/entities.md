# Entities

The entity APIs in `net.weaver.api.game.entity` cover what a mod with its own
mobs needs beyond registering the entity type: attributes, spawn rules,
multipart hitboxes, and control over damage and spawning.

## Attributes

A living entity type needs an attribute supplier (health, speed, armor, attack
damage, ...). Vanilla throws when a living entity without one is created.

```java
// During the register phase:
EntityAttributes.register(MY_GOLEM, Mob.createMobAttributes()
        .add(Attributes.MAX_HEALTH, 80)
        .add(Attributes.MOVEMENT_SPEED, 0.25));

// An attribute on any type, vanilla types included:
EntityAttributes.add(EntityTypes.ZOMBIE, MY_MANA, 20.0);
```

- `register(type, builder)` gives a mod type its supplier. On a vanilla type it
  replaces vanilla's.
- `add(type, attribute, base)` adds one attribute on top of the type's supplier,
  or changes its base value.
- Register before entities of the type exist. The server proof adds attack
  damage to pigs and checks a new pig has it, with its vanilla health kept.

## Spawn rules

```java
SpawnPlacementRules.register(MY_FROG, SpawnPlacementTypes.ON_GROUND,
        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules);

SpawnPlacementRules.addCheck(EntityTypes.ZOMBIE,
        (type, level, reason, pos, random) -> !Wards.near(level, pos));
```

- `register` gives a mod mob type its placement (on the ground, in water, ...),
  its heightmap, and its rule, as vanilla does for its own mobs. Natural
  spawning uses them.
- `addCheck` adds a condition that every spawn of a type must also pass,
  checked after the type's own rule. A check that throws is logged, and the
  spawn is allowed.

## Multipart entities

An entity made of several hitboxes, like the ender dragon, implements
`MultipartEntity` and gives itself `PartEntity` parts:

```java
public class Serpent extends Monster implements MultipartEntity {
    private final SerpentPart head = new SerpentPart(this, "head", 1.0F, 1.0F);
    private final SerpentPart tail = new SerpentPart(this, "tail", 0.8F, 0.8F);

    public PartEntity<?>[] getParts() { return new PartEntity<?>[] {head, tail}; }

    public boolean hurtFromPart(PartEntity<?> part, ServerLevel level, DamageSource source, float amount) {
        return hurtServer(level, source, part == head ? amount * 2 : amount);
    }

    public void aiStep() {
        super.aiStep();
        head.setPos(...); // move the parts with the body every tick
        tail.setPos(...);
    }
}
```

Weaver makes the parts work like the dragon's:

- Box queries find them: melee, the crosshair, projectiles, and area damage.
- A part id in a packet resolves to the part, so a player hitting the head hits
  the head.
- Hits go to `hurtFromPart`.
- The client gives the parts the same ids as the server.

A part that knows its size only after its own constructor ran (it sets a size
field, then overrides `getDimensions`) uses the `PartEntity(parent, name)`
constructor, which does not ask for the size, and calls `refreshDimensions()`
once the size is set.

Parts are not in the level's entity list: they are not saved, not sent to
clients, and not drawn. Create them in the parent's constructor, right after
the parent, so their ids follow the parent's.

The server proof adds a pig with a head part, finds the part by id and by box,
hits it, and checks that the hit reached the parent and that the part leaves
with it.

## Damage

| Event | When | Can change |
|---|---|---|
| `EntityHurtEvent` | a hurt starts | cancel only |
| `LivingIncomingDamageEvent` | a hurt starts, before invulnerability, shield blocking, armor, and enchantments | the amount; cancel |
| `LivingShieldBlockEvent` | a shield blocks | how much is blocked |
| `LivingDamagePreEvent` | after every reduction, before health | the final damage; cancel |
| `LivingDamagePostEvent` | after the damage | nothing |

A combat mod's block or parry that takes part of a hit sets the amount in
`LivingIncomingDamageEvent`. The server proof halves a hit and checks the
health lost.

## Spawning

`FinalizeSpawnEvent` fires when a mob is set up for spawning (natural spawns,
spawners, spawn eggs, structures, commands):

- Canceling the event skips the mob's own setup (equipment, variants) and still
  adds the mob.
- `setSpawnCancelled(true)` keeps the mob out of the world: the level refuses to
  add it.

The server proof cancels a zombie spawned by command and checks it is not in
the level.

## Related

- [Block, item, and entity extensions](extensions.md)
- [Events](events.md)
- [Fluid types](fluids.md)
- [Networking: sending to the players that see an entity](network.md#sending-to-the-players-that-see-an-entity)
