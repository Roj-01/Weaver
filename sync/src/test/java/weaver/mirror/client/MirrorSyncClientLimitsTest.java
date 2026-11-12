package weaver.mirror.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MirrorSyncClientLimitsTest
{
    private static final byte[] CONTENT = content(20_000);

    @TempDir
    Path tempDir;

    private HttpServer server;
    private ExecutorService executor;
    private final AtomicInteger blobRequests = new AtomicInteger();
    private volatile HttpHandler blobHandler;
    private volatile HttpHandler manifestHandler;
    private Path target;
    private Path store;
    private String sha;

    @BeforeEach
    void setUp() throws Exception
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/weaver/manifest.json", exchange -> manifestHandler.handle(exchange));
        server.createContext("/blob", exchange ->
        {
            blobRequests.incrementAndGet();
            blobHandler.handle(exchange);
        });
        server.start();
        sha = MirrorSyncClient.sha256(CONTENT);
        manifestHandler = exchange -> send(exchange, 200,
                manifest(sha, CONTENT.length).getBytes(StandardCharsets.UTF_8));
        target = tempDir.resolve("target");
        store = tempDir.resolve("store");
        Files.createDirectories(store);
    }

    @AfterEach
    void tearDown()
    {
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    void completeLeftoverPartIsVerifiedAndAcceptedWithoutANetworkRequest() throws Exception
    {
        Files.write(store.resolve(sha + ".part"), CONTENT);
        blobHandler = exchange -> send(exchange, 416, new byte[0]);

        assertEquals(0, sync());

        assertEquals(0, blobRequests.get());
        assertPlaced();
    }

    @Test
    void fullLengthCorruptLeftoverPartIsDiscardedAndDownloadedAgain() throws Exception
    {
        byte[] wrong = CONTENT.clone();
        wrong[10] ^= 0x55;
        Files.write(store.resolve(sha + ".part"), wrong);
        blobHandler = exchange ->
        {
            if (exchange.getRequestHeaders().getFirst("Range") != null)
            {
                send(exchange, 416, new byte[0]);
                return;
            }
            send(exchange, 200, CONTENT);
        };

        assertEquals(0, sync());

        assertEquals(1, blobRequests.get());
        assertPlaced();
    }

    @Test
    void rangeNotSatisfiableTruncatesThePartAndRetriesFromZero() throws Exception
    {
        Files.write(store.resolve(sha + ".part"), Arrays.copyOf(CONTENT, 5_000));
        blobHandler = exchange ->
        {
            if (exchange.getRequestHeaders().getFirst("Range") != null)
            {
                send(exchange, 416, new byte[0]);
                return;
            }
            send(exchange, 200, CONTENT);
        };

        assertEquals(0, MirrorSyncClient.sync(manifestUrl(), target, store, 1, 3_000, 10_000));

        assertEquals(2, blobRequests.get());
        assertPlaced();
    }

    @Test
    void endlessBlobBodyIsAbortedAtTheDeclaredSize() throws Exception
    {
        blobHandler = MirrorSyncClientLimitsTest::endless;

        assertEquals(2, sync());

        Path part = store.resolve(sha + ".part");
        assertTrue(Files.notExists(part) || Files.size(part) <= CONTENT.length);
        assertFalse(Files.exists(target.resolve("mods").resolve("a.jar")));
    }

    @Test
    void endlessManifestBodyIsRejected() throws Exception
    {
        manifestHandler = MirrorSyncClientLimitsTest::endless;

        assertEquals(2, MirrorSyncClient.sync(manifestUrl(), target, store, 1, 3_000, 10_000,
                64 * 1024));
        assertEquals(0, blobRequests.get());
    }

    @Test
    void readLimitedRejectsBodiesPastTheCap()
    {
        assertArrayEquals(new byte[4], assertDoesNotThrowRead(new byte[4], 4));
        IOException failure = org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
                () -> MirrorSyncClient.readLimited(new java.io.ByteArrayInputStream(new byte[5]), 4,
                        java.net.URI.create("http://example.invalid/")));
        assertTrue(failure.getMessage().contains("body-too-large"), failure.getMessage());
    }

    private static byte[] assertDoesNotThrowRead(byte[] data, long limit)
    {
        try
        {
            return MirrorSyncClient.readLimited(new java.io.ByteArrayInputStream(data), limit,
                    java.net.URI.create("http://example.invalid/"));
        }
        catch (IOException failure)
        {
            throw new AssertionError(failure);
        }
    }

    private int sync() throws Exception
    {
        return MirrorSyncClient.sync(manifestUrl(), target, store, 2, 3_000, 10_000);
    }

    private void assertPlaced() throws IOException
    {
        assertArrayEquals(CONTENT, Files.readAllBytes(target.resolve("mods").resolve("a.jar")));
        assertArrayEquals(CONTENT, Files.readAllBytes(store.resolve(sha + ".blob")));
        assertFalse(Files.exists(store.resolve(sha + ".part")));
    }

    private String manifestUrl()
    {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/weaver/manifest.json";
    }

    private String manifest(String hash, long size)
    {
        return "{\"format\":\"weaver-manifest/1\",\"pack\":{\"id\":\"test\"},\"artifacts\":[{"
                + "\"id\":\"a\",\"path\":\"mods/a.jar\",\"sha256\":\"" + hash + "\","
                + "\"size\":" + size + ",\"kind\":\"mod\",\"urls\":[\"http://127.0.0.1:"
                + server.getAddress().getPort() + "/blob\"]}]}";
    }

    private static void send(HttpExchange exchange, int code, byte[] body) throws IOException
    {
        exchange.sendResponseHeaders(code, body.length == 0 ? -1 : body.length);
        try (OutputStream out = exchange.getResponseBody())
        {
            out.write(body);
        }
    }

    /** Streams zeros until the client hangs up (capped so a broken test cannot spin forever). */
    private static void endless(HttpExchange exchange) throws IOException
    {
        exchange.sendResponseHeaders(200, 0);
        byte[] chunk = new byte[8192];
        try (OutputStream out = exchange.getResponseBody())
        {
            for (long sent = 0; sent < (256L << 20); sent += chunk.length)
            {
                out.write(chunk);
            }
        }
        catch (IOException clientClosed)
        {
            // expected: the client aborted the body
        }
    }

    private static byte[] content(int length)
    {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++)
        {
            bytes[i] = (byte) (i * 31 + 7);
        }
        return bytes;
    }
}
