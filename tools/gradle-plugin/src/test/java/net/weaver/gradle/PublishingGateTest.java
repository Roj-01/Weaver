package net.weaver.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the repository's publishing gates and inspects the generated POMs.
 *
 * <p>This test uses the repository root because the gates are wired into the
 * root build; it only runs the two verification tasks, never a full build.
 */
class PublishingGateTest {

    @Test
    void rootPomsHaveMetadataAndNoGameDependencies() throws IOException {
        Path root = repositoryRoot();

        BuildResult api = runner(root)
                .withArguments(":api:verifyApiPom", "--console=plain")
                .build();
        String apiOutput = api.getOutput();
        assertTrue(apiOutput.contains("BUILD SUCCESSFUL"), apiOutput);
        assertTrue(apiOutput.contains(":api:verifyApiPom"), apiOutput);

        Path apiPom = root.resolve("api/build/publications/weaverApi/pom-default.xml");
        assertTrue(Files.isRegularFile(apiPom), "missing " + apiPom);
        String apiText = Files.readString(apiPom, StandardCharsets.UTF_8);
        assertTrue(apiText.contains("<name>Weaver API</name>"), apiText);
        assertTrue(apiText.contains("LGPL-2.1-only"), apiText);
        assertTrue(apiText.contains("weaver.apiVersion"), apiText);
        assertFalse(apiText.contains("net.minecraft"), apiText);
        assertFalse(apiText.contains("com.mojang"), apiText);
        assertFalse(apiText.contains("api-game"), apiText);

        boolean gameAvailable = Files.isRegularFile(root.resolve("work/game-26.3/classpath.txt"))
                || Files.isRegularFile(root.resolve(
                        "work/server-26.3/versions/26.3/server-26.3.jar"));
        if (!gameAvailable) {
            System.out.println("PublishingGateTest: no game classpath under " + root.resolve("work")
                    + "; skipping :api-game:verifyPomHasNoGameDependencies");
            return;
        }

        BuildResult game = runner(root)
                .withArguments(":api-game:verifyPomHasNoGameDependencies",
                        "--console=plain")
                .build();
        String gameOutput = game.getOutput();
        assertTrue(gameOutput.contains("BUILD SUCCESSFUL"), gameOutput);
        assertTrue(gameOutput.contains(":api-game:verifyPomHasNoGameDependencies"), gameOutput);

        Path gamePom = root.resolve("api-game/build/publications/weaverGame/pom-default.xml");
        assertTrue(Files.isRegularFile(gamePom), "missing " + gamePom);
        String gameText = Files.readString(gamePom, StandardCharsets.UTF_8);
        assertTrue(gameText.contains("weaver.gameApiVersion"), gameText);
        assertTrue(gameText.contains("26.3"), gameText);
        assertFalse(gameText.contains("net.minecraft"), gameText);
        assertFalse(gameText.contains("com.mojang"), gameText);
    }

    private static GradleRunner runner(Path root) {
        return GradleRunner.create().withProjectDir(root.toFile());
    }

    private static Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("api"))
                    && Files.isDirectory(current.resolve("api-game"))
                    && Files.isRegularFile(current.resolve("gradlew.bat"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("cannot locate the Weaver repository root from "
                + System.getProperty("user.dir"));
    }
}
