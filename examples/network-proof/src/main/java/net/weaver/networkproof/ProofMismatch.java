package net.weaver.networkproof;

import net.weaver.api.event.EventBus;
import net.weaver.api.network.NetworkPlayer;
import net.weaver.api.network.NetworkRegistry;
import net.weaver.core.network.NetworkClient;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class ProofMismatch
{
    private static final String EXPECTED_REASON =
            "handshake rejected: protocol mismatch: client=2, server=1";

    private ProofMismatch()
    {
        throw new AssertionError("No ProofMismatch instances");
    }

    public static void main(String[] args) throws Exception
    {
        if (args.length != 1)
        {
            System.out.println("NETWORK MISMATCH RESULT ok=false reason=usage:ProofMismatch <port>");
            System.exit(1);
            return;
        }
        int port;
        try
        {
            port = Integer.parseInt(args[0]);
        }
        catch (NumberFormatException failure)
        {
            System.out.println("NETWORK MISMATCH RESULT ok=false reason=invalid-port");
            System.exit(1);
            return;
        }

        ExecutorService game = Executors.newSingleThreadExecutor(runnable ->
        {
            Thread thread = new Thread(runnable, "network-proof-mismatch-game");
            thread.setDaemon(true);
            return thread;
        });
        NetworkRegistry registry = new NetworkRegistry();
        registry.registerClientbound(ProofProtocol.MOD_ID, ProofProtocol.PONG,
                (payload, context) ->
                {
                });
        registry.registerServerbound(ProofProtocol.MOD_ID, ProofProtocol.PING,
                (payload, context) ->
                {
                });

        NetworkClient client = null;
        boolean ok = false;
        try
        {
            client = NetworkClient.builder(registry)
                    .host("127.0.0.1")
                    .port(port)
                    .gameExecutor(game)
                    .eventBus(new EventBus("network-proof-mismatch"))
                    .player(new NetworkPlayer(UUID.randomUUID(), "NetworkProofMismatch"))
                    .mods(ProofProtocol.mods())
                    .protocolVersion(2)
                    .connect();

            boolean play = client.awaitPlay(5, TimeUnit.SECONDS);
            String reason = client.failureReason();
            ok = !play && EXPECTED_REASON.equals(reason);
            System.out.println("NETWORK MISMATCH CHECK ok=" + ok + " reason=" + reason);
            System.out.println("NETWORK MISMATCH RESULT ok=" + ok);
            System.out.flush();
        }
        finally
        {
            if (client != null)
            {
                client.close();
            }
            game.shutdownNow();
            game.awaitTermination(5, TimeUnit.SECONDS);
        }
        if (!ok)
        {
            System.exit(1);
        }
    }
}
