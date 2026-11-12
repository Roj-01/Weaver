# Getting started: your first Weaver mod

This guide builds the mod in `examples/first-mod`, which has an item, a
creative tab, an event listener, and a game test. Every command here is the
one the example is built and tested with.

Weaver is a **beta with a frozen API** ([API stability](api-stability.md)): see [release notes](release-notes.md)
for what is tested and what is not.

## 1. What you need

- Windows 10 or 11 with PowerShell 5.1 or later. Linux and macOS are not
  tested yet.
- JDK 26. Gradle must also run on JDK 26, so set `JAVA_HOME` to it.
- Git, and Git Bash for the client download script.
- About 2 GB of disk for the game files.

## 2. Build Weaver

```bash
git clone <weaver repository> Weaver
```

```bash
cd Weaver
```

```bash
gradlew.bat :api:jar :api-game:jar :core:agentJar
```

This builds three things:
- the base API: `api/build/libs`;
- the game API: `api-game/build/libs`;
- the agent: `core/build/agent/weaver-agent.jar`, with its libraries in
  `core/build/agent/lib`.

## 3. Get the game

```bash
bash tools/fetch-game/fetch-game.sh
```

```bash
powershell -NoProfile -ExecutionPolicy Bypass -File tools/fetch-game/fetch-server.ps1
```

The first downloads the 26.3 client into `work/game-26.3`, the second
extracts the dedicated server into `work/server-26.3`. Weaver never
redistributes game files; each developer downloads them from Mojang.

## 4. Start a mod

Copy `examples/first-mod`, or generate an empty one:

```bash
powershell -File tools/generator/new-mod.ps1 -Name "My Mod" -Target examples/my-mod
```

A mod is a Gradle project that applies the Weaver plugin (`build.gradle.kts`):

```kotlin
plugins {
    id("net.weaver.gradle")
}

weaver {
    mainClass.set("com.example.firstmod.FirstMod")
    modId.set("firstmod")
    modName.set("First Mod")
    modVersion.set("1.0.0")
    minecraftVersion.set("26.3")
    side.set("both")
}
```

The plugin writes `weaver.mod.json` and puts the game, the base API, and
the game API on the compile classpath. The example also sets the jar
and game paths inside this repository; a project elsewhere sets the
`WEAVER_*` environment variables instead (see
[toolchain](toolchain.md)).

## 5. The code

```java
public final class FirstMod implements GameWeaverMod {
    public static Item RUBY;

    @Override
    public void onInitialize(ModContext context) {
        // An item, registered straight into the game registry.
        ResourceKey<Item> rubyKey = ResourceKey.create(Registries.ITEM, id("ruby"));
        RUBY = Registry.register(BuiltInRegistries.ITEM, rubyKey, new Item(new Item.Properties().setId(rubyKey)));

        // A creative tab; Weaver gives mod tabs their own page.
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("main"), ModCreativeTabs.builder()
                .title(Component.translatable("itemGroup.firstmod"))
                .icon(() -> new ItemStack(RUBY))
                .displayItems(ModCreativeTabs.items(items -> items.accept(RUBY)))
                .build());

        // An event: greet players when they join a server.
        context.events().addListener(PlayerJoinEvent.class, event ->
                event.player().sendSystemMessage(Component.literal("Welcome! First Mod is installed.")));

        // A game test, run headless by the weaverGameTest task.
        GameTests.register(id("ruby_is_registered"), helper -> {
            helper.assertTrue(BuiltInRegistries.ITEM.getValue(id("ruby")) == RUBY, "the ruby is in the item registry");
            helper.succeed();
        });
    }
}
```

What is going on:

- **Plain game code.** A Weaver mod uses `net.minecraft` classes
  directly. Items, blocks, and entities go into the vanilla registries
  during `onInitialize`, before they freeze. `DeferredRegister` is there if
  you prefer it ([registry](registry.md)).
- **Creative tabs.** `ModCreativeTabs` exists because vanilla's item callback
  type is protected; mod tabs get pages of their own
  ([registry](registry.md#creative-tabs)).
- **Events.** `context.events()` is your mod's bus. The typed game events
  are under `net.weaver.api.game.event` ([hooks](hooks.md),
  [events](events.md)).
- **Resources.** These go under `src/main/resources/assets/firstmod/`: a
  language file and an item model definition. Data under
  `src/main/resources/data/` loads as a data pack.

## 6. Build, run, and test

Run these from the Weaver folder (`-p` points Gradle at the mod; a mod folder
has no Gradle wrapper of its own), with `JAVA_HOME` set to JDK 26:

```bash
gradlew.bat -p examples/first-mod weaverBuild
```

```bash
gradlew.bat -p examples/first-mod runClient
```

```bash
gradlew.bat -p examples/first-mod runServer
```

```bash
gradlew.bat -p examples/first-mod weaverGameTest
```

- `weaverBuild` writes `build/libs/First Mod.jar`.
- `runClient` and `runServer` start the game with the agent and your mod.
- `weaverGameTest` runs every registered game test on a headless server.
  It writes `build/weaver/gametest/report.xml`, and fails the build when a
  required test fails ([game tests](gametest.md)).

## 7. Give it to players

Players need Weaver itself and your jar:

```bash
powershell -File tools/installer/install.ps1 -GameDir <game folder> -Mods <folder with mod jars>
```

The installer copies the agent and the mods into `<game folder>/weaver`
and prints the launch command; the game starts through
`tools/launcher/launch.ps1`. There is no Minecraft Launcher, Prism, or
Modrinth integration yet, so this preview is for developers and testers.

On a server, every mod whose side is `both` must be on the client at the
same version. Otherwise the player is refused with the reason, for example
`missing required mod: firstmod (server=1.0.0)`
([network](network.md#mod-set-and-registry-check-on-join)).

## Next

| To do this | Read |
|---|---|
| Change game code | [Mixin and access transformers](toolchain.md) |
| Store data on items, entities, chunks, levels | [Capabilities](capabilities.md) |
| Move items, fluids, and energy between mods | [Lookups](lookups.md) |
| Change loot and biomes, add data maps | [World modifiers](world-modifiers.md) |
| Send packets | [Networking](network.md) |
| Configure your mod | [Configuration](config.md) |
| Client rendering, keys, screens | [Client API](client.md) |
| Commands | [Commands](commands.md) |
