package weaver.mirror.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Weaver mirror sync client: multiple mirrors plus resumable downloads.
 *
 * <p>Pipeline: manifest verification, content-addressed store diff, download,
 * sha256 verification, atomic placement. This client adds the transport layer:
 * <ul>
 *   <li><b>مرايا:</b> لكل عنصر قائمة {@code urls}؛ تُجرَّب بالترتيب، والانتقال للمرآة التالية
 *       لا يحدث إلا بعد <b>فشل فعلي</b> (رفض اتصال/قطع) — والانتقال <b>كسول ومرة واحدة</b>:
 *       بعد أول فشل تُذكَّر المرآة الميتة لهذه التشغيلة فلا يُعاد تجريبها.</li>
 *   <li><b>استكمال:</b> التنزيل يُكتب في {@code <sha>.part} الباقي؛ عند القطع (من الخادم أو
 *       من العميل) يُطلب فقط الباقي بـ{@code Range: bytes=K-} ويُبنى الملف على ما كُسب،
 *       ثم يُفحص sha256 قبل القبول. البقايا من تشغيلة سابقة تُستأنف أيضاً.</li>
 * </ul>
 *
 * <p>Exit codes: 0 success, 2 network/manifest failure (including all mirrors
 * down), 4 sha256 mismatch, 9 intentional client stop (test only: --stop-after).
 *
 * <p>الاستخدام: {@code java weaver.mirror.client.MirrorSyncClient --manifest-url <url> --target <dir> --store <dir> [--markers file] [--retries N] [--stop-after N]}
 */
public final class MirrorSyncClient {

    static final String PREFIX = "[weaver-mirror]";
    static final String FORMAT = "weaver-manifest/1";
    /** Default manifest body cap; override with {@code --max-manifest-bytes}. */
    static final long DEFAULT_MAX_MANIFEST_BYTES = 4L * 1024 * 1024;
    /** Disk cap for a blob without a declared size; a declared size is always the cap. */
    static final long MAX_UNDECLARED_BLOB_BYTES = 1L << 30;

    /** محاكاة «عميل يتوقف» في المنتصف (اختبار الاستكمال فقط) — يُلتقط في main ويخرج 9. */
    static final class StopRequested extends RuntimeException {
    }

    static long stopAfter = 0;
    static boolean stopArmed = false;

    private static volatile OutputStream markerFile;

    private MirrorSyncClient() {
    }

