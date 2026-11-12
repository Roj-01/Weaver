package net.weaver.clientproof;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.weaver.api.ModContext;
import net.weaver.api.capability.AttachmentRegistry;
import net.weaver.api.capability.AttachmentType;
import net.weaver.api.capability.Capability;
import net.weaver.api.capability.CapabilitySync;
import net.weaver.api.data.DataCodecs;
import net.weaver.api.game.event.PlayerJoinEvent;
import net.weaver.api.command.CommandContext;
import net.weaver.api.command.CommandNodeSpec;
import net.weaver.api.command.CommandRegistrationException;
import net.weaver.api.command.CommandRegistry;
import net.weaver.api.command.CommandSpec;
import net.weaver.api.command.PermissionNode;
import net.weaver.api.command.PermissionPolicy;
import net.weaver.api.command.PermissionService;
import net.weaver.api.event.events.ServerStartedEvent;
import net.weaver.api.event.events.network.NetworkConnectedEvent;
import net.weaver.api.network.NetworkPhase;
import net.weaver.api.network.NetworkRegistry;
import net.weaver.api.network.NetworkSide;
import net.weaver.api.network.PayloadBuffer;
import net.weaver.api.network.PayloadCodec;
import net.weaver.api.network.PayloadType;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

final class ClientProof
{
    private static final String ROOT = "weaverclientproof";
    private static final String ALIAS = "wcp";
    private static final String GATE = "gate";
    private static final String GATE_NODE_TEXT = "weaverclientproof:command/gate";
    private static final long SERVER_BOOT_DEADLINE_MILLIS = 120_000L;
    private static final long SERVER_DEADLINE_MILLIS = 60_000L;

    private static final PermissionNode GATE_NODE = PermissionNode.parse(GATE_NODE_TEXT);
    private static final AtomicReference<MinecraftServer> SERVER = new AtomicReference<>();
    private static final AtomicReference<ModContext> CONTEXT = new AtomicReference<>();
    private static final AtomicLong SERVER_DEADLINE = new AtomicLong();
    private static final AtomicInteger COMMAND_RUNS = new AtomicInteger();
    private static final AtomicBoolean COMMAND_DONE = new AtomicBoolean();
    private static final AtomicBoolean COMMAND_FAILED = new AtomicBoolean();
    private static final AtomicBoolean COMMAND_REGISTERED = new AtomicBoolean();
    private static final PayloadCodec<String> TEXT_CODEC = new PayloadCodec<>()
    {
        @Override
        public void encode(String value, PayloadBuffer out)
        {
            out.writeString(value);
        }

        @Override
        public String decode(PayloadBuffer in)
        {
            return in.readString();
        }
    };
    private static final PayloadType<String> PING =
            PayloadType.of("weaverclientproof", "ping", TEXT_CODEC);
    private static final PayloadType<String> PONG =
            PayloadType.of("weaverclientproof", "pong", TEXT_CODEC);
    private static final Capability<String> SYNC_CAPABILITY =
            AttachmentType.builder("weaverclientproof:synced", String.class)
                    .codec(DataCodecs.utf8String(64), 64)
                    .sync(CapabilitySync.RUNTIME)
                    .build();
    private static final AtomicReference<ServerPlayer> SYNC_PLAYER = new AtomicReference<>();
    private static final AtomicInteger SYNC_DELAY = new AtomicInteger(-1);

    private ClientProof()
    {
    }

    static void install(ModContext context)
    {
        CONTEXT.set(context);
        ProofState.physicalServer(context.isPhysicalServer());
        SERVER_DEADLINE.set(System.currentTimeMillis() + SERVER_BOOT_DEADLINE_MILLIS);
        context.events().addListener(ServerStartedEvent.class, event ->
        {
            if (event.server() instanceof MinecraftServer server)
            {
                SERVER.compareAndSet(null, server);
                SERVER_DEADLINE.set(System.currentTimeMillis() + SERVER_DEADLINE_MILLIS);
            }
        });
        try
        {
            CommandSpec spec = CommandSpec.command(ROOT)
                    .modId(context.id())
                    .description("Weaver client proof command")
                    .alias(ALIAS)
                    .child(CommandNodeSpec.literal(GATE)
                            .permission(GATE_NODE, PermissionPolicy.DENY, 0)
                            .executor(ClientProof::executeGate)
                            .build())
                    .build();
            CommandRegistry.register(spec);
            COMMAND_REGISTERED.set(true);
            out("CLIENT PROOF COMMAND REGISTERED ok=true");
        }
        catch (CommandRegistrationException failure)
        {
            COMMAND_FAILED.set(true);
            out("CLIENT PROOF COMMAND REGISTERED ok=false reason=" + reason(failure));
        }
        catch (Throwable failure)
        {
            COMMAND_FAILED.set(true);
            out("CLIENT PROOF COMMAND REGISTERED ok=false reason=" + reason(failure));
        }
        installNetwork();
        installCapabilitySync(context);
        out("CLIENT PROOF INIT side=" + context.side());
    }

