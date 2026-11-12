package net.weaver.api.game.event.mixin;

import com.mojang.serialization.Codec;
import net.minecraft.network.protocol.status.ClientboundStatusResponsePacket;
import net.minecraft.network.protocol.status.ServerStatus;
import net.weaver.api.game.network.StatusAdvertisement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** The status reply carries the server's Weaver mods ({@link StatusAdvertisement}). */
@Mixin(ClientboundStatusResponsePacket.class)
public abstract class StatusAdvertisementMixin {
    @ModifyArg(method = "<clinit>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/codec/ByteBufCodecs;fromCodec(Lcom/mojang/serialization/DynamicOps;Lcom/mojang/serialization/Codec;)Lnet/minecraft/network/codec/StreamCodec$CodecOperation;"),
            index = 1)
    private static Codec<ServerStatus> weaver$advertise(Codec<ServerStatus> codec) {
        return StatusAdvertisement.wrap(codec);
    }
}
