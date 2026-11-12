package net.weaver.core.jij;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

/**
 * Manual inspection tool for Weaver jar-in-jar layouts.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * java -cp core.jar net.weaver.core.jij.JijCli --jar mod.jar --out export/
 * }</pre>
 *
 * <p>The scanner summary is printed first, followed by one line per extracted
 * jar and a final {@code WEAVER JIJ ok=true} line. The process exits non-zero
 * when the jar cannot be read or its layout is invalid.
 */
public final class JijCli {

    private JijCli() {
    }

    /**
     * Runs the command-line tool.
     *
     * @param args {@code --jar <file> --out <dir>}; {@code --help} prints usage
     */
    public static void main(String[] args) {
        int status = run(args, System.out, System.err);
        if (status != 0) {
            System.exit(status);
        }
    }

    /**
     * Runs the command-line tool without exiting the JVM.
     *
     * @param args command-line arguments
     * @param out standard output
     * @param err standard error
     * @return {@code 0} on success, non-zero on a usage or layout error
     */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        Path jar = null;
        Path target = null;
        for (int index = 0; index < args.length; index++) {
            String arg = args[index];
            switch (arg) {
                case "--jar", "-j" -> {
                    if (++index >= args.length) {
                        err.println("weaver jij: --jar requires a file");
                        return 1;
                    }
                    jar = Path.of(args[index]);
                }
                case "--out", "-o" -> {
                    if (++index >= args.length) {
                        err.println("weaver jij: --out requires a directory");
                        return 1;
                    }
                    target = Path.of(args[index]);
                }
                case "--help", "-h" -> {
                    usage(out);
                    return 0;
                }
                default -> {
                    err.println("weaver jij: unknown argument: " + arg);
                    usage(err);
                    return 1;
                }
            }
        }
        if (jar == null || target == null) {
            err.println("weaver jij: --jar and --out are required");
            usage(err);
            return 1;
        }
        try {
            List<NestedJar> nested = JijScanner.scan(jar, line -> err.println(line));
            out.println("WEAVER JIJ jar=" + jar.toAbsolutePath() + " nested=" + nested.size());
            for (NestedJar entry : nested) {
                out.println("WEAVER JIJ ENTRY id=" + entry.id()
                        + " version=" + entry.version()
                        + " optional=" + entry.optional()
                        + " file=" + entry.entryName());
            }
            List<Path> extracted = JijExtractor.extract(jar, target,
                    line -> out.println(line));
            out.println("WEAVER JIJ ok=true nested=" + nested.size()
                    + " extracted=" + extracted.size()
                    + " out=" + target.toAbsolutePath());
            return 0;
        } catch (IOException | IllegalArgumentException e) {
            err.println("weaver jij: " + e.getMessage());
            return 1;
        }
    }

    private static void usage(PrintStream stream) {
        stream.println("usage: JijCli --jar <mod.jar> --out <directory>");
        stream.println("  --jar, -j  the Weaver mod jar to inspect");
        stream.println("  --out, -o  the directory that receives the nested jars");
    }
}
