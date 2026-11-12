import org.gradle.api.publish.maven.MavenPublication
import java.util.zip.ZipFile

plugins {
    `maven-publish`
}

val gameApiVersion = "26.3"

val gameClasspath: List<File> = (rootProject.extra["weaver.gameClasspath"] as? List<*>)
    ?.filterIsInstance<File>()
    .orEmpty()
val gameApiAvailable = rootProject.extra["weaver.gameApiAvailable"] as Boolean
val skipGameApi = rootProject.extra["weaver.skipGameApi"] as Boolean
val requireGameApi = rootProject.extra["weaver.requireGameApi"] as Boolean

dependencies {
    api(project(":api"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.14.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.0")
    compileOnly("net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7")
    // MixinExtras annotations for the game-event mixins; the loader bootstraps MixinExtras at runtime.
    compileOnly("io.github.llamalad7:mixinextras-fabric:0.5.5")

    if (gameApiAvailable) {
        compileOnly(files(gameClasspath))
        testImplementation(files(gameClasspath))
    }
}

java {
    withSourcesJar()
}

tasks.jar {
    manifest {
        attributes(
            "Weaver-Game-Api-Version" to gameApiVersion,
            "Implementation-Title" to "Weaver Game API",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to (rootProject.extra["weaver.vendor"] as String)
        )
    }
}

val verifyGameClasspath = tasks.register("verifyGameClasspath") {
    group = "verification"
    description = "Fails clearly when api-game is required but no Minecraft 26.3 classpath is available."
    onlyIf { requireGameApi && !gameApiAvailable && !skipGameApi }
    doLast {
        throw GradleException(
            """
            :api-game is required but no Minecraft 26.3 classpath was found.
            Fix one of the following:
              1. Set WEAVER_GAME_DIR to a directory containing classpath.txt.
              2. Provide work/server-26.3/versions/26.3/server-26.3.jar.
              3. Pass -Pweaver.skipGameApi=true if the optional game API is not needed.
            """.trimIndent()
        )
    }
}

tasks.named("compileJava") { dependsOn(verifyGameClasspath) }
tasks.named("compileTestJava") { dependsOn(verifyGameClasspath) }

val verifyNoGameClasses = tasks.register("verifyNoGameClasses") {
    group = "verification"
    description = "Fails if the api-game jar contains Minecraft or Mojang classes."
    onlyIf { gameApiAvailable }
    dependsOn(tasks.named("jar"))
    val jarFile = tasks.named<Jar>("jar").flatMap { it.archiveFile }
    inputs.file(jarFile)
    doLast {
        val forbidden = mutableListOf<String>()
        ZipFile(jarFile.get().asFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val name = entries.nextElement().name
                if (name.startsWith("net/minecraft/") || name.startsWith("com/mojang/")) {
                    forbidden += name
                }
            }
        }
        if (forbidden.isNotEmpty()) {
            throw GradleException(
                "api-game jar must not contain game classes: " + forbidden.take(20).joinToString(", ")
            )
        }
    }
}

val verifyPomHasNoGameDependencies = tasks.register("verifyPomHasNoGameDependencies") {
    group = "verification"
    description = "Fails if the published api-game POM references game jars or Minecraft/Mojang coordinates."
    onlyIf { gameApiAvailable }
    dependsOn("generatePomFileForWeaverGamePublication")
    val pomFile = layout.buildDirectory.file("publications/weaverGame/pom-default.xml")
    inputs.file(pomFile)
    doLast {
        val text = pomFile.get().asFile.readText()
        val required = listOf(
            "<name>Weaver Game API</name>",
            "LGPL-2.1-only",
            "weaver.gameApiVersion",
            "26.3"
        )
        val missing = required.filterNot { text.contains(it) }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "api-game POM is missing required metadata: " + missing.joinToString(", ")
            )
        }
        val forbidden = listOf("net.minecraft", "com.mojang", "client.jar", "server-26.3.jar")
        val hits = forbidden.filter { text.contains(it) }
        if (hits.isNotEmpty()) {
            throw GradleException(
                "api-game POM must not reference game dependencies; found: " + hits.joinToString(", ")
            )
        }
    }
}

if (gameApiAvailable) {
    tasks.named("check") {
        dependsOn(verifyNoGameClasses, verifyPomHasNoGameDependencies)
    }

    tasks.named("assemble") {
        dependsOn(verifyNoGameClasses)
    }

    tasks.named("publish") {
        dependsOn(verifyNoGameClasses, verifyPomHasNoGameDependencies)
    }
}

publishing {
    publications {
        create<MavenPublication>("weaverGame") {
            from(components["java"])
            artifactId = "api-game"
            pom {
                name.set("Weaver Game API")
                description.set(
                    "Optional game-typed Weaver API compiled against Minecraft 26.3. " +
                        "Game classes are compile-only and are never bundled or published as dependencies."
                )
                licenses {
                    license {
                        name.set("LGPL-2.1-only")
                        url.set("https://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt")
                    }
                }
                developers {
                    developer {
                        id.set("abdullah-haitham-al-nuaimi")
                        name.set("Abdullah Haitham Al-Nuaimi")
                    }
                }
                properties.put("weaver.apiVersion", "V2")
                properties.put("weaver.gameApiVersion", gameApiVersion)
                properties.put("weaver.minecraftVersion", gameApiVersion)
                properties.put("weaver.artifactType", "api-game")
            }
        }
    }
    repositories {
        maven {
            name = "weaverLocal"
            url = layout.buildDirectory.dir("repo").get().asFile.toURI()
        }
    }
}

if (!gameApiAvailable) {
    tasks.configureEach {
        if (name != "clean") {
            enabled = false
        }
    }
}
