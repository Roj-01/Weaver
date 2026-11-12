# Tools

- `gradle-plugin/` — game classpath, `weaverBuild`, `weaverStage`, `runClient`,
  `runServer`, `runDatagen`.
- `launcher/` — launches the client or dedicated server with the agent attached.
- `fetch-game/` — downloads the 26.3 client, libraries, assets, and the real
  server jar.
- `datagen/` — standalone data generator.
- `generator/` — creates a new mod project from the starter template.
- `installer/` — copies the agent and mods into a game directory.

The Gradle plugin is an included build:

```kotlin
pluginManagement {
    includeBuild("../../tools/gradle-plugin")
}
```

```kotlin
plugins { id("net.weaver.gradle") }

weaver {
    mainClass.set("com.example.MyMod")
    modId.set("my-mod")
    modVersion.set("1.0.0")
    minecraftVersion.set("26.3")
    gameDir.set(rootProject.file("work/game-26.3").absolutePath)
}
```

`gradle weaverBuild` produces the mod jar. `gradle runClient` and
`gradle runServer` launch the game with the agent and the built mod. Use
`-Pweaver.dryRun=true` to print the launch command without starting the game.
