package net.weaver.core.modops;

import net.weaver.core.ModLoader;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Standalone command that discovers a mods directory and prints a mod-list report.
 *
 * <p>Usage:
 * <pre>
 * ModListCommand --mods &lt;dir&gt; [--side client|server] [--json] [--policy warn|fatal]
 * </pre>
 *
 * <p>Loader log lines are written to standard error so that {@code --json}
 * output on standard output stays machine-readable. The command does not need
 * instrumentation or a game launch. The exit code is {@code 0} on success,
 * {@code 1} when a fatal policy aborts discovery, and {@code 2} for invalid
 * arguments.
 */
public final class ModListCommand {

    private ModListCommand() {
    }

    /**
     * Runs the command and terminates the JVM with a non-zero exit code on failure.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        int code = run(args, System.out, System.err);
        if (code != 0) {
            System.exit(code);
        }
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        Path modsDir = Path.of("mods");
        String side = "client";
        boolean json = false;
        ModPolicies.Mode cliPolicy = null;

        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            switch (argument) {
                case "--mods" -> {
                    if (index + 1 >= args.length) {
                        err.println("[weaver] MOD LIST missing value for --mods");
                        return 2;
                    }
                    modsDir = Path.of(args[++index]);
                }
                case "--side" -> {
                    if (index + 1 >= args.length) {
                        err.println("[weaver] MOD LIST missing value for --side");
                        return 2;
                    }
                    side = args[++index].toLowerCase(Locale.ROOT);
                    if (!"client".equals(side) && !"server".equals(side)) {
                        err.println("[weaver] MOD LIST invalid side: " + side);
                        return 2;
                    }
                }
                case "--json" -> json = true;
                case "--policy" -> {
                    if (index + 1 >= args.length) {
                        err.println("[weaver] MOD LIST missing value for --policy");
                        return 2;
                    }
                    String value = args[++index].toLowerCase(Locale.ROOT);
                    switch (value) {
                        case "warn" -> cliPolicy = ModPolicies.Mode.WARN;
                        case "fatal" -> cliPolicy = ModPolicies.Mode.FATAL;
                        default -> {
                            err.println("[weaver] MOD LIST invalid policy: " + value);
                            return 2;
                        }
                    }
                }
                case "--help", "-h" -> {
                    printUsage(out);
                    return 0;
                }
                default -> {
                    err.println("[weaver] MOD LIST unknown argument: " + argument);
                    printUsage(err);
                    return 2;
                }
            }
        }

        ModPolicies policies = ModPolicies.fromSystemProperties();
        if (cliPolicy != null) {
            policies = new ModPolicies(cliPolicy, cliPolicy);
        }

        try (ModLoader loader = ModLoader.discover(modsDir, side, err::println, policies)) {
            ModList report = loader.report();
            if (json) {
                out.println(report.toJson());
            } else {
                for (String line : report.toTextLines()) {
                    out.println(line);
                }
            }
            return 0;
        } catch (ModPolicyException failure) {
            for (ModDiagnostic diagnostic : failure.diagnostics()) {
                err.println("[weaver] MOD POLICY " + diagnostic.severity()
                        + " " + diagnostic.type()
                        + " subject=" + diagnostic.subject()
                        + " " + diagnostic.message());
            }
            err.println("[weaver] MOD POLICY FAILED " + failure.getMessage());
            return 1;
        }
    }

    private static void printUsage(PrintStream stream) {
        stream.println("usage: ModListCommand --mods <dir> [--side client|server] [--json]"
                + " [--policy warn|fatal]");
    }
}
