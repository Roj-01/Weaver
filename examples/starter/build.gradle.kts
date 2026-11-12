plugins {
    id("net.weaver.gradle")
}

group = "net.weaver.example"
version = "1.0.0"

val repoRoot = rootProject.projectDir.parentFile.parentFile

weaver {
    mainClass.set("net.weaver.starter.StarterMod")
    modId.set("weaver-starter")
    modName.set("Weaver Starter")
    modVersion.set("1.0.0")
    minecraftVersion.set("26.3")
    side.set("both")
    apiJar.set(System.getenv("WEAVER_API_JAR") ?: repoRoot.resolve("api/build/libs/api-2.3.2.jar").absolutePath)
    gameApiJar.set(System.getenv("WEAVER_GAME_API_JAR") ?: repoRoot.resolve("api-game/build/libs/api-game-2.3.2.jar").absolutePath)
    gameDir.set(System.getenv("WEAVER_GAME_DIR") ?: repoRoot.resolve("work/game-26.3").absolutePath)
    serverDir.set(System.getenv("WEAVER_SERVER_DIR") ?: repoRoot.resolve("work/server-26.3").absolutePath)
    agentJar.set(System.getenv("WEAVER_AGENT_JAR") ?: repoRoot.resolve("core/build/agent/weaver-agent.jar").absolutePath)
    launcher.set(System.getenv("WEAVER_LAUNCHER") ?: repoRoot.resolve("tools/launcher/launch.ps1").absolutePath)
}
