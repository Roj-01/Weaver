package weaver.sync.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Manifest server for Weaver sync.
 *
 * <p>يفحص مجلد محتوى (مثلاً حزمة سيرفر: مجلد فيه ملفات مودات)، يحسب sha256 لكل ملف،
 * ويخدم:
 * <ul>
 *   <li>{@code GET /weaver/manifest.json} — مانيفست {@code weaver-manifest/1} مبني لحظياً من القرص،</li>
 *   <li>{@code GET /blobs/<sha256>} — بايتات الملف المعني (content-addressed — الكاش مجاني عند العميل)،</li>
 *   <li>{@code GET /healthz} — جاهزية بسيطة.</li>
 * </ul>
 *
 * <p>كل شيء بلا تبعيات خارجية (خادم JDK المدمج) وبلا أي ثقة في العميل — العلامات تُطبع على stdout
 * بصيغة {@code [weaver-sync] …} لتُقرأ بالـgrep من الاختبارات.
 *
 * <p>الاستخدام: {@code java weaver.sync.server.ManifestServer --content <dir> --port 8787 [--pack-id id] [--pack-name name] [--rev N]}
 */
public final class ManifestServer {

    static final String PREFIX = "[weaver-sync]";

    private ManifestServer() {
    }

    public static void main(String[] args) throws Exception {
        Path content = null;
        Path stopFile = null;
        Path manifestFileOverride = null;
        int port = 8787;
        String packId = "local-smp";
        String packName = "SMP Roj";
        int rev = 1;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--content" -> content = Path.of(args[++i]);
                case "--stop-file" -> stopFile = Path.of(args[++i]);
                case "--manifest-file" -> manifestFileOverride = Path.of(args[++i]);
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--pack-id" -> packId = args[++i];
                case "--pack-name" -> packName = args[++i];
                case "--rev" -> rev = Integer.parseInt(args[++i]);
                default -> {
                    System.err.println("وسيط غير مفهوم: " + args[i]);
                    System.exit(2);
                }
            }
        }
        if (content == null || !Files.isDirectory(content)) {
            System.err.println(PREFIX + " MANIFEST SERVER FAILED reason=content-dir-missing path=" + content);
            System.exit(2);
        }
        if (stopFile != null) {
            Files.deleteIfExists(stopFile);
        }

        List<Artifact> artifacts = collect(content);
        // --manifest-file: يخدم ملف مانيفست جاهز حرفياً (لاختبارات الرفض وبصمات يدوية) مع بقاء /blobs من مجلد المحتوى.
        String manifest = manifestFileOverride != null
                ? Files.readString(manifestFileOverride, StandardCharsets.UTF_8)
                : buildManifest(packId, packName, rev, artifacts);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/weaver/manifest.json", exchange -> respondJson(exchange, 200, manifest));
        server.createContext("/healthz", exchange -> respondText(exchange, 200, "ok"));
        server.createContext("/blobs/", exchange -> {
            String sha = exchange.getRequestURI().getPath().substring("/blobs/".length());
            Artifact match = find(artifacts, sha);
            if (match == null) {
                respondText(exchange, 404, "no such blob");
                return;
            }
            byte[] bytes = Files.readAllBytes(match.absolutPath);
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.setExecutor(null);
        server.start();

        System.out.println(PREFIX + " MANIFEST SERVER READY host=127.0.0.1 port=" + port
                + " artifacts=" + artifacts.size()
                + " pack=" + packId + " rev=" + rev
                + " content=" + content.toAbsolutePath().toString().replace('\\', '/'));
        for (Artifact artifact : artifacts) {
            System.out.println(PREFIX + " MANIFEST ARTIFACT id=" + artifact.id
                    + " path=" + artifact.relPath
                    + " size=" + artifact.size
                    + " sha256=" + artifact.sha256);
        }
        System.out.println(PREFIX + " MANIFEST SHA256=" + sha256(manifest.getBytes(StandardCharsets.UTF_8)));

        // الخادم يعمل حتى يظهر ملف الإيقاف (أسلوب الورشة) أو حتى ساعة كحد أقصى.
        long deadline = System.currentTimeMillis() + 3_600_000L;
        while (System.currentTimeMillis() < deadline) {
            if (stopFile != null && Files.exists(stopFile)) {
                break;
            }
            Thread.sleep(250L);
        }
        server.stop(0);
        System.out.println(PREFIX + " MANIFEST SERVER STOPPED");
    }

    // ------------------------------------------------------------------ collecting

    record Artifact(String id, String version, String relPath, String sha256, long size, Path absolutPath) {
    }

    static List<Artifact> collect(Path content) throws IOException {
        List<Artifact> artifacts = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(content)) {
            List<Path> files = walk.filter(Files::isRegularFile)
                    .sorted()
                    .toList();
            for (Path file : files) {
                String rel = content.relativize(file).toString().replace('\\', '/');
                byte[] bytes = Files.readAllBytes(file);
                String sha = sha256(bytes);
                String name = file.getFileName().toString();
                String id = name.toLowerCase(Locale.ROOT).replaceAll("\\.jar$", "").replaceAll("[^a-z0-9._-]+", "-");
                String version = extractVersion(name);
                artifacts.add(new Artifact(id, version, rel, sha, bytes.length, file));
            }
        }
        return artifacts;
    }

    /** نسخة بسيطة: أي جزء يشبه vN أو رقم.x من اسم الملف — وإلا "1". */
    static String extractVersion(String fileName) {
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("v(\\d+(?:\\.\\d+)*)").matcher(fileName);
        return matcher.find() ? "v" + matcher.group(1) : "1";
    }

    static Artifact find(List<Artifact> artifacts, String sha256) {
        for (Artifact artifact : artifacts) {
            if (artifact.sha256.equalsIgnoreCase(sha256)) {
                return artifact;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ manifest

    static String buildManifest(String packId, String packName, int rev, List<Artifact> artifacts) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"format\": \"weaver-manifest/1\",\n");
        sb.append("  \"pack\": { \"id\": ").append(quote(packId))
                .append(", \"name\": ").append(quote(packName))
                .append(", \"rev\": ").append(rev).append(" },\n");
        sb.append("  \"game\": { \"version\": \"26.3\" },\n");
        sb.append("  \"artifacts\": [\n");
        for (int i = 0; i < artifacts.size(); i++) {
            Artifact a = artifacts.get(i);
            sb.append("    { \"id\": ").append(quote(a.id))
                    .append(", \"version\": ").append(quote(a.version))
                    .append(", \"path\": ").append(quote(a.relPath))
                    .append(", \"kind\": ").append(quote(a.relPath.startsWith("mods/") ? "module" : "file"))
                    .append(", \"sha256\": ").append(quote(a.sha256))
                    .append(", \"size\": ").append(a.size)
                    .append(", \"urls\": [ ").append(quote("/blobs/" + a.sha256)).append(" ] }")
                    .append(i + 1 < artifacts.size() ? "," : "").append("\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    static String quote(String raw) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : raw.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 غير متاح", e);
        }
    }

    static void respondJson(HttpExchange exchange, int code, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        respondText(exchange, code, body);
    }

    static void respondText(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    static String urlEncode(String raw) {
        return URLEncoder.encode(raw, StandardCharsets.UTF_8);
    }
}
