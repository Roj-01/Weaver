package weaver.sync.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncClientLimitsTest
{
    private static final byte[] CONTENT = "weaver-sync-blob".repeat(512)
            .getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tempDir;

    private HttpServer server;
    private ExecutorService executor;
    private volatile HttpHandler manifestHandler;
    private volatile HttpHandler blobHandler;
    private Path target;
    private Path store;

    @BeforeEach
    void setUp() throws Exception
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/weaver/manifest.json", exchange -> manifestHandler.handle(exchange));
        server.createContext("/blob", exchange -> blobHandler.handle(exchange));
        server.start();
        String sha = SyncClient.sha256(CONTENT);
        manifestHandler = exchange -> send(exchange,
                manifest(sha, CONTENT.length).getBytes(StandardCharsets.UTF_8));
        blobHandler = exchange -> send(exchange, CONTENT);
        target = tempDir.resolve("target");
        store = tempDir.resolve("store");
    }

    @AfterEach
    void tearDown()
    {
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    void normalSyncStillPlacesTheBlob() throws Exception
    {
        assertEquals(0, SyncClient.sync(manifestUrl(), target, store));
        assertArrayEquals(CONTENT, Files.readAllBytes(target.resolve("mods").resolve("a.jar")));
    }

    @Test
    void endlessManifestBodyIsAbortedAtTheCap()
    {
        manifestHandler = SyncClientLimitsTest::endless;

        IOException failure = assertThrows(IOException.class,
                () -> SyncClient.sync(manifestUrl(), target, store, 64 * 1024));
        assertTrue(failure.getMessage().contains("body-too-large"), failure.getMessage());
    }

    @Test
    void blobBodyLongerThanTheDeclaredSizeIsRejected()
    {
        blobHandler = SyncClientLimitsTest::endless;

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> SyncClient.sync(manifestUrl(), target, store));
        assertTrue(failure.getMessage().contains("all mirror urls failed"), failure.getMessage());
        assertFalse(Files.exists(target.resolve("mods").resolve("a.jar")));
    }

    @Test
    void declaredContentLengthOverTheCapIsRejectedBeforeReading()
    {
        blobHandler = exchange ->
        {
            byte[] longer = new byte[CONTENT.length + 1];
            send(exchange, longer);
        };

        assertThrows(IllegalStateException.class,
                () -> SyncClient.sync(manifestUrl(), target, store));
    }

    @Test
    void readLimitedAcceptsTheCapAndRejectsOneMoreByte() throws IOException
    {
        URI uri = URI.create("http://example.invalid/");
        assertEquals(3, SyncClient.readLimited(new ByteArrayInputStream(new byte[3]), 3, uri).length);
        assertThrows(IOException.class,
                () -> SyncClient.readLimited(new ByteArrayInputStream(new byte[4]), 3, uri));
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

    private static void send(HttpExchange exchange, byte[] body) throws IOException
    {
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody())
        {
            out.write(body);
        }
    }

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
}
