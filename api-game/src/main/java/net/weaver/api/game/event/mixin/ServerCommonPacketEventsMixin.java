package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import java.util.Optional;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.player.CustomClickActionEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Custom click actions reported by a client, in the configuration or play phase. */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketEventsMixin {
    @Shadow
    protected abstract GameProfile playerProfile();

    @WrapOperation(method = "handleCustomClickAction(Lnet/minecraft/network/protocol/common/ServerboundCustomClickActionPacket;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;handleCustomClickAction(Lnet/minecraft/resources/Identifier;Ljava/util/Optional;)V"))
    private void weaver$customClick(MinecraftServer server, Identifier id, Optional<Tag> payload, Operation<Void> original) {
        Object self = this;
        ServerPlayer player = self instanceof ServerGamePacketListenerImpl game ? game.player : null;
        CustomClickActionEvent event = GameEvents.post(
                new CustomClickActionEvent(player, playerProfile(), id, payload), LogicalSide.SERVER);
        if (!GameEvents.canceled(event)) {
            original.call(server, id, payload);
        }
    }
}
