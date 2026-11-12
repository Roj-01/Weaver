package net.weaver.api.game.network.mixin;

import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.weaver.api.game.network.GamePayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;

/**
 * Registers the Weaver bridge type in the clientbound custom-payload codecs.
 *
 * <p>Minecraft 26.3 builds {@code GAMEPLAY_STREAM_CODEC} and
 * {@code CONFIG_STREAM_CODEC} inside {@code ClientboundCustomPayloadPacket.<clinit>}
 * by calling
 * {@code CustomPacketPayload.codec(FallbackProvider, List)} twice. The gameplay
 * list is mutable, but the configuration list is created with {@code List.of}
 * and is immutable, so the only reliable point is this initialiser call. The
 * injector matches the interface invocation by its exact descriptor and appends
 * a copied list containing {@link GamePayload}.</p>
 *
 * <p>The verified 26.3 target is:</p>
 *
 * <pre>{@code
 * Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;codec(
 *     Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$FallbackProvider;
 *     Ljava/util/List;)Lnet/minecraft/network/codec/StreamCodec;
 * }</pre>
 */
@Mixin(ClientboundCustomPayloadPacket.class)
public abstract class ClientboundCustomPayloadPacketMixin
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
            require = 2)
    private static List weaverRegisterBridgeCodec(List codecs)
    {
        return GamePayload.registerIn(codecs);
    }
}
