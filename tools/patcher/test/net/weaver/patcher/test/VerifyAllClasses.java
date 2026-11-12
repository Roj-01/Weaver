package net.weaver.patcher.test;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Loads every class of a patched game jar through a URL class loader with
 * verification enabled.
 *
 * <p>This is the GPU-free structural check: it proves that the jar is a valid,
 * unsigned artifact, that every rewritten class passes the verifier, and that
 * the added interfaces resolve. It is the counterpart to the rendering client
 * proof, which depends on the host graphics stack.</p>
 */
public final class VerifyAllClasses {
    private VerifyAllClasses() {
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("usage: VerifyAllClasses <jar> <classpath-file> [min-classes]");
            System.exit(2);
        }
        try {
            Path jar = Path.of(args[0]).toAbsolutePath().normalize();
            Path classpath = Path.of(args[1]).toAbsolutePath().normalize();
            int minimum = args.length > 2 ? Integer.parseInt(args[2]) : 0;

            List<URL> urls = new ArrayList<>();
            urls.add(jar.toUri().toURL());
            for (String entry : Files.readString(classpath).split("[;\\r\\n]+")) {
                String value = entry.trim();
                if (value.isEmpty()) {
                    continue;
                }
                Path path = Path.of(value).toAbsolutePath().normalize();
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                if (!path.toString().replace('\\', '/').contains("/libraries/")) {
                    continue;
                }
                urls.add(path.toUri().toURL());
            }

            int ok = 0;
            List<String> failures = new ArrayList<>();
            try (URLClassLoader loader = new URLClassLoader(urls.toArray(URL[]::new),
                    ClassLoader.getPlatformClassLoader());
                 ZipFile zip = new ZipFile(jar.toFile())) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!name.endsWith(".class")
                            || name.equals("module-info.class")
                            || name.startsWith("META-INF/versions/")) {
                        continue;
                    }
                    String binaryName = name.substring(0, name.length() - 6)
                            .replace('/', '.');
                    try {
                        Class.forName(binaryName, false, loader);
                        ok++;
                    } catch (Throwable failure) {
                        failures.add(binaryName + " -> "
                                + failure.getClass().getSimpleName() + ": "
                                + failure.getMessage());
                    }
                }
            }

            System.out.println("PATCHER CLASSLOAD jar=" + jar.getFileName()
                    + " ok=" + ok + " failures=" + failures.size());
            for (int index = 0; index < Math.min(25, failures.size()); index++) {
                System.out.println("  FAIL " + failures.get(index));
            }
            if (ok < minimum) {
                throw new IllegalStateException("loaded " + ok + " classes, expected at least "
                        + minimum);
            }
            if (!failures.isEmpty()) {
                throw new IllegalStateException(failures.size()
                        + " classes failed verification/loading");
            }
        } catch (Throwable failure) {
            System.err.println("PATCHER CLASSLOAD ok=false: " + failure);
            failure.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
