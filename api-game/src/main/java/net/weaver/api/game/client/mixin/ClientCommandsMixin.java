package net.weaver.api.game.client.mixin;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.weaver.api.game.client.command.ClientCommands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Client commands: shown with the server's for suggestions, and run here instead of being sent. */
@Mixin(ClientPacketListener.class)
public abstract class ClientCommandsMixin {
    @Shadow
    private CommandDispatcher<ClientSuggestionProvider> commands;

    @Inject(method = "handleCommands", at = @At("RETURN"))
    private void weaver$mergeClientCommands(ClientboundCommandsPacket packet, CallbackInfo callback) {
        try {
            ClientCommands.merge(commands);
        } catch (RuntimeException failure) {
            System.out.println("[weaver] CLIENT COMMANDS MERGE FAILED reason=" + failure);
        }
    }

    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void weaver$runClientCommand(String command, CallbackInfo callback) {
        if (ClientCommands.execute(command)) {
            callback.cancel();
        }
    }

    @Inject(method = "sendUnattendedCommand", at = @At("HEAD"), cancellable = true)
    private void weaver$runUnattendedClientCommand(String command, Screen screen, CallbackInfo callback) {
        if (ClientCommands.execute(command)) {
            callback.cancel();
        }
    }
}
