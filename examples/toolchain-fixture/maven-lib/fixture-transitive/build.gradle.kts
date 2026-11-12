import org.gradle.api.publish.maven.MavenPublication

plugins {
    `java-library`
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "fixture"
            url = uri(rootProject.layout.projectDirectory.dir("../repo").asFile)
        }
    }
}
