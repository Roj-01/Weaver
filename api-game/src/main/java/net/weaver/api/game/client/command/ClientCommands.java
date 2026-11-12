package net.weaver.api.game.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Commands that run on the client, without the server: {@code /waypoint add},
 * {@code /mymod reloadshaders}, a local calculator. They work on any server,
 * vanilla ones too, and show in the chat suggestions next to the server's
 * commands.
 *
 * <pre>{@code
 * ClientCommands.register(LiteralArgumentBuilder.<ClientSuggestionProvider>literal("hello")
 *         .executes(context -> {
 *             ClientCommands.feedback(Component.literal("Hello from the client"));
 *             return 1;
 *         }));
 * }</pre>
 *
 * <p>A command typed in chat whose first word is a client command runs here and
 * is not sent; anything else goes to the server as before. A client command
 * shadows a server command with the same name. Register during client setup.</p>
 */
public final class ClientCommands {
    private static final CommandDispatcher<ClientSuggestionProvider> DISPATCHER = new CommandDispatcher<>();

    private ClientCommands() {
    }

    /** Adds a client command. */
    public static CommandNode<ClientSuggestionProvider> register(LiteralArgumentBuilder<ClientSuggestionProvider> command) {
        return DISPATCHER.register(Objects.requireNonNull(command, "command"));
    }

    /** The client command tree. */
    public static CommandDispatcher<ClientSuggestionProvider> dispatcher() {
        return DISPATCHER;
    }

    /** Whether {@code command} (without the slash) starts with a client command. */
    public static boolean handles(String command) {
        String first = command.strip();
        int space = first.indexOf(' ');
        if (space >= 0) {
            first = first.substring(0, space);
        }
        return !first.isEmpty() && DISPATCHER.getRoot().getChild(first) != null;
    }

    /**
     * Runs {@code command} (without the slash) when it is a client command. Errors are
     * shown in chat. Returns false when it is not a client command (it then goes to the
     * server).
     */
    public static boolean execute(String command) {
        if (!handles(command)) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            return false;
        }
        ClientSuggestionProvider source = minecraft.getConnection().getSuggestionsProvider();
        try {
            ParseResults<ClientSuggestionProvider> parsed = DISPATCHER.parse(command, source);
            DISPATCHER.execute(parsed);
        } catch (CommandSyntaxException invalid) {
            feedback(Component.literal(invalid.getMessage()).withStyle(net.minecraft.ChatFormatting.RED));
        } catch (RuntimeException failure) {
            MutableComponent message = Component.literal("Client command failed: " + failure.getMessage());
            feedback(message.withStyle(net.minecraft.ChatFormatting.RED));
            System.out.println("[weaver] CLIENT COMMAND FAILED command=" + command + " reason=" + failure);
        }
        return true;
    }

    /** Shows a line in the chat, for client command feedback. */
    public static void feedback(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(message);
        }
    }

    /** Adds the client commands to the client-side tree the server sent, for suggestions. Called by the loader. */
    @net.weaver.api.annotation.Internal
    public static void merge(CommandDispatcher<ClientSuggestionProvider> serverTree) {
        for (CommandNode<ClientSuggestionProvider> node : DISPATCHER.getRoot().getChildren()) {
            serverTree.getRoot().addChild(node);
        }
    }
}
