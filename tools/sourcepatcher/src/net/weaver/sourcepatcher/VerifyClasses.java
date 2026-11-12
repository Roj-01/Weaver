package net.weaver.sourcepatcher;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class VerifyClasses {
    private VerifyClasses() {
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("usage: VerifyClasses <jar> <classpath-file>");
            System.exit(2);
        }
        try {
            Path jar = Path.of(args[0]).toAbsolutePath().normalize();
            Path classpath = Path.of(args[1]).toAbsolutePath().normalize();
            if (!Files.isRegularFile(jar)) {
                throw new IllegalStateException("jar does not exist: " + jar);
            }
            if (!Files.isRegularFile(classpath)) {
                throw new IllegalStateException("classpath file does not exist: " + classpath);
            }

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
                String normalized = path.toString().replace('\\', '/');
                if (!normalized.contains("/libraries/")) {
                    continue;
                }
                if (path.equals(jar)) {
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

            System.out.println("SOURCE PATCHER CLASSLOAD jar=" + jar.getFileName()
                    + " ok=" + ok + " failures=" + failures.size());
            for (int index = 0; index < Math.min(25, failures.size()); index++) {
                System.out.println("  FAIL " + failures.get(index));
            }
            if (ok == 0) {
                throw new IllegalStateException("loaded no classes from " + jar);
            }
            if (!failures.isEmpty()) {
                throw new IllegalStateException(failures.size()
                        + " classes failed verification/loading");
            }
        } catch (Throwable failure) {
            System.err.println("SOURCE PATCHER CLASSLOAD ok=false: " + failure);
            failure.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
