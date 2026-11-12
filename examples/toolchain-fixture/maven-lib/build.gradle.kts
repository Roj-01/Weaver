val weaverRoot = rootDir.parentFile.parentFile.parentFile
val weaverApiJar = System.getenv("WEAVER_API_JAR")
    ?: File(weaverRoot, "api/build/libs/api-2.3.2.jar").absolutePath
val fixtureRepo = layout.projectDirectory.dir("../repo")
val fixtureArtifacts = listOf("fixture-transitive", "fixture-lib", "fixture-optional")

allprojects {
    group = "com.weaver.fixture"
    version = "1.0.0"

    repositories {
        mavenCentral()
    }

    plugins.withId("java-library") {
        dependencies.add("compileOnly", files(weaverApiJar))
    }
}

tasks.register("publish") {
    group = "publishing"
    description = "Publishes the fixture Maven libraries and writes local repository metadata."
    dependsOn(
        ":fixture-transitive:publish",
        ":fixture-lib:publish",
        ":fixture-optional:publish"
    )
    doLast {
        for (artifact in fixtureArtifacts) {
            val directory = fixtureRepo.dir("com/weaver/fixture/$artifact").asFile
            if (!directory.isDirectory) {
                continue
            }
            directory.resolve("maven-metadata.xml").writeText(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata>
                  <groupId>com.weaver.fixture</groupId>
                  <artifactId>$artifact</artifactId>
                  <versioning>
                    <latest>1.0.0</latest>
                    <release>1.0.0</release>
                    <versions>
                      <version>1.0.0</version>
                    </versions>
                    <lastUpdated>20260101000000</lastUpdated>
                  </versioning>
                </metadata>
                """.trimIndent() + "\n"
            )
        }
    }
}
