package net.weaver.gradle.run;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * A fully resolved child-process command for a Weaver run task.
 *
 * @param side {@code client} or {@code server}
 * @param command executable and arguments; element zero is the Java executable
 * @param environment extra environment variables, never {@code null}
 * @param workingDir process working directory
 * @param log combined stdout/stderr log file
 * @param markers agent marker file
 */
public record LaunchPlan(
        String side,
        List<String> command,
        Map<String, String> environment,
        File workingDir,
        File log,
        File markers) {

    public LaunchPlan {
        command = List.copyOf(command);
        environment = Map.copyOf(environment);
    }

    /** Renders the command for {@code -Pweaver.dryRun=true} output. */
    public String describe() {
        StringBuilder out = new StringBuilder("RUN DRY side=").append(side)
                .append(" workingDir=").append(workingDir.getAbsolutePath())
                .append(" env=");
        if (environment.isEmpty()) {
            out.append("none");
        } else {
            boolean first = true;
            for (Map.Entry<String, String> entry : environment.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                out.append(entry.getKey()).append('=').append(entry.getValue());
                first = false;
            }
        }
        out.append(" command=");
        for (String part : command) {
            if (out.charAt(out.length() - 1) != ' ') {
                out.append(' ');
            }
            out.append(quote(part));
        }
        return out.toString();
    }

    private static String quote(String value) {
        if (value.isEmpty()) {
            return "\"\"";
        }
        if (value.indexOf(' ') < 0 && value.indexOf('"') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\\\"") + '"';
    }
}
