package net.weaver.gradle.run;

import java.util.List;
import java.util.Map;

/**
 * Side-independent options applied to a generated launch command.
 *
 * @param jvmArgs extra JVM arguments inserted after the memory setting
 * @param programArgs extra program arguments appended after the required game arguments
 * @param environment extra environment variables for the child process
 * @param debug whether to add a JDWP debug agent
 * @param debugPort JDWP port
 * @param debugSuspend whether the JVM waits for a debugger before starting
 * @param workingDir explicit working directory, or {@code null} for the side directory
 * @param serverPort dedicated-server port
 * @param username offline client username
 * @param quickPlayWorld client quick-play single-player world, or blank
 * @param quickPlayServer client quick-play multiplayer address, or blank
 */
public record RunOptions(
        List<String> jvmArgs,
        List<String> programArgs,
        Map<String, String> environment,
        boolean debug,
        int debugPort,
        boolean debugSuspend,
        String workingDir,
        int serverPort,
        String username,
        String quickPlayWorld,
        String quickPlayServer) {

    public RunOptions {
        jvmArgs = jvmArgs == null ? List.of() : List.copyOf(jvmArgs);
        programArgs = programArgs == null ? List.of() : List.copyOf(programArgs);
        environment = environment == null ? Map.of() : Map.copyOf(environment);
        username = username == null || username.isBlank() ? "WeaverProof" : username;
        quickPlayWorld = quickPlayWorld == null ? "" : quickPlayWorld;
        quickPlayServer = quickPlayServer == null ? "" : quickPlayServer;
    }

    /** A no-extra-options value for tests and dry runs. */
    public static RunOptions defaults() {
        return new RunOptions(List.of(), List.of(), Map.of(), false, 5005, true,
                "", 25566, "WeaverProof", "", "");
    }
}
