package weaver.mirror.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Mirror server for Weaver sync.
 *
 * <p>عملية واحدة تخدم أكثر من «مجلد محتوى» (مرآة): كل مرآة خادم HTTP صغير على منفذها الخاص
 * ({@code /blobs/<sha256>} من جذرها)، وخادم بيانات يخدم المانيفست {@code weaver-manifest/1}
 * حيث كل عنصر يحمل قائمة {@code urls} تشاور كل المرايا التي تملك البايتات فعلاً.
 *
 * <p>دعم مقصود للاستكمال: {@code Range: bytes=N-} ⇒ 206 Partial Content مع
 * {@code Content-Range} (الباقي فقط على الشبكة).
 *
 * <p>أدوات اختبار مضبطة داخل الخادم (لإنتاج الأدلة فقط):
 * <ul>
 *   <li>{@code --cut-once-bytes N}: أول GET كامل يُقطع بعد N بايت (إغلاق متعمد في المنتصف).</li>
 *   <li>{@code --corrupt-once}: أول استجابة بلايتات مُفسدة (لفحص رفض البصمة عند العميل).</li>
 *   <li>{@code /ctl/mirror/down?id=…} و{@code /ctl/mirror/up?id=…}: إطفاء/إحياء مرآة فعلياً
 *       (يُغلق مقبس الاستماع ⇒ رفض اتصال حقيقي عند العميل) و{@code /ctl/status}.</li>
 * </ul>
 *
 * <p>الاستخدام:
 * {@code java weaver.mirror.server.MirrorServer --data-port 8820 --mirror id=m1,port=8821,root=<dir> --mirror id=m2,port=8822,root=<dir> [خيارات]}
 */
public final class MirrorServer {

    static final String PREFIX = "[weaver-mirror]";

    private MirrorServer() {
    }

    // ------------------------------------------------------------------ state

    /** مرآة = مجلد محتوى + خادم HTTP خاص بها. {@code http == null} يعني مطفأة. */
    static final class Mirror {
        final String id;
        final int port;
        final Path root;
        volatile HttpServer http;
        volatile Map<String, Path> blobs = Map.of();      // sha256 → ملف
        volatile List<Entry> entries = List.of();          // مرتبة بمسار نسبي
        volatile long refreshCount = 0;

        Mirror(String id, int port, Path root) {
            this.id = id;
            this.port = port;
            this.root = root;
        }
    }

    record Entry(String relPath, String sha256, long size, Path file) {
    }

    static final List<Mirror> mirrors = new ArrayList<>();
    static final AtomicBoolean cutArmed = new AtomicBoolean(false);
    static final AtomicLong cutBytes = new AtomicLong(0);
    static final AtomicBoolean corruptArmed = new AtomicBoolean(false);
    static String packId = "weaver-pack";
    static String packName = "Weaver Mirrors";
    static int rev = 1;