    public static void main(String[] args) {
        Path target = null;
        Path store = null;
        Path markers = null;
        String manifestUrl = null;
        int retries = 3;
        int connectMs = 3000;
        int requestMs = 60_000;
        long maxManifestBytes = DEFAULT_MAX_MANIFEST_BYTES;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--manifest-url" -> manifestUrl = args[++i];
                case "--target" -> target = Path.of(args[++i]);
                case "--store" -> store = Path.of(args[++i]);
                case "--markers" -> markers = Path.of(args[++i]);
                case "--retries" -> retries = Math.max(1, Integer.parseInt(args[++i]));
                case "--connect-timeout-ms" -> connectMs = Integer.parseInt(args[++i]);
                case "--request-timeout-ms" -> requestMs = Integer.parseInt(args[++i]);
                case "--stop-after" -> stopAfter = Long.parseLong(args[++i]);
                case "--max-manifest-bytes" -> maxManifestBytes = Math.max(1L, Long.parseLong(args[++i]));
                default -> {
                    System.err.println(PREFIX + " SYNC FAILED reason=unknown-arg arg=" + args[i]);
                    System.exit(2);
                }
            }
        }
        if (manifestUrl == null || target == null || store == null) {
            System.err.println(PREFIX + " SYNC FAILED reason=usage"
                    + " (المطلوب: --manifest-url --target --store [--markers] [--retries] [--stop-after] [--max-manifest-bytes])");
            System.exit(2);
        }
        openMarkers(markers);

        int exit;
        try {
            exit = sync(manifestUrl, target, store, retries, connectMs, requestMs, maxManifestBytes);
        } catch (StopRequested s) {
            exit = 9;
        } catch (Throwable t) {
            emit("SYNC FAILED reason=" + oneLine(t));
            exit = 2;
        } finally {
            closeMarkers();
        }
        System.exit(exit);
    }

    // ------------------------------------------------------------------ pipeline

    static int sync(String manifestUrl, Path target, Path store, int retries, int connectMs, int requestMs) throws Exception {
        return sync(manifestUrl, target, store, retries, connectMs, requestMs, DEFAULT_MAX_MANIFEST_BYTES);
    }

    static int sync(String manifestUrl, Path target, Path store, int retries, int connectMs, int requestMs,
                    long maxManifestBytes) throws Exception {
        emit("SYNC START manifest=" + manifestUrl + " target=" + target + " store=" + store);
        long startNanos = System.nanoTime();
        Files.createDirectories(store);
        Files.createDirectories(target);

        // 1) المانيفست
        URI manifestUri = URI.create(manifestUrl);
        byte[] manifestBytes;
        try {
            manifestBytes = httpGet(manifestUri, connectMs, requestMs, maxManifestBytes);
        } catch (Throwable t) {
            emit("MANIFEST FAILED url=" + manifestUrl + " reason=" + oneLine(t));
            emit("SYNC FAILED reason=manifest-unreachable");
            return 2;
        }
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

        // 2) لكل عنصر: أمان المسار → صحة الهش → فرق/تنزيل بالمرايا مع استكمال → تحقق sha256 → وضع ذرّي
        int placed = 0;
        int reused = 0;
        long bytesMoved = 0;
        Set<String> deadBases = new HashSet<>();   // مرايا فشلت فعلياً في هذه التشغيلة (lazy-once)
        stopArmed = stopAfter > 0;

        for (Object raw : artifacts) {
            Map<String, Object> artifact = MiniJson.asObject(raw);
            if (artifact == null) {
                throw new IllegalStateException("artifact entry is not an object");
            }
            String id = MiniJson.asString(artifact.get("id"));
            String relPath = MiniJson.asString(artifact.get("path"));
            String sha = MiniJson.asString(artifact.get("sha256"));
            String kind = MiniJson.asString(artifact.get("kind"));
            // MiniJson reads numbers as Double, so "262144.0" is possible; accept both forms.
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
                emit("ARTIFACT REJECTED id=" + id + " path=" + relPath + " reason=path-unsafe");
                return 2;
            }
            if (sha == null || !sha.matches("[a-f0-9]{64}")) {
                emit("ARTIFACT REJECTED id=" + id + " reason=bad-sha256 value=" + sha);
                return 2;
            }

            // 3) الفرق: نفس البايتات في المخزن؟ ⇒ صفر تنزيل
            Path cached = store.resolve(sha + ".blob");
            boolean haveBlob = Files.isRegularFile(cached)
                    && sha.equals(sha256File(cached));
            String usedUrl = null;
            if (!haveBlob) {
                List<Object> urls = MiniJson.asArray(artifact.get("urls"));
                if (urls == null || urls.isEmpty()) {
                    throw new IllegalStateException("artifact " + id + " has no urls");
                }
                List<URI> resolved = new ArrayList<>();
                for (Object rawUrl : urls) {
                    String url = MiniJson.asString(rawUrl);
                    try {
                        resolved.add(manifestUri.resolve(url));
                    } catch (Throwable t) {
                        emit("URL INVALID artifact=" + id + " url=" + url + " reason=" + oneLine(t));
                    }
                }
                Path part = store.resolve(sha + ".part");
                long kept0 = Files.isRegularFile(part) ? Files.size(part) : 0L;
                boolean partComplete = false;
                if (size > 0 && kept0 >= size) {
                    // A leftover as long as (or longer than) the declared size cannot be
                    // resumed (Range: bytes=<size>- is unsatisfiable): verify it directly.
                    if (sha.equals(sha256File(part))) {
                        partComplete = true;
                    } else {
                        Files.deleteIfExists(part);    // بقايا فاسدة/أكبر من المعلن
                        emit("RESUME DISCARDED file=" + relPath + " bytes-kept=" + kept0
                                + " total=" + size + " reason=sha-mismatch part-removed=true");
                        kept0 = 0L;
                    }
                }
                if (partComplete) {
                    moveAtomic(part, cached);
                    emit("RESUME COMPLETE file=" + relPath + " bytes=" + kept0
                            + " sha256-match=true (leftover .part already complete — no network request)");
                    usedUrl = "store";
                } else {
                    if (kept0 > 0) {
                        emit("RESUME RECOVERED file=" + relPath + " bytes-kept=" + kept0
                                + " (بقايا من تشغيلة سابقة — بلا أي طلب شبكة بعد)");
                    }

                    Outcome outcome = downloadWithMirrors(resolved, part, sha, size, kept0, deadBases,
                            retries, connectMs, requestMs, relPath, id);
                    if (!outcome.ok) {
                        emit("MIRRORS EXHAUSTED id=" + id + " tried=" + outcome.tried);
                        if (Files.isRegularFile(part) && Files.size(part) == 0) {
                            Files.deleteIfExists(part);
                        }
                        emit("SYNC FAILED reason=all-mirrors-down artifact=" + id);
                        return 2;
                    }
                    usedUrl = outcome.usedUrl;

                    // 4) التحقق قبل القبول: بصمة sha256 على ما وصل فعلاً من القرص
                    String got = sha256File(part);
                    if (!sha.equals(got)) {
                        Files.deleteIfExists(part);        // رفض نظيف — لا بقايا
                        emit("SHA MISMATCH rejected=true file=" + relPath
                                + " expected=" + sha + " got=" + got
                                + " part-removed=true url=" + usedUrl);
                        return 4;
                    }
                    if (outcome.resumeStart > 0) {
                        emit("RESUME used=true bytes-kept=" + outcome.resumeStart
                                + " bytes-fetched=" + outcome.bytesFetchedOnResume
                                + " sha256-match=true total=" + size
                                + " file=" + relPath + " mirror=" + baseOf(usedUrl));
                    }
                    moveAtomic(part, cached);
                    emit("MIRROR OK url=" + usedUrl + " mirror=" + baseOf(usedUrl)
                            + " file=" + relPath + " bytes=" + Files.size(cached) + " sha256-match=true");
                }
            } else {
                emit("STORE HIT id=" + id + " sha256=" + sha + " (صفر تنزيل — نفس البايتات)");
                reused++;
                usedUrl = "store";
            }

            // 5) Verify the cached bytes on disk, then place atomically (same order as SyncClient).
            String diskSha = sha256File(cached);
            if (!sha.equals(diskSha)) {
                emit("ARTIFACT REJECTED id=" + id + " reason=disk-sha-mismatch expected=" + sha + " got=" + diskSha);
                return 4;
            }
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
            emit("PLACED path=" + relPath + " sha256=" + sha + " kind=" + kind + " via=" + baseOf(usedUrl));
        }

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        emit("SYNC DONE placed=" + placed + " reused=" + reused
                + " elapsed-ms=" + elapsedMs + " bytes=" + bytesMoved);
        return 0;
    }

    // ------------------------------------------------------------------ mirrors + resume

    record Outcome(boolean ok, String usedUrl, long resumeStart, long bytesFetchedOnResume, int tried) {
    }

    /**
     * حلقة المرايا: تُجرَّب العناوين بالترتيب. الفشل الاتصالي (اتصال مرفوض/مهلة) يوقف تجريب
     * نفس العنوان فوراً ويُسجَّل في deadBases (لن يُعاد تجريبه في هذه التشغيلة — failover كسول
     * مرة واحدة). القطع في منتصف البايتات لا يُعد «موت مرآة» — تُعاد محاولة نفس المرآة مع
     * استكمال (Range) قبل تجريب التالية، والباقي المتراكم يبقى في .part عبر المرايا.
     */
    static Outcome downloadWithMirrors(List<URI> urls, Path part, String sha, long size, long kept0,
                                       Set<String> deadBases, int retries, int connectMs, int requestMs,
                                       String relPath, String id) throws Exception {
        long kept = kept0;
        long limit = size > 0 ? size : MAX_UNDECLARED_BLOB_BYTES;
        long fetched = 0;             // كل ما سُحب من الشبكة لهذا العنصر في هذه التشغيلة
        long resumeStart = 0;          // قيمة kepd لحظة أول طلب Range (تعريف «ما كُسب»)
        long fetchedAtResumeStart = -1; // عدّاد الشبكة لحظة الاستئناف
        long bytesFetchedOnResume = 0;
        String prevFailedUrl = null;
        long tGiveup = 0;
        String usedUrl = null;
        int tried = 0;

        if (kept0 > 0) {
            resumeStart = kept0;
            fetchedAtResumeStart = fetched;
        }

        for (URI url : urls) {
            String base = baseOf(url.toString());
            boolean skip = deadBases.contains(base) && urls.size() > 1
                    && anyAliveAlternative(urls, deadBases);
            if (skip) {
                emit("MIRROR SKIP url=" + url
                        + " reason=known-dead (failover كسول مرة واحدة — لا إعادة تجريب للمرآة الميتة)");
                continue;
            }
            tried++;
            long tUrlStart = System.nanoTime();
            boolean urlOk = false;
            boolean connLevel = false;
            String failReason = null;
            int attemptsLeft = Math.max(1, retries);

            boolean rangeReset = false;

            while (attemptsLeft-- > 0) {
                boolean cut = false;
                boolean oversize = false;
                String cutReason = null;
                try {
                    HttpRequest.Builder builder = HttpRequest.newBuilder(url)
                            .timeout(Duration.ofMillis(requestMs))
                            .GET();
                    if (kept > 0) {
                        builder.header("Range", "bytes=" + kept + "-");
                    }
                    long tBefore = System.nanoTime();
                    HttpResponse<InputStream> response =
                            httpClient(connectMs).send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
                    int code = response.statusCode();
                    if (code == 416 && kept > 0 && !rangeReset) {
                        // The kept bytes cannot be resumed (e.g. the .part already has the
                        // full length): drop them and fetch from 0 once on this mirror.
                        closeQuietly(response.body());
                        emit("RANGE NOT SATISFIABLE url=" + url + " bytes-kept=" + kept
                                + " — truncating .part and retrying from 0");
                        kept = 0;
                        truncate(part, 0);
                        resumeStart = 0;
                        fetchedAtResumeStart = -1;
                        rangeReset = true;
                        attemptsLeft++;
                        continue;
                    }
                    if (code != 200 && code != 206) {
                        closeQuietly(response.body());
                        failReason = "HTTP " + code;
                        break;
                    }
                    if (prevFailedUrl != null) {
                        long elapsed = (System.nanoTime() - tGiveup) / 1_000_000L;
                        emit("MIRROR SWITCH from=" + prevFailedUrl + " to=" + url
                                + " elapsed-ms=" + elapsed
                                + " switch-bytes=" + kept + " (الانتقال بعد فشل فعلي — failover كسول)");
                        prevFailedUrl = null;
                    }
                    if (resumeStart == 0 && kept > 0) {
                        resumeStart = kept;
                        fetchedAtResumeStart = fetched;
                    }
                    if (code == 200 && kept > 0) {
                        emit("RANGE IGNORED url=" + url + " — إعادة من الصفر (الخادم رفض الاستكمال)");
                        kept = 0;
                        truncate(part, 0);
                    }
                    long firstByteNanos = System.nanoTime();

                    InputStream in = response.body();
                    FileChannel channel = FileChannel.open(part,
                            StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    try {
                        channel.position(kept);
                        byte[] buf = new byte[8192];
                        while (true) {
                            int n;
                            try {
                                n = in.read(buf);
                            } catch (IOException e) {
                                cut = true;
                                cutReason = oneLine(e);
                                break;
                            }
                            if (n < 0) {
                                break; // EOF
                            }
                            if (kept + n > limit) {
                                oversize = true;   // never write past the declared size
                                break;
                            }
                            channel.write(ByteBuffer.wrap(buf, 0, n));
                            kept += n;
                            fetched += n;
                            if (stopArmed && kept >= stopAfter) {
                                // محاكاة «عميل يتوقف»: نضبط .part على N بالضبط ثم نخرج 9
                                channel.truncate(stopAfter);
                                kept = stopAfter;
                                stopArmed = false;
                                channel.close();
                                closeQuietly(in);
                                emit("CLIENT STOP file=" + relPath + " after-bytes=" + kept
                                        + " exit=9 (محاكاة عميل يتوقف في المنتصف — تُستأنف في تشغيلة تالية)");
                                throw new StopRequested();
                            }
                        }
                    } finally {
                        channel.close();
                    }
                    closeQuietly(in);

                    if (oversize) {
                        // A body longer than declared is broken or hostile: discard what it sent.
                        Files.deleteIfExists(part);
                        emit("DOWNLOAD OVERSIZE url=" + url + " limit=" + limit
                                + " bytes-kept=" + kept + " part-removed=true"
                                + " reason=body-exceeds-" + (size > 0 ? "declared-size" : "undeclared-cap"));
                        kept = 0;
                        resumeStart = 0;
                        fetchedAtResumeStart = -1;
                        failReason = "body-too-large";
                        break;
                    }
                    if (size > 0 && kept < size) {
                        emit("DOWNLOAD CUT url=" + url + " bytes-kept=" + kept
                                + " reason=" + (cutReason == null ? "unexpected-eof (بايتات أقل من المعلن)" : cutReason)
                                + " — إعادة محاولة بنفس المرآة مع استكمال Range");
                        continue; // قطع ≠ موت مرآة: عُد لنفسها مع الباقي فقط
                    }
                    urlOk = true;
                    break;
                } catch (StopRequested s) {
                    throw s;
                } catch (Throwable t) {
                    if (isConnectionLevel(t)) {
                        failReason = oneLine(t);
                        connLevel = true;
                        break;
                    }
                    cut = true;
                    cutReason = oneLine(t);
                    if (kept < size) {
                        emit("DOWNLOAD CUT url=" + url + " bytes-kept=" + kept
                                + " reason=" + cutReason + " — إعادة محاولة بنفس المرآة مع استكمال Range");
                        continue;
                    }
                    failReason = cutReason;
                    break;
                }
            }

            if (urlOk) {
                long elapsed = (System.nanoTime() - tUrlStart) / 1_000_000L;
                if (fetchedAtResumeStart >= 0) {
                    bytesFetchedOnResume = fetched - fetchedAtResumeStart;
                }
                emit("MIRROR TRY url=" + url + " result=ok bytes-fetched=" + fetched
                        + " elapsed-ms=" + elapsed);
                return new Outcome(true, url.toString(), resumeStart, bytesFetchedOnResume, tried);
            }
            if (failReason == null) {
                failReason = "download-cut-persisted";
            }
            emit("MIRROR TRY url=" + url + " result=fail reason=" + failReason);
            if (connLevel) {
                deadBases.add(base);
            }
            prevFailedUrl = url.toString();
            tGiveup = System.nanoTime();
        }
        return new Outcome(false, usedUrl, resumeStart, bytesFetchedOnResume, tried);
    }

    static boolean anyAliveAlternative(List<URI> urls, Set<String> deadBases) {
        for (URI u : urls) {
            if (!deadBases.contains(baseOf(u.toString()))) {
                return true;
            }
        }
        return false;
    }

    /** فشل اتصالي حقيقي (يبرّر اعتبار المرآة «ميتة» لهذه التشغيلة). */
    static boolean isConnectionLevel(Throwable t) {
        Throwable cursor = t;
        for (int depth = 0; depth < 8 && cursor != null; depth++) {
            if (cursor instanceof ConnectException
                    || cursor instanceof NoRouteToHostException
                    || cursor instanceof HttpConnectTimeoutException
                    || cursor instanceof HttpTimeoutException) {
                return true;
            }
            if (cursor instanceof SocketException se
                    && se.getMessage() != null
                    && se.getMessage().toLowerCase(Locale.ROOT).contains("refused")) {
                return true;
            }
            cursor = cursor.getCause() == cursor ? null : cursor.getCause();
        }
        return false;
    }

    static HttpClient httpClient(int connectMs) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectMs))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    static String baseOf(String url) {
        try {
            URI uri = URI.create(url);
            int port = uri.getPort();
            return uri.getScheme() + "://" + uri.getHost() + (port > 0 ? ":" + port : "");
        } catch (Throwable t) {
            return url;
        }
    }

    // ------------------------------------------------------------------ safety helpers

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

    static void truncate(Path file, long size) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            channel.truncate(size);
        }
    }

    /**
     * GETs a body of at most {@code maxBytes} bytes. A larger declared
     * Content-Length, or a body that streams past the cap, aborts with an
     * {@link IOException} before more than {@code maxBytes} are buffered.
     */
    static byte[] httpGet(URI uri, int connectMs, int requestMs, long maxBytes)
            throws IOException, InterruptedException {
        HttpClient client = httpClient(connectMs);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(requestMs))
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

    static void closeQuietly(InputStream in) {
        if (in != null) {
            try {
                in.close();
            } catch (Throwable ignored) {
                // best effort
            }
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
        OutputStream sink = markerFile;
        if (sink != null) {
            try {
                sink.close();
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
        String message = t.getMessage();
        String text = t.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        return text.replace('\r', ' ').replace('\n', ' ');
    }
}
