import java.util.jar.JarFile

val gameApiAvailable = rootProject.extra["weaver.gameApiAvailable"] as Boolean

dependencies {
    implementation(project(":api"))
    implementation(project(":mixin"))
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("org.ow2.asm:asm:9.11.1")
    implementation("org.ow2.asm:asm-commons:9.11.1")

    if (gameApiAvailable) {
        runtimeOnly(project(":api-game"))
        runtimeOnly(project(":nf"))
    }

    testImplementation(project(":api"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.0")
}

// ApiSurfaceTest compares the API with this file: an edit to it reruns the tests.
tasks.named<Test>("test") {
    inputs.file(rootProject.file("api-baseline/weaver-api.txt")).withPropertyName("apiBaseline").optional()
}

val agentLibs = tasks.register<Sync>("agentLibs") {
    from(configurations.runtimeClasspath)
    into(layout.buildDirectory.dir("agent/lib"))
}

val agentClassPath = configurations.runtimeClasspath.get().files
    .joinToString(" ") { "lib/${it.name}" }

val agentJar = tasks.register<Jar>("agentJar") {
    dependsOn(tasks.named("classes"), agentLibs)
    archiveFileName.set("weaver-agent.jar")
    destinationDirectory.set(layout.buildDirectory.dir("agent"))
    from(sourceSets.main.get().output)
    inputs.property("agentClassPath", agentClassPath)
    manifest {
        attributes(
            "Premain-Class" to "net.weaver.core.LoaderAgent",
            "Agent-Class" to "net.weaver.core.LoaderAgent",
            // Developer hot swap (-Dweaver.dev.hotswap) redefines changed mod classes.
            "Can-Redefine-Classes" to "true",
            "Implementation-Title" to "Weaver Agent",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to (rootProject.extra["weaver.vendor"] as String),
            "Class-Path" to agentClassPath
        )
    }
}

val verifyAgentLayout = tasks.register("verifyAgentLayout") {
    group = "verification"
    description = "Verifies the agent lib and manifest contain exactly one api-game jar and no bundled game or base API classes."
    dependsOn(agentJar)
    onlyIf { gameApiAvailable }
    val agentJarFile = agentJar.flatMap { it.archiveFile }
    val libDir = layout.buildDirectory.dir("agent/lib")
    doLast {
        val lib = libDir.get().asFile
        val gameApiJars = lib.listFiles { file ->
            file.isFile && file.name.startsWith("api-game-") && file.name.endsWith(".jar")
        }?.toList().orEmpty()
        if (gameApiJars.size != 1) {
            throw GradleException(
                "agent lib must contain exactly one api-game-*.jar in $lib (found ${gameApiJars.size})"
            )
        }
        JarFile(gameApiJars.single()).use { jar ->
            val version = jar.manifest?.mainAttributes?.getValue("Weaver-Game-Api-Version")
            if (version != "26.3") {
                throw GradleException(
                    "api-game jar manifest must declare Weaver-Game-Api-Version: 26.3 (found $version)"
                )
            }
            val names = jar.entries().asSequence().map { it.name }.toList()
            val gameClasses = names.filter {
                it.startsWith("net/minecraft/") || it.startsWith("com/mojang/")
            }
            if (gameClasses.isNotEmpty()) {
                throw GradleException(
                    "api-game jar bundles game classes: " + gameClasses.take(20).joinToString(", ")
                )
            }
            val baseApiClasses = names.filter {
                !it.endsWith("/") &&
                    it.startsWith("net/weaver/api/") &&
                    !it.startsWith("net/weaver/api/game/")
            }
            if (baseApiClasses.isNotEmpty()) {
                throw GradleException(
                    "api-game jar must not bundle base API classes: " +
                        baseApiClasses.take(20).joinToString(", ")
                )
            }
        }
        JarFile(agentJarFile.get().asFile).use { jar ->
            val classPath = jar.manifest?.mainAttributes?.getValue("Class-Path")
                ?: throw GradleException("agent jar manifest has no Class-Path attribute")
            val entries = classPath.split(Regex("\\s+")).filter { it.isNotBlank() }
            val gameApiEntries = entries.filter { it.matches(Regex("lib/api-game-.*\\.jar")) }
            if (gameApiEntries.size != 1) {
                throw GradleException(
                    "agent manifest Class-Path must list exactly one lib/api-game-*.jar; " +
                        "found ${gameApiEntries.size}"
                )
            }
            val apiEntries = entries.filter { it.matches(Regex("lib/api-(?!game-).*\\.jar")) }
            if (apiEntries.size != 1) {
                throw GradleException(
                    "agent manifest Class-Path must list exactly one lib/api-*.jar; " +
                        "found ${apiEntries.size}"
                )
            }
        }
    }
}

tasks.named("check") {
    dependsOn(verifyAgentLayout)
}

tasks.named("assemble") {
    dependsOn(agentJar, verifyAgentLayout)
}
