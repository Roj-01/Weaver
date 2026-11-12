# Extensible enums

A mod can add constants to existing enums, vanilla ones included: a new
`Rarity`, a new mob category, a new arm pose. Weaver adds them when the enum
loads, so they are in `values()`, `valueOf`, and everything vanilla builds from
those (codecs, id maps, `EnumMap`, `EnumSet`).

## Declaring constants

List the constants in `META-INF/enumextensions.json` inside the mod jar:

```json
{
  "entries": [
    {
      "enum": "net/minecraft/world/item/Rarity",
      "name": "EXAMPLEMOD_MYTHIC",
      "constructor": "(ILjava/lang/String;Lnet/minecraft/ChatFormatting;)V",
      "parameters": {"class": "com/example/ExampleEnums", "method": "mythic"}
    }
  ]
}
```

- `enum` is the enum's class name (`/` or `.` both work).
- `name` must start with the mod id in upper case and an underscore
  (`EXAMPLEMOD_`; hyphens in the id become underscores, so `my-mod` gives
  `MY_MOD_`), so two mods never pick the same name. Entries that do not are
  rejected and logged (`ENUM EXTENSION REJECTED`).
- `constructor` is the descriptor of one of the enum's constructors, without
  the name and ordinal that every enum constructor starts with. Look it up with
  `javap -p` on the enum.
- `parameters` names a static method of the mod that returns the constructor's
  arguments as an `Object[]` (primitives boxed). It may take an `int`: the new
  constant's ordinal, for enums whose ids must follow on, like `Rarity`.

```java
final class ExampleEnums {
    static Object[] mythic(int ordinal) {
        return new Object[] {ordinal, "examplemod_mythic", ChatFormatting.LIGHT_PURPLE};
    }
}
```

The method runs while the enum initializes, before any of its constants can be
used: it must not touch the enum itself. Its class is initialized at that point
too, so keep the lookup of the new constant (below) out of that class's static
fields: put the method in a class of its own.

## Using a constant

The constant is not in the game jar, so code compiled against it cannot name
it. Look it up once:

```java
public static final Rarity MYTHIC = EnumExtensions.get(Rarity.class, "EXAMPLEMOD_MYTHIC");
```

`EnumExtensions.get` is `Enum.valueOf`: it finds vanilla and added constants
alike.

## What it changes

For each enum with added constants, when the class loads Weaver:

1. adds a `public static final` enum field for each constant;
2. builds the constants in the static initializer, after vanilla's, with the
   next ordinals;
3. makes `$values()` return vanilla's constants followed by the added ones.

Mods' constants follow in mod load order. A constant whose name is taken (by
vanilla or an earlier entry), or whose constructor does not exist, is skipped and logged (`ENUM EXTENSION
SKIPPED`). Enums whose constants have bodies of their own (abstract enums)
cannot be extended.

Code that switches over an enum sends the new constants to its `default`
branch. A mod that adds a constant is responsible for the places vanilla needs
to know about it (for example a translation or a texture).

Both sides need the same constants: an enum sent over the network by ordinal
(like `Rarity`) must match on the client and the server.

## Arm poses

An added `HumanoidModel.ArmPose` does nothing until the mod says how it moves
the arms: see `ArmPoses` and `ClientItemExtension.getArmPose` in
[extensions](extensions.md#client-item-behavior).

## Proofs

- A unit test adds a constant to a test enum and checks `values()`,
  `valueOf`, the ordinal, the field, and the constructor arguments.
- The server proof adds `WEAVERPROOF_MYTHIC` to `Rarity` and checks it is the
  last value, that the codec decodes `weaverproof_mythic`, that the id map
  finds it, and that an item stack carries it as its rarity.
- The client proof adds `WEAVERCLIENTPROOF_STEADY` to `HumanoidModel.ArmPose`
  and uses it as an item's arm pose.

## Related

- [Block, item, and entity extensions](extensions.md)
- [Toolchain: access transformers](toolchain.md)
