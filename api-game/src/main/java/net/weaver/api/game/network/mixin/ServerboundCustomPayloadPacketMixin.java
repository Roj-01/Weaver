package net.weaver.api.game.network.mixin;

import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.weaver.api.game.network.GamePayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;

/**
 * Registers the Weaver bridge type in the serverbound custom-payload codec.
 *
 * <p>Minecraft 26.3 builds {@code ServerboundCustomPayloadPacket.STREAM_CODEC}
 * inside the class initialiser from an immutable {@code List.of} containing the
 * vanilla brand payload. The injector appends a copied list containing
 * {@link GamePayload} at the single
 * {@code CustomPacketPayload.codec(FallbackProvider, List)} call.</p>
 *
 * <p>Known codecs are not subject to the 32,767-byte
 * {@code DiscardedPayload} fallback; the serverbound bridge body is bounded by
 * {@link GamePayload#MAX_BRIDGE_BODY} and the 26.3 frame limit instead.</p>
 */
@Mixin(ServerboundCustomPayloadPacket.class)
public abstract class ServerboundCustomPayloadPacketMixin
{
    private static final String CODEC_CALL =
            "Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;codec("
                    + "Lnet/minecraft/network/protocol/common/custom/"
                    + "CustomPacketPayload$FallbackProvider;"
                    + "Ljava/util/List;)Lnet/minecraft/network/codec/StreamCodec;";

    @SuppressWarnings({"rawtypes", "unchecked"})
    @ModifyArg(method = "<clinit>",
            at = @At(value = "INVOKE", target = CODEC_CALL),
            index = 1,
            require = 1)
    private static List weaverRegisterBridgeCodec(List codecs)
    {
        return GamePayload.registerIn(codecs);
    }
}