    static Capability<String> syncCapability()
    {
        return SYNC_CAPABILITY;
    }

    private static void installCapabilitySync(ModContext context)
    {
        try
        {
            AttachmentRegistry.global().register(SYNC_CAPABILITY);
            context.events().addListener(PlayerJoinEvent.class, event ->
            {
                if (event.player() != null)
                {
                    SYNC_PLAYER.set(event.player());
                    SYNC_DELAY.set(10);
                }
            });
            out("CLIENT PROOF CAPABILITY REGISTERED ok=true id=" + SYNC_CAPABILITY.id());
        }
        catch (Throwable failure)
        {
            out("CLIENT PROOF CAPABILITY REGISTERED ok=false reason=" + reason(failure));
        }
    }

    private static void installNetwork()
    {
        try
        {
            NetworkRegistry.global().registerServerbound("weaverclientproof", PING,
                    NetworkPhase.PLAY, (payload, payloadContext) ->
                            payloadContext.reply(PONG, "pong:" + payload));
            NetworkRegistry.global().registerClientbound("weaverclientproof", PONG,
                    NetworkPhase.PLAY, (payload, payloadContext) ->
                    {
                        if ("pong:ping".equals(payload) && ProofState.markNetwork())
                        {
                            out("CLIENT PROOF NETWORK ok=true value=" + payload);
                        }
                    });
            net.weaver.api.event.EventBus.GAME.addListener(NetworkConnectedEvent.class,
                    event ->
                    {
                        if (event.side() != NetworkSide.CLIENT)
                        {
                            return;
                        }
                        try
                        {
                            event.connection().send(PING, "ping");
                            out("CLIENT PROOF NETWORK SENT ok=true channel="
                                    + PING.channel());
                        }
                        catch (Throwable failure)
                        {
                            out("CLIENT PROOF NETWORK ok=false reason=" + reason(failure));
                        }
                    });
            out("CLIENT PROOF NETWORK REGISTERED ok=true channels=" + PING.channel()
                    + "," + PONG.channel());
        }
        catch (Throwable failure)
        {
            out("CLIENT PROOF NETWORK REGISTERED ok=false reason=" + reason(failure));
        }
    }

    static void onServerTick(ModContext context, long tick)
    {
        CONTEXT.set(context);
        if (ProofState.physicalServer() && !context.isPhysicalServer())
        {
            ProofState.physicalServer(false);
        }
        int syncDelay = SYNC_DELAY.get();
        if (syncDelay >= 0 && SYNC_DELAY.compareAndSet(syncDelay, syncDelay - 1))
        {
            if (syncDelay <= 0)
            {
                ServerPlayer player = SYNC_PLAYER.getAndSet(null);
                if (player != null)
                {
                    try
                    {
                        AttachmentRegistry.global().set(player, SYNC_CAPABILITY, "synced");
                        out("CLIENT PROOF CAPABILITY SENT ok=true player=" + player.getUUID());
                    }
                    catch (Throwable failure)
                    {
                        out("CLIENT PROOF CAPABILITY SENT ok=false reason="
                                + reason(failure));
                    }
                }
            }
        }
        MinecraftServer server = SERVER.get();
        long deadline = SERVER_DEADLINE.get();
        if (server == null)
        {
            if (deadline > 0L && System.currentTimeMillis() >= deadline)
            {
                failServerCommand("server-missing");
            }
            return;
        }
        if (COMMAND_DONE.get() || COMMAND_FAILED.get())
        {
            return;
        }
        Object dispatcher;
        try
        {
            dispatcher = CommandRegistry.dispatcher();
        }
        catch (Throwable failure)
        {
            failServerCommand("dispatcher-unavailable: " + reason(failure));
            return;
        }
        if (dispatcher == null)
        {
            if (deadline > 0L && System.currentTimeMillis() >= deadline)
            {
                failServerCommand("dispatcher-missing");
            }
            return;
        }
        @SuppressWarnings("unchecked")
        CommandDispatcher<CommandSourceStack> typed = (CommandDispatcher<CommandSourceStack>) dispatcher;
        CommandNode<CommandSourceStack> root = typed.getRoot().getChild(ROOT);
        if (root == null || root.getChild(GATE) == null
                || typed.getRoot().getChild(ALIAS) == null)
        {
            if (deadline > 0L && System.currentTimeMillis() >= deadline)
            {
                failServerCommand("command-not-applied");
            }
            return;
        }
        if (!COMMAND_DONE.compareAndSet(false, true))
        {
            return;
        }
        runCommandProof(server, typed);
    }

