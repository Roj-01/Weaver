plugins {
    `java-gradle-plugin`
}

group = "net.weaver"
version = "2.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(26))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(26)
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.0")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
    // TestKit projects are isolated temporary builds; do not let the plugin
    // build's own configuration cache leak into them.
    systemProperty("org.gradle.testkit.runner.GradleRunner", "true")
}

gradlePlugin {
    plugins {
        create("weaver") {
            id = "net.weaver.gradle"
            implementationClass = "net.weaver.gradle.WeaverPlugin"
            displayName = "Weaver Gradle plugin"
            description = "Dependency resolution, jar-in-jar, publishing, and run tasks for Weaver mods."
        }
    }
}