    public static void main(String[] args) throws Exception {
        int dataPort = 8820;
        Path stopFile = null;
        List<String> specs = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--data-port" -> dataPort = Integer.parseInt(args[++i]);
                case "--mirror" -> specs.add(args[++i]);
                case "--stop-file" -> stopFile = Path.of(args[++i]);
                case "--pack-id" -> packId = args[++i];
                case "--pack-name" -> packName = args[++i];
                case "--rev" -> rev = Integer.parseInt(args[++i]);
                case "--cut-once-bytes" -> {
                    cutBytes.set(Long.parseLong(args[++i]));
                    cutArmed.set(true);
                }
                case "--corrupt-once" -> corruptArmed.set(true);
                default -> {
                    System.err.println(PREFIX + " SERVER FAILED reason=unknown-arg arg=" + args[i]);
                    System.exit(2);
                }
            }
        }
        if (specs.isEmpty()) {
            System.err.println(PREFIX + " SERVER FAILED reason=no-mirrors");
            System.exit(2);
        }
        for (String spec : specs) {
            String id = null;
            int port = -1;
            String root = null;
            for (String part : spec.split(",")) {
                int eq = part.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String k = part.substring(0, eq).trim();
                String v = part.substring(eq + 1).trim();
                switch (k) {
                    case "id" -> id = v;
                    case "port" -> port = Integer.parseInt(v);
                    case "root" -> root = v;
                    default -> {
                        System.err.println(PREFIX + " SERVER FAILED reason=bad-mirror-spec spec=" + spec);
                        System.exit(2);
                    }
                }
            }
            if (id == null || port <= 0 || root == null) {
                System.err.println(PREFIX + " SERVER FAILED reason=bad-mirror-spec spec=" + spec);
                System.exit(2);
            }
            Path rootPath = Path.of(root);
            if (!Files.isDirectory(rootPath)) {
                System.err.println(PREFIX + " SERVER FAILED reason=mirror-root-missing root=" + root);
                System.exit(2);
            }
            mirrors.add(new Mirror(id, port, rootPath));
        }
        if (stopFile != null) {
            Files.deleteIfExists(stopFile);
        }

        for (Mirror m : mirrors) {
            refresh(m);
            startMirror(m);
        }

        HttpServer data = HttpServer.create(new InetSocketAddress("127.0.0.1", dataPort), 0);
        data.createContext("/healthz", ex -> respondText(ex, 200, "ok"));
        data.createContext("/weaver/manifest.json", ex -> respondJson(ex, 200, buildManifest()));
        data.createContext("/ctl/mirror/down", MirrorServer::ctlDown);
        data.createContext("/ctl/mirror/up", MirrorServer::ctlUp);
        data.createContext("/ctl/status", ex -> respondText(ex, 200, statusText()));
        data.setExecutor(null);
        data.start();

        log("DATA SERVER READY host=127.0.0.1 port=" + dataPort
                + " mirrors=" + mirrors.size() + " manifest=/weaver/manifest.json");
        String manifest = buildManifest();
        log("MANIFEST BUILT artifacts=" + mirrors.get(0).entries.size()
                + " mirrors=" + mirrors.size());
        log("MANIFEST SHA256=" + sha256(manifest.getBytes(StandardCharsets.UTF_8)));

        // يعمل حتى ملف الإيقاف (أسلوب الورشة) أو ساعة كحد أقصى.
        long deadline = System.currentTimeMillis() + 3_600_000L;
        while (System.currentTimeMillis() < deadline) {
            if (stopFile != null && Files.exists(stopFile)) {
                break;
            }
            Thread.sleep(200L);
        }
        for (Mirror m : mirrors) {
            HttpServer h = m.http;
            if (h != null) {
                h.stop(0);
            }
        }
        data.stop(0);
        log("SERVER STOPPED");
    }

    // ------------------------------------------------------------------ mirrors lifecycle

    static void startMirror(Mirror m) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= 20; attempt++) {
            try {
                HttpServer h = HttpServer.create(new InetSocketAddress("127.0.0.1", m.port), 0);
                h.createContext("/healthz", ex -> respondText(ex, 200, "ok"));
                h.createContext("/blobs/", ex -> serveBlob(m, ex));
                h.setExecutor(null);
                h.start();
                m.http = h;
                log("MIRROR READY id=" + m.id + " port=" + m.port
                        + " files=" + m.blobs.size()
                        + " root=" + m.root.toAbsolutePath().toString().replace('\\', '/'));
                return;
            } catch (IOException e) {
                last = e;
                try {
                    Thread.sleep(250L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        throw last;
    }

    static void ctlDown(HttpExchange ex) {
        try {
            String id = param(ex, "id");
            Mirror m = find(id);
            if (m == null) {
                respondText(ex, 404, "unknown mirror " + id);
                return;
            }
            HttpServer h = m.http;
            if (h == null) {
                respondText(ex, 200, "mirror " + id + " already down");
                return;
            }
            m.http = null;                 // أولاً: لن تخدم أي معالجة جديدة
            h.stop(0);                     // إغلاق مقبس الاستماع ⇒ رفض اتصال حقيقي
            log("CTL MIRROR DOWN id=" + id + " port=" + m.port + " (مقبس مُغلق ⇒ connection refused)");
            respondText(ex, 200, "mirror " + id + " down");
        } catch (Throwable t) {
            respondTextQuiet(ex, 500, "ctl error " + t);
        }
    }

    static void ctlUp(HttpExchange ex) {
        try {
            String id = param(ex, "id");
            Mirror m = find(id);
            if (m == null) {
                respondText(ex, 404, "unknown mirror " + id);
                return;
            }
            if (m.http != null) {
                respondText(ex, 200, "mirror " + id + " already up");
                return;
            }
            try {
                startMirror(m);
                log("CTL MIRROR UP id=" + id + " port=" + m.port);
                respondText(ex, 200, "mirror " + id + " up");
            } catch (IOException e) {
                log("CTL MIRROR UP FAILED id=" + id + " reason=" + oneLine(e));
                respondText(ex, 500, "mirror " + id + " up failed: " + oneLine(e));
            }
        } catch (Throwable t) {
            respondTextQuiet(ex, 500, "ctl error " + t);
        }
    }

    static Mirror find(String id) {
        for (Mirror m : mirrors) {
            if (m.id.equals(id)) {
                return m;
            }
        }
        return null;
    }

    static String statusText() {
        StringBuilder sb = new StringBuilder();
        for (Mirror m : mirrors) {
            sb.append(m.id).append('=').append(m.http == null ? "down" : "up")
                    .append(" port=").append(m.port)
                    .append(" files=").append(m.blobs.size()).append('\n');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ blob serving (Range + قطع/إفساد للاختبار)

    static void serveBlob(Mirror m, HttpExchange ex) {
        String sha = "";
        try {
            String path = ex.getRequestURI().getPath();
            sha = path.substring("/blobs/".length());
            Path file = m.blobs.get(sha.toLowerCase(Locale.ROOT));
            if (file == null) {
                respondText(ex, 404, "no such blob");
                return;
            }
            long size = Files.size(file);

            long start = 0;
            long end = size - 1;
            boolean ranged = false;
            String range = ex.getRequestHeaders().getFirst("Range");
            if (range != null && range.startsWith("bytes=")) {
                String spec = range.substring("bytes=".length()).trim();
                int dash = spec.indexOf('-');
                if (dash < 0) {
                    respondText(ex, 400, "bad range");
                    return;
                }
                String fromRaw = spec.substring(0, dash).trim();
                String toRaw = spec.substring(dash + 1).trim();
                try {
                    start = fromRaw.isEmpty() ? 0 : Long.parseLong(fromRaw);
                    end = toRaw.isEmpty() ? size - 1 : Long.parseLong(toRaw);
                } catch (NumberFormatException e) {
                    respondText(ex, 400, "bad range");
                    return;
                }
                ranged = true;
                if (start < 0 || start >= size || end >= size || end < start) {
                    ex.getResponseHeaders().set("Content-Range", "bytes */" + size);
                    respondText(ex, 416, "range unsatisfiable");
                    return;
                }
            }
            long len = end - start + 1;

            ex.getResponseHeaders().set("Content-Type", "application/octet-stream");
            ex.getResponseHeaders().set("Accept-Ranges", "bytes");
            if (ranged) {
                ex.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + size);
            }

            // (أ) قطع متعمد مرة واحدة: أول GET كامل — نعلن الطول الكامل ثم نكتب N بايت ونغلق.
            // فخ مقيس (out/probe/CutProbe.java): استدعاء out.close() على استجابة ناقصة الطول
            // يُبقي الاتصال معلقاً (العميل ينتظر حتى المهلة ~8s+ ثم timeout)، بينما ex.close()
            // وحده يغلق المقبس فوراً (curl exit=18، partial file — خلال 859ms).
            long cut = cutBytes.get();
            if (!ranged && cut > 0 && size > cut && cutArmed.compareAndSet(true, false)) {
                ex.sendResponseHeaders(200, size);
                OutputStream out = ex.getResponseBody();
                try (InputStream in = Files.newInputStream(file)) {
                    copy(in, out, cut);
                    out.flush();
                }
                log("BLOB SERVICE mirror=" + m.id + " sha=" + sha.substring(0, 12)
                        + " range=0-" + (size - 1) + " bytes=" + cut + " code=200"
                        + " result=cut-after-" + cut + " declared-len=" + size);
                ex.close(); // إغلاق الاتصال الناقص — بلا out.close()
                return;
            }

            int code = ranged ? 206 : 200;

            // (ب) إفساد متعمد مرة واحدة: بايت مقلوب، والطول كامل — العميل هو من يجب أن يرفض.
            if (corruptArmed.compareAndSet(true, false)) {
                byte[] bytes = Files.readAllBytes(file);
                int at = (int) Math.min(16, bytes.length - 1);
                bytes[at] ^= 0x5A;
                ex.sendResponseHeaders(code, len);
                try (OutputStream out = ex.getResponseBody()) {
                    out.write(bytes, (int) start, (int) len);
                }
                log("BLOB SERVICE mirror=" + m.id + " sha=" + sha.substring(0, 12)
                        + " range=" + start + "-" + end + " bytes=" + len + " code=" + code
                        + " result=corrupted-once at-offset=" + at);
                return;
            }

            ex.sendResponseHeaders(code, len);
            try (OutputStream out = ex.getResponseBody(); InputStream in = Files.newInputStream(file)) {
                skipFully(in, start);
                copy(in, out, len);
            }
            log("BLOB SERVICE mirror=" + m.id + " sha=" + sha.substring(0, 12)
                    + " range=" + start + "-" + end + " bytes=" + len + " code=" + code
                    + (ranged ? " (الباقي فقط — استكمال)" : ""));
        } catch (Throwable t) {
            try {
                ex.close();
            } catch (Throwable ignored) {
                // best effort
            }
            log("BLOB ERROR mirror=" + m.id + " sha=" + shortSha(sha) + " reason=" + oneLine(t));
        }
    }

    // ------------------------------------------------------------------ manifest

    static void refresh(Mirror m) {
        Map<String, Path> blobs = new TreeMap<>();
        List<Entry> entries = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(m.root)) {
            List<Path> files = walk.filter(Files::isRegularFile).sorted().toList();
            for (Path file : files) {
                String rel = m.root.relativize(file).toString().replace('\\', '/');
                long size = Files.size(file);
                String sha = sha256File(file);
                blobs.put(sha, file);
                entries.add(new Entry(rel, sha, size, file));
            }
        } catch (IOException e) {
            log("MIRROR REFRESH FAILED id=" + m.id + " reason=" + oneLine(e));
        }
        entries.sort((a, b) -> a.relPath.compareTo(b.relPath));
        m.blobs = blobs;
        m.entries = entries;
        m.refreshCount++;
        log("MIRROR REFRESH id=" + m.id + " root=" + m.root.toAbsolutePath().toString().replace('\\', '/')
                + " files=" + entries.size());
    }

    static String buildManifest() {
        for (Mirror m : mirrors) {
            refresh(m);
        }
        Mirror primary = mirrors.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"format\": \"weaver-manifest/1\",\n");
        sb.append("  \"pack\": { \"id\": ").append(quote(packId))
                .append(", \"name\": ").append(quote(packName))
                .append(", \"rev\": ").append(rev).append(" },\n");
        sb.append("  \"game\": { \"version\": \"26.3\" },\n");
        sb.append("  \"artifacts\": [\n");
        List<Entry> entries = primary.entries;
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            sb.append("    { \"id\": ").append(quote(artifactId(e.relPath)))
                    .append(", \"version\": ").append(quote(extractVersion(e.relPath)))
                    .append(", \"path\": ").append(quote(e.relPath))
                    .append(", \"kind\": ").append(quote(e.relPath.startsWith("mods/") ? "module" : "file"))
                    .append(", \"sha256\": ").append(quote(e.sha256))
                    .append(", \"size\": ").append(e.size)
                    .append(", \"urls\": [ ");
            boolean first = true;
            for (Mirror m : mirrors) {
                if (m.blobs.containsKey(e.sha256)) {
                    if (!first) {
                        sb.append(", ");
                    }
                    sb.append(quote("http://127.0.0.1:" + m.port + "/blobs/" + e.sha256));
                    first = false;
                }
            }
            sb.append(" ] }").append(i + 1 < entries.size() ? "," : "").append("\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    static String artifactId(String relPath) {
        String name = relPath.substring(relPath.lastIndexOf('/') + 1);
        return name.toLowerCase(Locale.ROOT).replaceAll("\\.jar$", "").replaceAll("[^a-z0-9._-]+", "-");
    }

    static String extractVersion(String fileName) {
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("v(\\d+(?:\\.\\d+)*)").matcher(fileName);
        return matcher.find() ? "v" + matcher.group(1) : "1";
    }

    // ------------------------------------------------------------------ helpers

    static String sha256File(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) >= 0) {
                digest.update(buf, 0, n);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 غير متاح", e);
        }
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 غير متاح", e);
        }
    }

    static String shortSha(String sha) {
        return sha == null ? "?" : sha.length() > 12 ? sha.substring(0, 12) : sha;
    }

    static void copy(InputStream in, OutputStream out, long len) throws IOException {
        byte[] buf = new byte[65536];
        long remaining = len;
        while (remaining > 0) {
            int n = in.read(buf, 0, (int) Math.min(buf.length, remaining));
            if (n < 0) {
                throw new IOException("unexpected end of file, missing " + remaining + " bytes");
            }
            out.write(buf, 0, n);
            remaining -= n;
        }
    }

    static void skipFully(InputStream in, long n) throws IOException {
        long remaining = n;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) {
                if (in.read() < 0) {
                    throw new IOException("unexpected end of file while skipping");
                }
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    static String param(HttpExchange ex, String name) {
        String query = ex.getRequestURI().getRawQuery();
        if (query == null) {
            return "";
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return "";
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

    static String oneLine(Throwable t) {
        String message = t.getMessage();
        String text = t.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        return text.replace('\r', ' ').replace('\n', ' ');
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

    static void respondTextQuiet(HttpExchange exchange, int code, String body) {
        try {
            respondText(exchange, code, body);
        } catch (IOException ignored) {
            // best effort
        }
    }

    static void log(String message) {
        System.out.println(PREFIX + " " + message);
        System.out.flush();
    }
}
