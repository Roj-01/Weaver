# Weaver starter

A minimal mod built with the Weaver Gradle plugin.

```powershell
cd examples\starter
..\..\gradlew.bat weaverBuild
..\..\gradlew.bat runClient
..\..\gradlew.bat runServer
```

`weaverBuild` writes `build/libs/Weaver Starter.jar` with
`weaver.mod.json`, `Weaver-Module-Class`, and `Weaver-Api-Version: V2`.

The `runClient` and `runServer` tasks stage the jar into `build/weaver/mods`
and launch through `tools/launcher/launch.ps1`.

Use `-Pweaver.dryRun=true` to print the launch command only.
