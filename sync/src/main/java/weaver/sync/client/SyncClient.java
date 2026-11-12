package weaver.sync.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Weaver sync client: fetch the manifest, verify its format and hashes, diff the
 * content-addressed store, download missing blobs, verify the bytes on disk, and
 * place them atomically in the target directory.
 *
 * <p>Paths are relative and must not contain {@code ..}, absolute paths, or drive
 * letters. Invalid SHA-256 values are rejected. Every step prints a
 * {@code [weaver-sync] ...} marker.
 *
 * <p>Exit codes: 0 success, 2 usage/network/manifest failure, 4 hash mismatch.
 */
public final class SyncClient {

    static final String PREFIX = "[weaver-sync]";
    static final String FORMAT = "weaver-manifest/1";
    /** Default manifest body cap; override with {@code --max-manifest-bytes}. */
    static final long DEFAULT_MAX_MANIFEST_BYTES = 4L * 1024 * 1024;
    /** Hard cap for a blob without a declared size (the in-memory buffer limit). */
    static final long MAX_BLOB_BYTES = Integer.MAX_VALUE - 8L;

    private static volatile OutputStream markerFile;

    private SyncClient() {
    }

    public static void main(String[] args) {
        Path target = null;
        Path store = null;
        Path markers = null;
        String manifestUrl = null;
        long maxManifestBytes = DEFAULT_MAX_MANIFEST_BYTES;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--manifest-url" -> manifestUrl = args[++i];
                case "--target" -> target = Path.of(args[++i]);
                case "--store" -> store = Path.of(args[++i]);
                case "--markers" -> markers = Path.of(args[++i]);
                case "--max-manifest-bytes" -> maxManifestBytes = Math.max(1L, Long.parseLong(args[++i]));
                default -> {
                    System.err.println(PREFIX + " SYNC FAILED reason=unknown-arg arg=" + args[i]);
                    System.exit(2);
                }
            }
        }
        if (manifestUrl == null || target == null || store == null) {
            System.err.println(PREFIX + " SYNC FAILED reason=usage"
                    + " (المطلوب: --manifest-url --target --store [--markers] [--max-manifest-bytes])");
            System.exit(2);
        }
        openMarkers(markers);

        int exit;
        try {
            exit = sync(manifestUrl, target, store, maxManifestBytes);
        } catch (Throwable t) {
            emit("SYNC FAILED reason=" + oneLine(t));
            exit = 2;
        } finally {
            closeMarkers();
        }
        System.exit(exit);
    }

    // ------------------------------------------------------------------ pipeline

    static int sync(String manifestUrl, Path target, Path store) throws Exception {
        return sync(manifestUrl, target, store, DEFAULT_MAX_MANIFEST_BYTES);
    }

    static int sync(String manifestUrl, Path target, Path store, long maxManifestBytes) throws Exception {
        emit("SYNC START manifest=" + manifestUrl);
        long startNanos = System.nanoTime();
        Files.createDirectories(store);
        Files.createDirectories(target);

        // 1) المانيفست
        URI manifestUri = URI.create(manifestUrl);
        byte[] manifestBytes = httpGet(manifestUri, 20_000, maxManifestBytes);
        String manifestText = new String(manifestBytes, StandardCharsets.UTF_8);
        Map<String, Object> root = MiniJson.asObject(MiniJson.parse(manifestText));
        if (root == null) {
            throw new IllegalStateException("manifest is not a JSON object");
        }
        String format = MiniJson.asString(root.get("format"));
        if (!FORMAT.equals(format)) {
            throw new IllegalStateException("unsupported manifest format: " + format);
        }
        Map<String, Object> pack = MiniJson.asObject(root.get("pack"));
        String packId = pack == null ? "?" : MiniJson.asString(pack.get("id"));
        List<Object> artifacts = MiniJson.asArray(root.get("artifacts"));
        if (artifacts == null) {
            throw new IllegalStateException("manifest has no artifacts array");
        }
        emit("MANIFEST OK pack=" + packId + " artifacts=" + artifacts.size()
                + " sha256=" + sha256(manifestBytes));

        // 2) لكل عنصر: أمان المسار → صحة الهش → فرق/تنزيل → تحقق → فحص → وضع ذرّي
        int placed = 0;
        int reused = 0;
        long bytesMoved = 0;
        for (Object raw : artifacts) {
            Map<String, Object> artifact = MiniJson.asObject(raw);
            if (artifact == null) {
                throw new IllegalStateException("artifact entry is not an object");
            }
            String id = MiniJson.asString(artifact.get("id"));
            String relPath = MiniJson.asString(artifact.get("path"));
            String sha = MiniJson.asString(artifact.get("sha256"));
            String kind = MiniJson.asString(artifact.get("kind"));
            // MiniJson يقرأ الأرقام كـDouble ⇒ "13.0" — تحويل يقبل الحالتين (عدد صحيح أو عشري).
            Object sizeRaw = artifact.get("size");
            long size = 0L;
            if (sizeRaw != null) {
                try {
                    size = (long) Double.parseDouble(String.valueOf(sizeRaw));
                } catch (NumberFormatException ignored) {
                    size = 0L;
                }
            }

            if (relPath == null || !pathIsSafe(relPath)) {
                // حماية path traversal: المسارات النسبية فقط، ولا خروج عن المجلد الهدف.
                emit("ARTIFACT REJECTED id=" + id + " path=" + relPath + " reason=path-unsafe");
                return 2;
            }
            if (sha == null || !sha.matches("[a-f0-9]{64}")) {
                emit("ARTIFACT REJECTED id=" + id + " reason=bad-sha256 value=" + sha);
                return 2;
            }
            if (size < 0 || size > MAX_BLOB_BYTES) {
                emit("ARTIFACT REJECTED id=" + id + " reason=bad-size value=" + size
                        + " max=" + MAX_BLOB_BYTES);
                return 2;
            }
            // Never buffer more than the declared size (or the hard cap when undeclared).
            long blobLimit = size > 0 ? size : MAX_BLOB_BYTES;

            // 3) الفرق: الملف نفسه موجود في المخزن بهذه البصمة؟ ⇒ صفر تنزيل (الدلتا مجانية)
            Path cached = store.resolve(sha + ".blob");
            boolean haveBlob = Files.isRegularFile(cached)
                    && sha.equals(sha256(Files.readAllBytes(cached)));
            if (!haveBlob) {
                List<Object> urls = MiniJson.asArray(artifact.get("urls"));
                if (urls == null || urls.isEmpty()) {
                    throw new IllegalStateException("artifact " + id + " has no urls");
                }
                Path part = store.resolve(sha + ".part");
                String usedUrl = null;
                byte[] downloaded = null;
                for (Object rawUrl : urls) {
                    String url = MiniJson.asString(rawUrl);
                    try {
                        URI uri = manifestUri.resolve(url);
                        downloaded = httpGet(uri, 60_000, blobLimit);
                        usedUrl = uri.toString();
                        break;
                    } catch (Throwable t) {
                        emit("DOWNLOAD url-failed url=" + url + " reason=" + oneLine(t));
                    }
                }
                if (downloaded == null) {
                    throw new IllegalStateException("all mirror urls failed for " + id);
                }
                String got = sha256(downloaded);
                if (!sha.equals(got)) {
                    Files.write(part, downloaded, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    Files.deleteIfExists(part);
                    emit("ARTIFACT REJECTED id=" + id + " reason=sha-mismatch expected=" + sha + " got=" + got);
                    return 4;
                }
                Files.write(part, downloaded, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                moveAtomic(part, cached);
                emit("DOWNLOADED id=" + id + " url=" + usedUrl + " bytes=" + downloaded.length + " sha256=" + sha);
            } else {
                emit("STORE HIT id=" + id + " sha256=" + sha + " (صفر تنزيل — نفس البايتات)");
                reused++;
            }

            // 4) Verify the cached bytes on disk before any placement.
            String diskSha = sha256(Files.readAllBytes(cached));
            if (!sha.equals(diskSha)) {
                emit("ARTIFACT REJECTED id=" + id + " reason=disk-sha-mismatch expected=" + sha + " got=" + diskSha);
                return 4;
            }
            // 5) الوضع الذرّي داخل الهدف: نسخة إلى .part ثم نقل ذرّي
            Path destination = target.resolve(relPath).normalize();
            if (!destination.startsWith(target.normalize())) {
                emit("ARTIFACT REJECTED id=" + id + " path=" + relPath + " reason=escapes-target");
                return 2;
            }
            Files.createDirectories(destination.getParent());
            Path temp = destination.resolveSibling(destination.getFileName() + ".part");
            Files.copy(cached, temp, StandardCopyOption.REPLACE_EXISTING);
            moveAtomic(temp, destination);
            placed++;
            bytesMoved += size > 0 ? size : Files.size(destination);
            emit("PLACED path=" + relPath + " sha256=" + sha + " kind=" + kind);
        }

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        emit("SYNC DONE placed=" + placed + " reused=" + reused
                + " elapsed-ms=" + elapsedMs + " bytes=" + bytesMoved);
        return 0;
    }

    // ------------------------------------------------------------------ safety helpers

    /** مسارات نسبية فقط: لا مطلق ولا ".." ولا شرطة قرص — حماية path traversal الموثقة في التصميم. */
    static boolean pathIsSafe(String relPath) {
        if (relPath.isBlank()) {
            return false;
        }
        String normalized = relPath.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) {
            return false;
        }
        for (String segment : normalized.split("/")) {
            if (segment.equals("..") || segment.equals(".") || segment.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    static void moveAtomic(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * GETs a body of at most {@code maxBytes} bytes. A larger declared
     * Content-Length, or a body that streams past the cap, aborts with an
     * {@link IOException} before more than {@code maxBytes} are buffered.
     */
    static byte[] httpGet(URI uri, int timeoutMs, long maxBytes) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.min(timeoutMs, 10_000)))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(timeoutMs))
                .GET()
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " from " + uri);
            }
            OptionalLong declared = response.headers().firstValueAsLong("Content-Length");
            if (declared.isPresent() && declared.getAsLong() > maxBytes) {
                throw new IOException("body-too-large from " + uri + ": Content-Length "
                        + declared.getAsLong() + " exceeds limit " + maxBytes);
            }
            return readLimited(body, maxBytes, uri);
        }
    }

    static byte[] readLimited(InputStream in, long maxBytes, URI uri) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(maxBytes, 64 * 1024));
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) >= 0) {
            total += n;
            if (total > maxBytes) {
                throw new IOException("body-too-large from " + uri + ": more than "
                        + maxBytes + " bytes");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 غير متاح", e);
        }
    }

    // ------------------------------------------------------------------ markers

    static void openMarkers(Path file) {
        if (file == null) {
            return;
        }
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            markerFile = Files.newOutputStream(file, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (Throwable t) {
            markerFile = null;
        }
    }

    static void closeMarkers() {
        if (markerFile != null) {
            try {
                markerFile.close();
            } catch (Throwable ignored) {
                // best effort
            }
        }
    }

    static void emit(String message) {
        String line = PREFIX + " " + message;
        System.out.println(line);
        System.out.flush();
        OutputStream sink = markerFile;
        if (sink != null) {
            try {
                sink.write((line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
                sink.flush();
            } catch (Throwable ignored) {
                // never break the sync over a log line
            }
        }
    }

    static String oneLine(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && (root instanceof java.lang.reflect.InvocationTargetException
                || root instanceof java.util.concurrent.CompletionException
                || root instanceof java.util.concurrent.ExecutionException)) {
            root = root.getCause();
        }
        String message = root.getMessage();
        String text = root.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        return text.replace('\r', ' ').replace('\n', ' ');
    }
}
