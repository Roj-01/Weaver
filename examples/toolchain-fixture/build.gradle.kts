import org.gradle.api.tasks.bundling.AbstractArchiveTask

plugins {
    id("net.weaver.gradle")
}

group = "com.weaver.fixture"
version = "1.0.0"

val weaverRoot = rootProject.projectDir.parentFile.parentFile

evaluationDependsOn(":nested")

repositories {
    maven {
        name = "fixtureLocal"
        url = uri("repo")
    }
}

dependencies {
    weaverMod("com.weaver.fixture:fixture-lib:[1.0,2.0)")
    weaverOptional("com.weaver.fixture:fixture-optional:1.0.0")
}

weaver {
    mainClass.set("com.weaver.fixture.toolchain.FixtureMod")
    modId.set("toolchain-fixture")
    modName.set("Weaver Toolchain Fixture")
    modVersion.set("1.0.0")
    minecraftVersion.set("26.3")
    side.set("server")
    embedModDependencies.set(true)
    exportJijForRun.set(true)
    strictConflicts.set(true)
    dependencies.put("fixture-lib", ">=1.0")
    optionalDependencies.put("fixture-optional", "1.0.0")

    apiJar.set(System.getenv("WEAVER_API_JAR")
        ?: weaverRoot.resolve("api/build/libs/api-2.3.2.jar").absolutePath)
    gameApiJar.set(System.getenv("WEAVER_GAME_API_JAR")
        ?: weaverRoot.resolve("api-game/build/libs/api-game-2.3.2.jar").absolutePath)
    agentJar.set(System.getenv("WEAVER_AGENT_JAR")
        ?: weaverRoot.resolve("core/build/agent/weaver-agent.jar").absolutePath)
    launcher.set(System.getenv("WEAVER_LAUNCHER")
        ?: weaverRoot.resolve("tools/launcher/launch.ps1").absolutePath)
    gameDir.set(System.getenv("WEAVER_GAME_DIR")
        ?: weaverRoot.resolve("work/game-26.3").absolutePath)
    serverDir.set(System.getenv("WEAVER_SERVER_DIR")
        ?: weaverRoot.resolve("work/server-26.3").absolutePath)

    localMods.from(project(":nested").tasks.named<AbstractArchiveTask>("weaverBuild")
        .flatMap { it.archiveFile })
}

tasks.register("fixtureDeps") {
    group = "weaver"
    description = "Resolves the fixture mod dependencies and reports the diagnostics file."
    dependsOn("weaverResolveMods")
    doLast {
        val report = layout.buildDirectory.file("weaver/dependencies.json").get().asFile
        if (report.isFile) {
            logger.lifecycle("TOOLCHAINFIXTURE DEPS report={}", report.absolutePath)
        } else {
            logger.lifecycle("TOOLCHAINFIXTURE DEPS report=missing")
        }
    }
}
