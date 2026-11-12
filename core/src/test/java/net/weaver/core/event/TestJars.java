package net.weaver.core.event;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public final class TestJars {

    private TestJars() {
    }

    public static Path create(String modId, String mainClass, Class<?>... types) throws IOException {
        Path jar = Files.createTempFile("weaver-scanner-", ".jar");
        String metadata = "{\"schemaVersion\":2,\"id\":\"" + modId + "\",\"version\":\"1.0.0\","
                + "\"mainClass\":\"" + mainClass + "\"}";
        try (OutputStream raw = Files.newOutputStream(jar);
             JarOutputStream out = new JarOutputStream(raw)) {
            put(out, "weaver.mod.json", metadata.getBytes(StandardCharsets.UTF_8));
            for (Class<?> type : types) {
                put(out, type.getName().replace('.', '/') + ".class", readClass(type));
            }
        }
        return jar;
    }

    private static byte[] readClass(Class<?> type) throws IOException {
        String resource = type.getName().replace('.', '/') + ".class";
        try (InputStream in = type.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing class resource: " + resource);
            }
            return in.readAllBytes();
        }
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws IOException {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }
}
