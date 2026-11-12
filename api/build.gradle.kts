import org.gradle.api.publish.maven.MavenPublication

plugins {
    `maven-publish`
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.0")
}

java {
    withSourcesJar()
}

tasks.jar {
    manifest {
        attributes(
            "Weaver-Api-Version" to "V2",
            "Implementation-Title" to "Weaver API",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to (rootProject.extra["weaver.vendor"] as String)
        )
    }
}

val benchmark = tasks.register<JavaExec>("benchmark") {
    group = "verification"
    description = "Runs the EventBus dispatch micro-benchmark."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("net.weaver.api.event.EventBusBenchmark")
    jvmArgs("--add-modules", "jdk.management")
}

val verifyApiPom = tasks.register("verifyApiPom") {
    group = "verification"
    description = "Fails if the published api POM lacks metadata or references game classes."
    dependsOn("generatePomFileForWeaverApiPublication")
    val pomFile = layout.buildDirectory.file("publications/weaverApi/pom-default.xml")
    inputs.file(pomFile)
    doLast {
        val text = pomFile.get().asFile.readText()
        val required = listOf(
            "<name>Weaver API</name>",
            "<artifactId>api</artifactId>",
            "LGPL-2.1-only",
            "weaver.apiVersion"
        )
        val missing = required.filterNot { text.contains(it) }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "api POM is missing required metadata: " + missing.joinToString(", ")
            )
        }
        val forbidden = listOf("net.minecraft", "com.mojang", "api-game")
        val hits = forbidden.filter { text.contains(it) }
        if (hits.isNotEmpty()) {
            throw GradleException(
                "api POM must not reference game dependencies; found: " + hits.joinToString(", ")
            )
        }
    }
}

tasks.named("check") {
    dependsOn(verifyApiPom)
}

tasks.named("publish") {
    dependsOn(verifyApiPom)
}

publishing {
    publications {
        create<MavenPublication>("weaverApi") {
            from(components["java"])
            artifactId = "api"
            pom {
                name.set("Weaver API")
                description.set(
                    "Game-independent Weaver API. Contains no Minecraft or Mojang classes " +
                        "and can be consumed by any Java project."
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
                properties.put("weaver.artifactType", "api")
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
