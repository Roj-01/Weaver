// Weaver NF: NeoForge-shaped classes (DeferredRegister, DeferredHolder, ModConfigSpec,
// networking, ...) written for Weaver, so a NeoForge mod ports by changing its imports.
// It ships in the agent's lib; mods compile against it and do not bundle it.

val gameClasspath: List<File> = (rootProject.extra["weaver.gameClasspath"] as? List<*>)
    ?.filterIsInstance<File>()
    .orEmpty()
val gameApiAvailable = rootProject.extra["weaver.gameApiAvailable"] as Boolean

dependencies {
    api(project(":api-game"))
    // WeaverConfig (the documented way to open a mod config) lives in core, on the agent class path at run time.
    compileOnly(project(":core"))
    compileOnly("net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7")

    testImplementation("org.junit.jupiter:junit-jupiter:5.14.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.0")

    if (gameApiAvailable) {
        compileOnly(files(gameClasspath))
        testImplementation(files(gameClasspath))
    }
}

java {
    withSourcesJar()
}

tasks.jar {
    archiveBaseName.set("weaver-nf")
    manifest {
        attributes(
            "Implementation-Title" to "Weaver NF",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to (rootProject.extra["weaver.vendor"] as String)
        )
    }
}

if (!gameApiAvailable) {
    tasks.configureEach {
        if (name != "clean") {
            enabled = false
        }
    }
}
