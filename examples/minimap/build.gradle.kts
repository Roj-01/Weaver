plugins {
    id("net.weaver.gradle")
}

group = "net.weaver.minimap"
version = "1.0.0"

val repoRoot = rootProject.projectDir.parentFile.parentFile

weaver {
    mainClass.set("net.weaver.minimap.MinimapMod")
    modId.set("weaver-minimap")
    modName.set("Weaver Minimap")
    modVersion.set("1.0.0")
    minecraftVersion.set("26.3")
    side.set("client")
    apiJar.set(System.getenv("WEAVER_API_JAR") ?: repoRoot.resolve("api/build/libs/api-2.3.2.jar").absolutePath)
    gameApiJar.set(System.getenv("WEAVER_GAME_API_JAR") ?: repoRoot.resolve("api-game/build/libs/api-game-2.3.2.jar").absolutePath)
    gameDir.set(System.getenv("WEAVER_GAME_DIR") ?: repoRoot.resolve("work/game-26.3").absolutePath)
    serverDir.set(System.getenv("WEAVER_SERVER_DIR") ?: repoRoot.resolve("work/server-26.3").absolutePath)
    agentJar.set(System.getenv("WEAVER_AGENT_JAR") ?: repoRoot.resolve("core/build/agent/weaver-agent.jar").absolutePath)
    launcher.set(System.getenv("WEAVER_LAUNCHER") ?: repoRoot.resolve("tools/launcher/launch.ps1").absolutePath)
    quickPlayWorld.set("WeaverProofFixture")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(26))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.0")
    testImplementation("com.google.code.gson:gson:2.11.0")
}

tasks.test {
    useJUnitPlatform()
}