    static void out(String message)
    {
        System.out.println(message);
        System.out.flush();
    }

    static String reason(Throwable failure)
    {
        if (failure == null)
        {
            return "unknown";
        }
        String message = failure.getMessage();
        String text = failure.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return text.replaceAll("\\s+", " ");
    }

    static boolean clientClassPresent()
    {
        try
        {
            Class.forName("net.minecraft.client.Minecraft", false,
                    ClientProof.class.getClassLoader());
            return true;
        }
        catch (Throwable absent)
        {
            return false;
        }
    }

    private static int executeGate(CommandContext context)
    {
        COMMAND_RUNS.incrementAndGet();
        out("CLIENT PROOF COMMAND EXECUTED");
        return 1;
    }

    private static void runCommandProof(MinecraftServer server,
            CommandDispatcher<CommandSourceStack> dispatcher)
    {
        CommandSourceStack allowed;
        CommandSourceStack denied;
        try
        {
            CommandSourceStack base = server.createCommandSourceStack();
            allowed = base.withPermission(PermissionSet.NO_PERMISSIONS);
            denied = base.withPermission(PermissionSet.ALL_PERMISSIONS);
            PermissionService.allow(allowed, GATE_NODE);
            PermissionService.deny(denied, GATE_NODE);
        }
        catch (Throwable failure)
        {
            failServerCommand("source-setup: " + reason(failure));
            return;
        }

        boolean allowedOk = false;
        String allowedDetail = "";
        try
        {
            COMMAND_RUNS.set(0);
            int result = dispatcher.execute(ROOT + " " + GATE, allowed);
            allowedOk = result == 1 && COMMAND_RUNS.get() == 1;
            if (!allowedOk)
            {
                allowedDetail = " result=" + result + " runs=" + COMMAND_RUNS.get();
            }
        }
        catch (Throwable failure)
        {
            allowedDetail = " reason=" + reason(failure);
        }
        if (allowedOk)
        {
            ProofState.commandAllowed();
        }
        out("CLIENT PROOF COMMAND allowed ok=" + allowedOk + allowedDetail);

        boolean deniedOk = false;
        String deniedDetail = "";
        try
        {
            COMMAND_RUNS.set(0);
            dispatcher.execute(ROOT + " " + GATE, denied);
            deniedDetail = " reason=no-exception runs=" + COMMAND_RUNS.get();
        }
        catch (Throwable failure)
        {
            deniedOk = COMMAND_RUNS.get() == 0;
            if (!deniedOk)
            {
                deniedDetail = " reason=executor-ran runs=" + COMMAND_RUNS.get();
            }
        }
        if (deniedOk)
        {
            ProofState.commandDenied();
        }
        out("CLIENT PROOF COMMAND denied ok=" + deniedOk + deniedDetail);

        boolean aliasOk = false;
        String aliasDetail = "";
        try
        {
            COMMAND_RUNS.set(0);
            int result = dispatcher.execute(ALIAS + " " + GATE, allowed);
            aliasOk = result == 1 && COMMAND_RUNS.get() == 1;
            if (!aliasOk)
            {
                aliasDetail = " result=" + result + " runs=" + COMMAND_RUNS.get();
            }
        }
        catch (Throwable failure)
        {
            aliasDetail = " reason=" + reason(failure);
        }
        if (aliasOk)
        {
            ProofState.commandAlias();
        }
        out("CLIENT PROOF COMMAND alias ok=" + aliasOk + aliasDetail);

        maybeFinishServer();
    }

    private static void failServerCommand(String reason)
    {
        if (!COMMAND_FAILED.compareAndSet(false, true))
        {
            return;
        }
        COMMAND_DONE.set(true);
        out("CLIENT PROOF COMMAND RESULT ok=false reason=" + reason);
        if (ProofState.physicalServer() && ProofState.claimServerResult())
        {
            out("CLIENT PROOF SERVER RESULT ok=false side=server reason=" + reason);
        }
    }

    private static void maybeFinishServer()
    {
        if (!ProofState.physicalServer() || !ProofState.commandsOk()
                || !ProofState.claimServerResult())
        {
            return;
        }
        boolean clientApi = clientClassPresent();
        boolean componentOk = !clientApi;
        out("CLIENT PROOF SERVER COMPONENT ok=" + componentOk + " client_api=" + clientApi);
        if (!componentOk)
        {
            out("CLIENT PROOF SERVER RESULT ok=false side=server reason=client-class-present");
            return;
        }
        out("CLIENT PROOF SERVER RESULT ok=true side=server");
    }
}
