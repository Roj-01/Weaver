plugins {
    id("net.weaver.gradle")
}

group = "com.weaver.fixture"
version = "1.0.0"

val weaverRoot = rootProject.projectDir.parentFile.parentFile

weaver {
    mainClass.set("com.weaver.fixture.nested.NestedMod")
    modId.set("fixture-nested")
    modName.set("Weaver Toolchain Fixture Nested")
    modVersion.set("1.0.0")
    minecraftVersion.set("26.3")
    side.set("server")

    apiJar.set(System.getenv("WEAVER_API_JAR")
        ?: weaverRoot.resolve("api/build/libs/api-2.3.2.jar").absolutePath)
    gameApiJar.set(System.getenv("WEAVER_GAME_API_JAR")
        ?: weaverRoot.resolve("api-game/build/libs/api-game-2.3.2.jar").absolutePath)
    agentJar.set(System.getenv("WEAVER_AGENT_JAR")
        ?: weaverRoot.resolve("core/build/agent/weaver-agent.jar").absolutePath)
    gameDir.set(System.getenv("WEAVER_GAME_DIR")
        ?: weaverRoot.resolve("work/game-26.3").absolutePath)
    serverDir.set(System.getenv("WEAVER_SERVER_DIR")
        ?: weaverRoot.resolve("work/server-26.3").absolutePath)
    launcher.set(System.getenv("WEAVER_LAUNCHER")
        ?: weaverRoot.resolve("tools/launcher/launch.ps1").absolutePath)
}
