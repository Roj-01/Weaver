package net.weaver.patcher;

import java.io.IOException;
import java.nio.file.Path;

/**
 * CLI entry point for the Weaver binary patcher.
 */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        try {
            Options options = Options.parse(args);
            if (options.help) {
                System.out.print(usage());
                return;
            }
            JarPatcher.Result result = JarPatcher.patch(options.input, options.output, options.inject,
                    options.report, options.patchLevel, PatchSet.ID, PatchSet.weaver263v1());
            System.out.println("PATCHER PATCH ok=true input=" + result.input()
                    + " output=" + result.output()
                    + " applied=" + result.applied().size()
                    + " skipped=" + result.skipped().size()
                    + " elapsedMs=" + result.elapsedMs()
                    + " sha256=" + result.outputSha256());
        } catch (PatchException e) {
            System.err.println("PATCHER ERROR: " + e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("PATCHER ERROR: I/O failure: " + e.getMessage());
            System.exit(1);
        } catch (IllegalArgumentException e) {
            System.err.println("PATCHER ERROR: " + e.getMessage());
            System.exit(1);
        } catch (RuntimeException e) {
            System.err.println("PATCHER ERROR: unexpected failure: " + e);
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static String usage() {
        return """
                Usage: net.weaver.patcher.Main --input <jar> --output <jar> --inject <dir> --report <json> --patch-level 1

                  --input        official Minecraft jar to read (never modified)
                  --output       patched jar to create (temp file then atomic move)
                  --inject       directory containing compiled net/weaver/patched classes
                  --report       JSON report path
                  --patch-level  patch set level; this patcher implements level 1
                """;
    }

    private static final class Options {
        private Path input;
        private Path output;
        private Path inject;
        private Path report;
        private int patchLevel = -1;
        private boolean help;

        private static Options parse(String[] args) {
            Options options = new Options();
            for (int i = 0; i < args.length; i++) {
                String argument = args[i];
                switch (argument) {
                    case "--help", "-h" -> options.help = true;
                    case "--input" -> options.input = Path.of(requireValue(args, ++i, argument));
                    case "--output" -> options.output = Path.of(requireValue(args, ++i, argument));
                    case "--inject" -> options.inject = Path.of(requireValue(args, ++i, argument));
                    case "--report" -> options.report = Path.of(requireValue(args, ++i, argument));
                    case "--patch-level" -> {
                        String value = requireValue(args, ++i, argument);
                        try {
                            options.patchLevel = Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("--patch-level must be an integer: " + value);
                        }
                    }
                    default -> throw new IllegalArgumentException("unknown argument: " + argument);
                }
            }

            if (options.help) {
                return options;
            }
            if (options.input == null || options.output == null || options.inject == null
                    || options.report == null) {
                throw new IllegalArgumentException("--input, --output, --inject, and --report are required");
            }
            if (options.patchLevel < 0) {
                throw new IllegalArgumentException("--patch-level is required");
            }
            return options;
        }

        private static String requireValue(String[] args, int index, String argument) {
            if (index >= args.length) {
                throw new IllegalArgumentException(argument + " requires a value");
            }
            return args[index];
        }
    }
}
