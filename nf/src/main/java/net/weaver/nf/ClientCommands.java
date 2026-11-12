package net.weaver.nf;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side commands written against {@link CommandSourceStack}, as NeoForge's
 * {@code RegisterClientCommandsEvent}. They run on the client, without the server,
 * through Weaver's {@code ClientCommands}: each top-level command is offered in
 * chat, and its arguments go to the command as written.
 */
public final class ClientCommands {
    private static final CommandDispatcher<CommandSourceStack> DISPATCHER = new CommandDispatcher<>();
    private static final Set<String> BRIDGED = new HashSet<>();

    private ClientCommands() {
    }

    /** Adds commands to the client dispatcher. */
    public static synchronized void register(Consumer<CommandDispatcher<CommandSourceStack>> registration) {
        registration.accept(DISPATCHER);
        for (CommandNode<CommandSourceStack> node : DISPATCHER.getRoot().getChildren()) {
            String name = node.getName();
            if (BRIDGED.add(name)) {
                net.weaver.api.game.client.command.ClientCommands.register(
                        LiteralArgumentBuilder.<ClientSuggestionProvider>literal(name)
                                .executes(context -> run(name) ? 1 : 0)
                                .then(RequiredArgumentBuilder.<ClientSuggestionProvider, String>argument("arguments",
                                                StringArgumentType.greedyString())
                                        .executes(context -> run(name + " "
                                                + StringArgumentType.getString(context, "arguments")) ? 1 : 0)));
            }
        }
    }

    /** Runs {@code command} (without the slash) on the client; false when it failed or is unknown. */
    public static boolean run(String command) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        CommandSource feedback = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                net.weaver.api.game.client.command.ClientCommands.feedback(message);
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        CommandSourceStack source = new CommandSourceStack(feedback, player.position(),
                new Vec2(player.getXRot(), player.getYRot()), null, PermissionSet.ALL_PERMISSIONS, null, player);
        try {
            DISPATCHER.execute(command, source);
            return true;
        } catch (CommandSyntaxException failure) {
            net.weaver.api.game.client.command.ClientCommands.feedback(Component.literal(failure.getMessage()));
            return false;
        } catch (RuntimeException failure) {
            net.weaver.api.game.client.command.ClientCommands.feedback(Component.literal(String.valueOf(failure.getMessage())));
            return false;
        }
    }

    /** Kept for code written against the copies; Weaver merges client commands into the tree itself. */
    public static void merge(CommandDispatcher<?> serverTree) {
    }

    static Vec3 unused() {
        return Vec3.ZERO;
    }
}
