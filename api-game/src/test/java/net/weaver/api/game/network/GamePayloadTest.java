package net.weaver.api.game.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GamePayloadTest
{
    @Test
    void typeIsTheFixedWeaverBridgeId()
    {
        assertEquals("weaver:bridge", GamePayload.TYPE.id().toString());
    }

    @Test
    void bodyIsCopiedOnConstructionAndAccess()
    {
        byte[] body = new byte[]{1, 2, 3};
        GamePayload payload = new GamePayload(body);
        body[0] = 9;

        assertArrayEquals(new byte[]{1, 2, 3}, payload.body());
        byte[] accessor = payload.body();
        accessor[1] = 9;
        assertArrayEquals(new byte[]{1, 2, 3}, payload.body());
    }

    @Test
    void oversizedBodyIsRejected()
    {
        byte[] body = new byte[GamePayload.MAX_BRIDGE_BODY + 1];

        assertThrows(IllegalArgumentException.class, () -> new GamePayload(body));
    }

    @Test
    void codecRoundTripsTheBody()
    {
        GamePayload original = new GamePayload(new byte[]{4, 5, 6});
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try
        {
            GamePayload.STREAM_CODEC.encode(buffer, original);
            GamePayload decoded = GamePayload.STREAM_CODEC.decode(buffer);
            assertArrayEquals(new byte[]{4, 5, 6}, decoded.body());
        }
        finally
        {
            buffer.release();
        }
    }

    @Test
    void registerInCopiesTheListAndAppendsTheBridgeType()
    {
        CustomPacketPayload.TypeAndCodec<FriendlyByteBuf, BrandPayload> brand =
                new CustomPacketPayload.TypeAndCodec<>(BrandPayload.TYPE,
                        BrandPayload.STREAM_CODEC);
        List<CustomPacketPayload.TypeAndCodec<?, ?>> original = List.of(brand);

        List<CustomPacketPayload.TypeAndCodec<?, ?>> extended =
                GamePayload.registerIn(original);

        assertEquals(2, extended.size());
        assertEquals(1, original.size());
        assertEquals(BrandPayload.TYPE, extended.get(0).type());
        assertEquals(GamePayload.TYPE, extended.get(1).type());
        assertInstanceOf(GamePayload.class, new GamePayload(new byte[0]));
    }

    @Test
    void registerInDoesNotDuplicateAnExistingBridgeEntry()
    {
        CustomPacketPayload.TypeAndCodec<FriendlyByteBuf, GamePayload> bridge =
                new CustomPacketPayload.TypeAndCodec<>(GamePayload.TYPE,
                        GamePayload.STREAM_CODEC);

        List<CustomPacketPayload.TypeAndCodec<?, ?>> extended =
                GamePayload.registerIn(List.of(bridge));

        assertEquals(1, extended.size());
        assertTrue(extended.get(0).type().id().equals(GamePayload.TYPE.id()));
        assertFalse(extended.get(0) == bridge);
    }
}
