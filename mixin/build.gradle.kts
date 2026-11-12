dependencies {
    implementation(project(":api"))
    implementation("net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7")
    implementation("org.ow2.asm:asm:9.11.1")
    implementation("org.ow2.asm:asm-tree:9.11.1")
    implementation("org.ow2.asm:asm-commons:9.11.1")
    implementation("org.ow2.asm:asm-util:9.11.1")
    implementation("org.ow2.asm:asm-analysis:9.11.1")
    implementation("com.google.guava:guava:33.7.2-jre")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("io.github.llamalad7:mixinextras-fabric:0.5.5")
}

sourceSets {
    main {
        java {
            srcDir("src")
        }
        resources {
            srcDir("src")
            include("META-INF/services/**")
        }
    }
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "Weaver Mixin",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to (rootProject.extra["weaver.vendor"] as String)
        )
    }
}
