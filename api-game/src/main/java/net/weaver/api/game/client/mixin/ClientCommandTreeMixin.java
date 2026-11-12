package net.weaver.api.game.client.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.weaver.api.command.CommandRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Records the client command tree after the game applies a command packet.
 *
 * <p>The injection runs at the tail of
 * {@code ClientPacketListener#handleCommands(ClientboundCommandsPacket)}. It
 * reads the live dispatcher through the public {@code getCommands()} accessor,
 * builds the root child names in dispatcher order, and calls
 * {@link CommandRegistry#recordTreeSync(Object, List, String)} with the source
 * label {@code "client"}.
 *
 * <p>The target was verified against Minecraft 26.3:
 * {@code ClientPacketListener#handleCommands(Lnet/minecraft/network/protocol/game/ClientboundCommandsPacket;)V}.
 * Every failure is contained and reported once as
 * {@code [weaver] CLIENT COMMAND TREE FAILED}; nothing throws into the game.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientCommandTreeMixin
{
    private static final AtomicBoolean REPORTED_FAILURE = new AtomicBoolean();

    @Inject(method = "handleCommands", at = @At("TAIL"), require = 1)
    private void weaverRecordCommandTree(ClientboundCommandsPacket packet, CallbackInfo callback)
    {
        try
        {
            ClientPacketListener listener = (ClientPacketListener) (Object) this;
            CommandDispatcher<ClientSuggestionProvider> dispatcher = listener.getCommands();
            if (dispatcher == null)
            {
                return;
            }
            List<String> names = new ArrayList<>();
            for (CommandNode<?> child : dispatcher.getRoot().getChildren())
            {
                String name = child.getName();
                if (name != null)
                {
                    names.add(name);
                }
            }
            CommandRegistry.recordTreeSync(dispatcher, names, "client");
        }
        catch (Throwable failure)
        {
            if (REPORTED_FAILURE.compareAndSet(false, true))
            {
                System.err.println("[weaver] CLIENT COMMAND TREE FAILED reason=" + failure);
            }
        }
    }
}
